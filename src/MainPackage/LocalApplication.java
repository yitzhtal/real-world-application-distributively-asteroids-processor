package MainPackage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;

import org.apache.commons.codec.binary.Base64;

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

public class LocalApplication {	
	private static final String bucketName      = "real-world-application-distributively-asteroids-processor";
	
	public static final String local_application_queue_name = "local_application_to_manager";
	public static final String local_application_queue_name_url = mySQS.getInstance().createQueue(local_application_queue_name);
	
	
    private static String getUserDataScript(){
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("wget https://s3.amazonaws.com/real-world-application-distributively-asteroids-processor/project.jar -P /home/ec2-user");
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
		
			//* Uploading file to S3... //*
			System.out.println("Local Application :: Uploading the input file to S3...\n");
			File file = new File(uploadFileName);
			s3client.putObject(new PutObjectRequest(bucketName, inputFileName, file));
			
			String twoPartMessageWithDelimiter = bucketName + "$";
			twoPartMessageWithDelimiter += inputFileName;
			
			//* Sending message to manager... //*
			mySQS.getInstance().sendMessageToQueue(local_application_queue_name_url,twoPartMessageWithDelimiter);
			
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
			
			
			
			/* make instance run ... */
			
			System.out.println("Local Application :: trying to run a manager ec2 instance... \n");
			
			AWSCredentials credentials = new BasicAWSCredentials(accessKey,secretKey);
		    AmazonEC2Client ec2 = new AmazonEC2Client(credentials);
			RunInstancesRequest request = new RunInstancesRequest();
			        
			request.setInstanceType(InstanceType.T2Micro.toString());
			        request.setMinCount(1);
			        request.setMaxCount(20);
			        request.setImageId("ami-b73b63a0");
			        request.setKeyName("hardwell");
			        request.setUserData(getUserDataScript());
			        ec2.runInstances(request);    
			        
			/* make instance run ... */
			
			System.out.println("Local Application: done.");
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


