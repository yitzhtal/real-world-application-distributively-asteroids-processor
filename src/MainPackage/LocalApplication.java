package MainPackage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.commons.codec.binary.Base64;
import org.jets3t.service.S3ServiceException;
import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.model.S3Object;

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
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.util.IOUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class LocalApplication {	

	private static final String bucketName                           = "real-world-application-asteroids";
	private static final String credentialsFileName                  = "AWSCredentials.zip";
	public static final String All_local_applications_queue_name     = "all_local_applications_to_manager";
	
    private static String getUserDataScript(){
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
	
    public static void createManager(RunInstancesRequest request,AmazonEC2Client ec2) {
		System.out.println("Local Application :: Manager was not found. we now create an instance of it!");
		request.setInstanceType("t2.micro");
		request.setMinCount(1);
		request.setMaxCount(1);
		request.setImageId("ami-b73b63a0");
	    request.setKeyName("hardwell");
		request.setUserData(getUserDataScript());
		RunInstancesResult runInstances = ec2.runInstances(request);  
		List<com.amazonaws.services.ec2.model.Instance> instances=runInstances.getReservation().getInstances();
		CreateTagsRequest createTagsRequest = new CreateTagsRequest();
		createTagsRequest.withResources(instances.get(0).getInstanceId()).withTags(new Tag("name","manager"));
		ec2.createTags(createTagsRequest);
    }
    
    public static void uploadFileToS3(String inputFileName) {
    	AmazonS3 s3client = new AmazonS3Client(new ProfileCredentialsProvider());
		System.out.println("Local Application :: Uploading the input file to S3...\n");
		File file = new File(inputFileName);
		PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, inputFileName, file);
		s3client.putObject(putObjectRequest.withCannedAcl(CannedAccessControlList.PublicReadWrite));
    }
    
    public static boolean hasManager(List<Reservation> reservations) {
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
    	return (tag.getValue().equals("manager") && instance.getState().getName().equals("running"));
    }
	
	public static void main(String[] args) {
		
		String inputFileName = args[0];
		String outputFileName = args[1];
		int n = Integer.parseInt(args[2]);
		int d = Integer.parseInt(args[3]);
		
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
			/* credentials handling ...  */
			
			String All_local_application_queue_name_url = mySQS.getInstance().createQueue(All_local_applications_queue_name);
			
			uploadFileToS3(inputFileName); 		
			
			Gson gson = new GsonBuilder().create();
			String uuid = UUID.randomUUID().toString();
			String queueURLToGoBackTo = mySQS.getInstance().createQueue(uuid);
			
			LocalApplicationMessage m = new LocalApplicationMessage(bucketName,inputFileName,queueURLToGoBackTo,outputFileName,n,d);  
			
			//* Sending message to manager... //
			mySQS.getInstance().sendMessageToQueue(All_local_application_queue_name_url,gson.toJson(m));			
			/* make instance run ... */
			
			System.out.println("Local Application :: trying to run a manager ec2 instance... \n");
			
		    AmazonEC2Client ec2 = new AmazonEC2Client(new BasicAWSCredentials(accessKey,secretKey));
			RunInstancesRequest request = new RunInstancesRequest();
			DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest();
			List<Reservation> reservations  = ec2.describeInstances(describeInstancesRequest).getReservations();
			
			if(!hasManager(reservations)) { 
				createManager(request,ec2); 
			} 

			System.out.println("Local Application :: done. Now, I`m just waiting for the results... :)");
			List<Message> result = mySQS.getInstance().awaitMessagesFromQueue(queueURLToGoBackTo,5);
				
			for (Message msg : result) { 
				System.out.println(msg.getBody()); 
			}
			
			System.out.println("Local Application: done. Thanks for serving me!");
				
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


