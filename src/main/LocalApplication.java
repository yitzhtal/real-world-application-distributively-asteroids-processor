package main;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONArray;
import org.json.JSONException;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;

import com.amazonaws.services.ec2.model.Instance;

import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Tag;

import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.sqs.model.Message;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import JsonObjects.AtomicAnalysis;

import JsonObjects.LocalApplicationMessage;
import JsonObjects.SummaryFile;
import JsonObjects.SummaryFileReceipt;
import enums.DangerColor;

public class LocalApplication {	

    public static String getUserDataScript(){
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("#!/bin/bash");
        lines.add("echo y|sudo yum install java-1.8.0");
        lines.add("echo y|sudo yum remove java-1.7.0-openjdk");
        lines.add("wget https://s3.amazonaws.com/real-world-application-asteroids/"+Constants.ZipFileName+" -O AWSCredentialsTEMP.zip");
        lines.add("unzip -P "+Constants.ZipFilePassword +" AWSCredentialsTEMP.zip");
        lines.add("wget https://s3.amazonaws.com/real-world-application-asteroids/manager.jar -O manager.jar");
        lines.add("java -jar manager.jar");
        String str = new String(Base64.encodeBase64(join(lines, "\n").getBytes()));
        return str;
    }
    
    private static void copyFileUsingStream(File source, File dest) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(dest);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            is.close();
            os.close();
        }
    }
    
	static String readFile(String path, Charset encoding) throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return new String(encoded, encoding);
	}

    private static String getStringFromInputStream(InputStream input)
    throws IOException {
    	// Read one text line at a time and display.
        BufferedReader reader = new BufferedReader(new 
        		InputStreamReader(input));
        
        String str = "";
        while (true) {
            String line = reader.readLine();
            if (line == null) break;

           str = str + line;
        }
        return str;
    }
    
    static String join(Collection<String> s, String delimiter) {
        StringBuilder builder = new StringBuilder();
        Iterator<String> iter = s.iterator();
        while (iter.hasNext()) {
            builder.append(iter.next());
            if (!iter.hasNext()) {
                break;
            }
            builder.append(delimiter);
        }
        return builder.toString();
    }
	
    public static void createManager(RunInstancesRequest request,AmazonEC2Client ec2,String instancetype,String keyname,String imageid) {
		request.setInstanceType(instancetype);
		request.setMinCount(Constants.AmountOfInstancesRestrictionOnLocalApplication);
		request.setMaxCount(Constants.AmountOfInstancesRestrictionOnLocalApplication);
		request.setImageId(imageid);
	    request.setKeyName(keyname);
		request.setUserData(getUserDataScript());
		RunInstancesResult runInstances = ec2.runInstances(request);  
		List<com.amazonaws.services.ec2.model.Instance> instances=runInstances.getReservation().getInstances();
		CreateTagsRequest createTagsRequest = new CreateTagsRequest();
		createTagsRequest.withResources(instances.get(0).getInstanceId()).withTags(new Tag("name","manager"));
		ec2.createTags(createTagsRequest);
    }
    
    public static void uploadFileToS3(String inputFileName,String accessKey, String secretKey) {
    	AmazonS3 s3client = new AmazonS3Client(new BasicAWSCredentials(accessKey,secretKey));
		PutObjectRequest putObjectRequest = new PutObjectRequest(Constants.bucketName, inputFileName,new File(inputFileName));
		s3client.putObject(putObjectRequest.withCannedAcl(CannedAccessControlList.PublicReadWrite));
    }
    
    public static boolean hasManager(AmazonEC2Client ec2) {
		DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest();
		List<Reservation> reservations  = ec2.describeInstances(describeInstancesRequest).getReservations();
		
		for(Reservation reservation : reservations) {
			for(Instance instance : reservation.getInstances()) {	
				for(Tag tag:instance.getTags()) {
					if(hasManagerTaggedConditions(tag,instance)) {
						System.out.println("Local Application :: Already has manager");
						return true;
					}
				}	
			}
		}
		return false;
    }
    
    public static boolean hasManagerTaggedConditions(Tag tag,Instance instance) {
    	boolean state = (instance.getState().getName().equals("running") || 
    			        instance.getState().getName().equals("pending"));
    	
    	return (tag.getValue().equals("manager") && state);
    }
	
	public static void main(String[] args) throws IOException, JSONException {
		if(args.length <= 3 || args.length > 5) { System.out.println("Local Application :: the number of arguments is suppose to be 4 or 5! "); }
		String inputFileName = args[0];
		String outputFileName = args[1];
		int n = Integer.parseInt(args[2]);
		int d = Integer.parseInt(args[3]);
		Boolean terminate = false;
		
		if(args.length == 5) {
			System.out.println("LocalApplication :: got terminate as an argument.");
			terminate = (args[4].equals("terminate")) ? true : false;
		}
		
		
		System.out.println("Local Application :: has started...\n");
		System.out.println("Local Application :: Arguments received :: inputFileName = "+ inputFileName);		
		System.out.println("Local Application :: Arguments received :: outputFileName = "+ outputFileName);	
		System.out.println("Local Application :: Arguments received :: n = "+ n);	
		System.out.println("Local Application :: Arguments received :: d = "+ d + "\n\n");	
		
		try {
			
			PropertiesCredentials p = new PropertiesCredentials(LocalApplication.class.getResourceAsStream("/main/resources/" + Constants.AWSCredentialsProperties));
		    
			String accessKey = p.getAWSAccessKeyId();
			String secretKey = p.getAWSSecretKey();
			
			if(accessKey == null || secretKey == null) {
				System.out.println("LocalApplication :: accessKey / secret key are null.");
			}
			
			mySQS.setAccessAndSecretKey(accessKey, secretKey);
		    AmazonEC2Client ec2 = new AmazonEC2Client(new BasicAWSCredentials(accessKey,secretKey));
		    
			/* credentials handling ...  */
			
			String All_local_application_queue_name_url = mySQS.getInstance().createQueue(Constants.All_local_applications_queue_name);
			String uuid = UUID.randomUUID().toString();
			System.out.println("Local Application :: Uploading the input file to S3...\n");
			File newFileToSendToS3 = new File(uuid+"-"+inputFileName);
			newFileToSendToS3.createNewFile();
			copyFileUsingStream(new File(inputFileName),newFileToSendToS3);
			uploadFileToS3(uuid+"-"+inputFileName,accessKey,secretKey); 
			
			Gson gson = new GsonBuilder().create();
			String queueURLToGoBackTo = mySQS.getInstance().createQueue(uuid);
			
			LocalApplicationMessage m = new LocalApplicationMessage(Constants.bucketName,uuid+"-"+inputFileName,queueURLToGoBackTo,outputFileName,n,d,uuid,terminate);  
			
			//* Sending message to manager... //
			mySQS.getInstance().sendMessageToQueue(All_local_application_queue_name_url,gson.toJson(m));			
			/* make instance run ... */
			
			System.out.println("Local Application :: trying to run a manager ec2 instance... \n");

			//We can assume no race conditions here...
			if(!hasManager(ec2)) { 
				System.out.println("Local Application :: Manager was not found. we now create an instance of it!");
				createManager(new RunInstancesRequest(),ec2,Constants.InstanceType,Constants.KeyName,Constants.ImageID); 
			} 

			System.out.println("Local Application :: done. Now, I`m just waiting for the results... :)");
			List<Message> result = mySQS.getInstance().awaitMessagesFromQueue(queueURLToGoBackTo,Constants.LocalApplicationAwaitMessageDelay,"Local Application");
			
			String localUUID = null;
			SummaryFileReceipt r = null;
			for (Message msg : result) { 
				r = new Gson().fromJson(msg.getBody(), SummaryFileReceipt.class); 	
				System.out.println("LocalApplication :: Thanks Manager, for this URL I just got: " + r.getSummaryFileURL());
			}
			
			  AmazonS3 s3Client = new AmazonS3Client(new BasicAWSCredentials(accessKey,secretKey));
		      System.out.println("Local Application :: Downloading the summary file receipt I just got from the manager :)");
		      S3Object s3object = s3Client.getObject(new GetObjectRequest(Constants.bucketName, r.getSummaryFileName()));
		      S3ObjectInputStream contentFromS3 = s3object.getObjectContent();

		      String contentAsJson = getStringFromInputStream(contentFromS3);	      
		      
		      //at this moment, we can delete the summary file from s3 so there won`t be any garbage there...   
		      System.out.println("LocalApplication :: trying to delete the file from bucket: "+Constants.bucketName+", named "+r.getSummaryFileName());
		      s3Client.deleteObject(new DeleteObjectRequest(Constants.bucketName, r.getSummaryFileName()));		
		      
		      //System.out.println("Local Application :: the content of the summary file receipt as json is: " +contentAsJson);
		      SummaryFile s = new Gson().fromJson(contentAsJson,SummaryFile.class);
		      String AtomicAnalysisResult = s.getAtomicAnalysisResult();
		      JSONArray AtomicAnalysisResultAsJsonArray = new JSONArray(AtomicAnalysisResult);
		      
		      //convert to ArrayList, so we can use Collections.sort()...

			  ArrayList<AtomicAnalysis> AtomicAnalysisResultAsArrayList = new ArrayList<AtomicAnalysis>();     

			  if(AtomicAnalysisResultAsJsonArray != null) { 
					   for (int i=0; i<AtomicAnalysisResultAsJsonArray.length(); i++) { 
						   String atomicString = (String) AtomicAnalysisResultAsJsonArray.get(i);
						   JsonObjects.AtomicAnalysis o = new Gson().fromJson(atomicString, JsonObjects.AtomicAnalysis.class);
						   AtomicAnalysisResultAsArrayList.add(o);
					   } 
			  } 
		      
		      Collections.sort(AtomicAnalysisResultAsArrayList, new Comparator<AtomicAnalysis>() {
		          public int compare(AtomicAnalysis a, AtomicAnalysis b) {
		              int res = a.getDanger().getId() - b.getDanger().getId();
		              if(res != 0) return res;
		              double res2 = Double.parseDouble(a.getMiss_distance_kilometers()) - Double.parseDouble(b.getMiss_distance_kilometers());
		              return (int) res2;
		          }
		      });
		        
		      localUUID = s.getLocalUUID();
				
		      //Creating the html file with the summary file brought by the manager...

		      File file = new File(outputFileName +"-"+ uuid +".html");
			 
			  if(file.exists()) {
	  			            System.out.println("LocalApplication :: File already exists! WTF, Dude...");
	  		  } else {
		  			        FileWriter fileWriter = null;
		  			        BufferedWriter bufferedWriter = null;
							fileWriter = new FileWriter(file);
		  			        bufferedWriter = new BufferedWriter(fileWriter);	
		  			     
							bufferedWriter.write(readFile(System.getProperty("user.dir")+"/src/main/resources/" + Constants.beginningName,StandardCharsets.UTF_8));

							for (int i = 0; i < AtomicAnalysisResultAsArrayList.size(); i++) {
										JsonObjects.AtomicAnalysis o =  AtomicAnalysisResultAsArrayList.get(i);		
										if(o.getDanger() == DangerColor.GREEN) { //green
											bufferedWriter.write("<tr bgcolor=#90EE90>");
										} else if(o.getDanger() == DangerColor.YELLOW) { //yellow
											bufferedWriter.write("<tr bgcolor=#FFD700>");
										} else if(o.getDanger() == DangerColor.RED) { //red
											bufferedWriter.write("<tr bgcolor=#FF6347>");
										} else {
											bufferedWriter.write("<tr>");
										}
										
										if(o.getDanger() != DangerColor.DEFAULT) {
											bufferedWriter.write("<td>" + o.getNameAsteroid() +"</td>");
											bufferedWriter.write("<td>" + o.getClose_approach_data() +"</td>");
											bufferedWriter.write("<td>" + o.getVelocity() +"</td>");
											bufferedWriter.write("<td>" + o.getEstimated_diameter_min() +"</td>");
											bufferedWriter.write("<td>" + o.getEstimated_diameter_max()+"</td>");
											bufferedWriter.write("<td>" + o.getMiss_distance_kilometers() +"</td>");	
											bufferedWriter.write("</tr>");		
										} 
							}

							bufferedWriter.write(readFile(System.getProperty("user.dir")+"/src/main/resources/"+ Constants.endName,StandardCharsets.UTF_8));
		    			    System.out.println("LocalApplication :: Thanks for the summary file! I have created the HTML!");
							bufferedWriter.flush();
							fileWriter.flush();
							bufferedWriter.close();
							fileWriter.close();
							System.out.println("Local Application ("+localUUID+"): I`m done. Thanks for serving me!");
							System.out.println("Local Application ("+localUUID+"): Now I can view the results on " + outputFileName+ " file! :)");
							mySQS.getInstance().deleteQueueByURL(queueURLToGoBackTo);
							new File(uuid+"-"+inputFileName).delete();
							new File("AsteroidsAnalysis-"+uuid).delete();
							new File(Constants.AWSCredentialsProperties).delete();	
							new File(Constants.ZipFileName).delete();
	  		  }	  
		} catch (AmazonServiceException ase) {
				System.out.println(""+ "Caught an AmazonServiceException, which " +"means your request made it " +"to Amazon S3, but was rejected with an error response" +" for some reason.");
				System.out.println("Error Message:    " + ase.getMessage());
				System.out.println("HTTP Status Code: " + ase.getStatusCode());
				System.out.println("AWS Error Code:   " + ase.getErrorCode());
				System.out.println("Error Type:       " + ase.getErrorType());
				System.out.println("Request ID:       " + ase.getRequestId());
		} catch (AmazonClientException ace) {
				System.out.println("Caught an AmazonClientException, which " +"means the client encountered " +"an internal error while trying to " +"communicate with S3, " +"such as not being able to access the network.");
				System.out.println("Error Message: " + ace.getMessage()); 
		} 
	} 
} 


