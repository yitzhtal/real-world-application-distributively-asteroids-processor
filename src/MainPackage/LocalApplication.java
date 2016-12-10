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
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.util.IOUtils;

public class LocalApplication {	
	private static final String bucketName                           = "real-world-application-distributively-asteroids-processor";
	private static final String credentialsFileName                  = "AWSCredentials.zip";
	public static final String All_local_applications_queue_name     = "all_local_applications_to_manager";
	
    private static String getUserDataScript(){
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("#!/bin/bash");
        lines.add("echo y|sudo yum install java-1.8.0");
        lines.add("echo y|sudo yum remove java-1.7.0-openjdk");
        lines.add("wget https://s3.amazonaws.com/real-world-application-distributively-asteroids-processor/AWSCredentials.zip -O AWSCredentialsTEMP.zip");
        lines.add("unzip -P audiocodes AWSCredentialsTEMP.zip");
        lines.add("wget https://s3.amazonaws.com/real-world-application-distributively-asteroids-processor/manager.jar -O manager.jar");
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
	
	
	public static void main(String[] args) {
		
		String inputFileName = args[0];
		String outputFileName = args[1];
		String n = args[2];
		String d = args[3];
		
		System.out.println("Local Application :: has started...\n");
		System.out.println("Arguments received :: inputFileName = "+ inputFileName);		
		System.out.println("Arguments received :: outputFileName = "+ outputFileName);	
		System.out.println("Arguments received :: n = "+ n);	
		System.out.println("Arguments received :: d = "+ d + "\n\n");	
		
		final String uploadFileName  = inputFileName;
		
		AmazonS3 s3client = new AmazonS3Client(new ProfileCredentialsProvider());

		try {
			
			/* credentials handling ...  */
			
			Properties properties = new Properties();
			String path = "C:/Users/Tal Itshayek/Desktop/DistributedSystems/importexport-webservice-tool/AwsCredentials.properties";
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
			
			/* credentials handling ...  */
			
			mySQS.setAccessAndSecretKey(accessKey, secretKey);
			String All_local_application_queue_name_url = mySQS.getInstance().createQueue(All_local_applications_queue_name);
			
			//* Uploading file to S3... //*
			System.out.println("Local Application :: Uploading the input file to S3...\n");
			File file = new File(uploadFileName);
			s3client.putObject(new PutObjectRequest(bucketName, inputFileName, file));
			
			String MessageFromLocalApplication = bucketName + "||||||";
			String queueToGoBackTo = Integer.toString(System.identityHashCode(MessageFromLocalApplication));
			String queueURLToGoBackTo = mySQS.getInstance().createQueue(queueToGoBackTo);
			MessageFromLocalApplication += inputFileName + "||||||";
			MessageFromLocalApplication += queueURLToGoBackTo;
			
			//* Sending message to manager... //*
			mySQS.getInstance().sendMessageToQueue(All_local_application_queue_name_url,MessageFromLocalApplication);
			
			/* make instance run ... */
			
			System.out.println("Local Application :: trying to run a manager ec2 instance... \n");
			
			AWSCredentials credentials = new BasicAWSCredentials(accessKey,secretKey);
		    AmazonEC2Client ec2 = new AmazonEC2Client(credentials);
			RunInstancesRequest request = new RunInstancesRequest();
			        
			String userDataScript = getUserDataScript();
			System.out.println("userDataScript returned: "+ userDataScript);
			request.setInstanceType("t2.micro");
			        request.setMinCount(1);
			        request.setMaxCount(1);
			        request.setImageId("ami-b73b63a0");
			        request.setKeyName("hardwell");
			        request.setUserData(userDataScript);
			        ec2.runInstances(request);    
			        
			/* make instance run ... */
			
			System.out.println("Local Application: done. Now, I`m just waiting for the results... :)");
			
			System.out.println("LocalApplication :: singleton is still alive -> "+mySQS.getInstance());
			List<Message> result = mySQS.getInstance().awaitMessagesFromQueue(queueURLToGoBackTo,20);
			
			for (Message msg : result) {
				System.out.println(msg.getBody());
			}
			System.out.println("Local Application: done. Thanks for serving me!");
			
		} catch (AmazonServiceException ase) {
			System.out.println(""
					+ "Caught an AmazonServiceException, which " +
					"means your request made it " +
					"to Amazon S3, but was rejected with an error response" +
					" for some reason.");
			System.out.println("Error Message:    " + ase.getMessage());
			System.out.println("HTTP Status Code: " + ase.getStatusCode());
			System.out.println("AWS Error Code:   " + ase.getErrorCode());
			System.out.println("Error Type:       " + ase.getErrorType());
			System.out.println("Request ID:       " + ase.getRequestId());
		} catch (AmazonClientException ace) {
			System.out.println("Caught an AmazonClientException, which " +
					"means the client encountered " +
					"an internal error while trying to " +
					"communicate with S3, " +
					"such as not being able to access the network.");
			System.out.println("Error Message: " + ace.getMessage()); 
		} 
	}
}


