package MainPackage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.commons.codec.binary.Base64;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.util.IOUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import JsonObjects.InputFile;
import JsonObjects.LocalApplicationMessage;
import JsonObjects.SummaryFile;
import JsonObjects.SummaryFileReceipt;

public class LocalApplication {	

	static final String bucketName                           = "real-world-application-asteroids";
	private static final String credentialsFileName                  = "AWSCredentials.zip";
	public static final String All_local_applications_queue_name     = "all_local_applications_to_manager";
	
    public static String getUserDataScript(){
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("#!/bin/bash");
        lines.add("echo y|sudo yum install java-1.8.0");
        lines.add("echo y|sudo yum remove java-1.7.0-openjdk");
        lines.add("wget https://s3.amazonaws.com/real-world-application-asteroids/AWSCredentials.zip -O AWSCredentialsTEMP.zip");
        lines.add("unzip -P audiocodes AWSCredentialsTEMP.zip");
        lines.add("wget https://s3.amazonaws.com/real-world-application-asteroids/manager.jar -O manager.jar");
        lines.add("java -jar manager.jar");
        String str = new String(Base64.encodeBase64(join(lines, "\n").getBytes()));
        return str;
    }
    

	static String readFile(String path, Charset encoding) throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return new String(encoded, encoding);
	}


    private static void displayTextInputStream(InputStream input)
    throws IOException {
    	// Read one text line at a time and display.
        BufferedReader reader = new BufferedReader(new 
        		InputStreamReader(input));
        while (true) {
            String line = reader.readLine();
            if (line == null) break;

            System.out.println("    " + line);
        }
        System.out.println();
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
		request.setMinCount(1);
		request.setMaxCount(1);
		request.setImageId(imageid);
	    request.setKeyName(keyname);
		request.setUserData(getUserDataScript());
		RunInstancesResult runInstances = ec2.runInstances(request);  
		List<com.amazonaws.services.ec2.model.Instance> instances=runInstances.getReservation().getInstances();
		CreateTagsRequest createTagsRequest = new CreateTagsRequest();
		createTagsRequest.withResources(instances.get(0).getInstanceId()).withTags(new Tag("name","manager"));
		ec2.createTags(createTagsRequest);
    }
    
    public static void uploadFileToS3(String inputFileName) {
    	AmazonS3 s3client = new AmazonS3Client(new ProfileCredentialsProvider());
		PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, inputFileName,new File(inputFileName));
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
	
	public static void main(String[] args) throws IOException {
		String inputFileName = args[0];
		String outputFileName = args[1];
		int n = Integer.parseInt(args[2]);
		int d = Integer.parseInt(args[3]);
		Boolean terminate = null;
		
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
			
			/* credentials handling ...  */
			
			Properties properties = new Properties();
			String path = "C:/Users/Tal Itshayek/Desktop/DistributedSystems/importexport-webservice-tool/AWSCredentials.properties";
			try {
				properties.load(new FileInputStream(path));
			} catch (FileNotFoundException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			String accessKey = properties.getProperty("accessKeyId");
			String secretKey = properties.getProperty("secretKey"); 
			
			mySQS.setAccessAndSecretKey(accessKey, secretKey);
		    AmazonEC2Client ec2 = new AmazonEC2Client(new BasicAWSCredentials(accessKey,secretKey));
		    
			/* credentials handling ...  */
			
			String All_local_application_queue_name_url = mySQS.getInstance().createQueue(All_local_applications_queue_name);
			
			String uuid = UUID.randomUUID().toString();
			System.out.println("Local Application :: Uploading the input file to S3...\n");
			uploadFileToS3(inputFileName); 		
			
			Gson gson = new GsonBuilder().create();
			String queueURLToGoBackTo = mySQS.getInstance().createQueue(uuid);
			
			LocalApplicationMessage m = new LocalApplicationMessage(bucketName,inputFileName,queueURLToGoBackTo,outputFileName,n,d,uuid,terminate);  
			
			//* Sending message to manager... //
			mySQS.getInstance().sendMessageToQueue(All_local_application_queue_name_url,gson.toJson(m));			
			/* make instance run ... */
			
			System.out.println("Local Application :: trying to run a manager ec2 instance... \n");

			//if(!hasManager(ec2)) { 
			//	System.out.println("Local Application :: Manager was not found. we now create an instance of it!");
			//	createManager(new RunInstancesRequest(),ec2,"t2.micro","hardwell","ami-b73b63a0"); 
			//} 

			System.out.println("Local Application :: done. Now, I`m just waiting for the results... :)");
			List<Message> result = mySQS.getInstance().awaitMessagesFromQueue(queueURLToGoBackTo,15,"Local Application");
			
			StringBuilder HTMLResult = null;
			String fileNameBeforeHTML = null,localUUID = null;
			SummaryFileReceipt r = null;
			for (Message msg : result) { 
				r = new Gson().fromJson(msg.getBody(), SummaryFileReceipt.class); 	
				System.out.println("LocalApplication :: Thanks Manager, for this URL I just got: " + r.getSummaryFileURL());
			}
			
			  AmazonS3 s3Client = new AmazonS3Client(new ProfileCredentialsProvider());
		      System.out.println("Local Application :: Downloading the summary file receipt I just got from the manager :)");
		      S3Object s3object = s3Client.getObject(new GetObjectRequest(
		      bucketName, r.getSummaryFileName()));
		      SummaryFile s = new Gson().fromJson(s3object.getObjectMetadata().getContentType(), SummaryFile.class);
		      HTMLResult = s.getHTMLResult();
		      localUUID = s.getLocalUUID();
				
		      //Creating the html file with the summary file brought by the manager...
				
			  File file = new File(outputFileName);
			 
			  if(file.exists()) {
	  			            System.out.println("File already exists! WTF, Dude...");
	  		  } else {
		  			        FileWriter fileWriter = null;
		  			        BufferedWriter bufferedWriter = null;
							fileWriter = new FileWriter(file);
		  			        bufferedWriter = new BufferedWriter(fileWriter);	  		
							bufferedWriter.write(readFile("beginning.html",StandardCharsets.UTF_8));
							bufferedWriter.write(HTMLResult.toString());
							bufferedWriter.write(readFile("end.html",StandardCharsets.UTF_8));
		    			    System.out.println("LocalApplication :: Thanks for the summary file! I have created the HTML!");
							bufferedWriter.flush();
							fileWriter.flush();
							bufferedWriter.close();
							fileWriter.close();
							System.out.println("Local Application ("+localUUID+"): I`m done. Thanks for serving me!");
							System.out.println("Local Application ("+localUUID+"): Now I can view the results on" + outputFileName+ " file! :)");
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


