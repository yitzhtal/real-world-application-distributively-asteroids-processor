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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
		/* credentials handling ...  */
		
		Properties properties = new Properties();
		String path = "AWSCredentials.properties";
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
		
		/* Manager waits for a message from the local applications main queue */
		
		System.out.println("Manager :: awaits a message stating the location of the input file on S3.");
		System.out.println("Manager :: fetching messages from queue: "+ LocalApplication.All_local_applications_queue_name);
		System.out.println("Manager :: queue URL is: "+ mySQS.getInstance().getQueueUrl(LocalApplication.All_local_applications_queue_name));
		
		String queueURL = mySQS.getInstance().getQueueUrl(LocalApplication.All_local_applications_queue_name);
		List<com.amazonaws.services.sqs.model.Message> messages = mySQS.getInstance().getMessagesFromQueue(queueURL);

		String bucketName = null, keyName = null, queueURLtoGetBackTo = null,outputFileName;
		int n,d;	
		Gson gson = new GsonBuilder().create();
		com.amazonaws.services.sqs.model.Message msgObject = null;
		if(!messages.isEmpty()) {
	        for(com.amazonaws.services.sqs.model.Message msg :  messages) {
	        	 System.out.println("Manager :: moving over the messages...");
	        	 msgObject = msg;
	             LocalApplicationMessage m = gson.fromJson(msgObject.getBody(), LocalApplicationMessage.class);
	     		 bucketName = m.getBucketName();
	    		 keyName = m.getInputFileName();
	    		 queueURLtoGetBackTo = m.getQueueURLToGoBackTo();
	    		 outputFileName = m.getOutputFileName();
	    		 n = m.getN();
	    		 d = m.getD();
	        }
		} else {
			System.out.println("Manager :: list of messages from local application is empty...");
		}
	
	    System.out.println("Manager :: sennding message back to local application! :)");    
	    mySQS.getInstance().sendMessageToQueue(queueURLtoGetBackTo,"Hi local application! I am done. here is what you wanted. :)");
  
	}
}
