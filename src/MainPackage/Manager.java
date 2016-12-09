package MainPackage;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import javax.swing.text.html.HTMLDocument.Iterator;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import org.jets3t.service.S3ServiceException;
import org.jets3t.service.ServiceException;
import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.security.AWSCredentials;

public class Manager {
	
	private static String getStringFromInputStream(InputStream is) {

		BufferedReader br = null;
		StringBuilder sb = new StringBuilder();

		String line;
		try {

			br = new BufferedReader(new InputStreamReader(is));
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		return sb.toString();

	}

	public static void main(String[] args) throws ServiceException {
		/* Manager waits for a message from the local application */
		
		System.out.println("Manager :: awaits a message stating the location of the input file on S3.");
		
		List<com.amazonaws.services.sqs.model.Message> messages = mySQS.getInstance().getMessagesFromQueue(LocalApplication.local_application_queue_name_url);
	 
		String bucketName = null, keyName = null;
		com.amazonaws.services.sqs.model.Message msgObject = null;
		if(!messages.isEmpty()) {
	        for(com.amazonaws.services.sqs.model.Message msg :  messages) {
	        	 System.out.println("Manager :: moving over the messages, searching for one with $ to splice...");
	        	 msgObject = msg;
	        	 String[] tokens = msg.getBody().split(Pattern.quote("$"));
	     		 bucketName = tokens[0];
	    		 keyName = tokens[1];
	        }
		} else {
			System.out.println("Manager :: list of messages from local application is empty...");
		}
	
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
		
	    RestS3Service s3Service = new RestS3Service(new AWSCredentials(accessKey, secretKey));
	    
	    if(keyName != null && bucketName != null) {
			    S3Object s3obj = s3Service.getObject(bucketName,keyName);
			    mySQS.getInstance().deleteMessageFromQueue(LocalApplication.local_application_queue_name_url,msgObject);
				InputStream content = s3obj.getDataInputStream();
				System.out.println(getStringFromInputStream(content));
	    } else {
	    	    System.out.println("Manager :: Error! keyName = "+keyName + ", bucketName = "+bucketName);
	    }
	}
}
