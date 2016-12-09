package MainPackage;

import java.io.File;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.PutObjectRequest;

public class LocalApplication {	
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
		
		
		final String bucketName      = "real-world-application-distributively-asteroids-processor";
		final String keyName         = "AKIAJG2FA5HDEBQAHA6A";
		final String uploadFileName  = inputFileName;
		
		AmazonS3 s3client = new AmazonS3Client(new ProfileCredentialsProvider());

		try {
			System.out.println("Local Application :: Uploading the input file to S3...\n");
			File file = new File(uploadFileName);
			s3client.putObject(new PutObjectRequest(
					bucketName, keyName, file));
			
			String local_application_queue_name = "local_application_to_manager";
			String local_application_queue_name_url = mySQS.getInstance().createQueue(local_application_queue_name);
			mySQS.getInstance().sendMessageToQueue(local_application_queue_name_url, "Hi! I am local application!");						
					
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


