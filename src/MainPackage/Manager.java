package MainPackage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;

import Runnables.LocalMsgHandlerRunnable;
import Runnables.WorkersMsgHandlerRunnable;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.sqs.model.*;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Message;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import JsonObjects.AtomicTask;
import JsonObjects.LocalApplicationMessage;
import JsonObjects.SummaryFile;
import JsonObjects.SummaryFileReceipt;
import JsonObjects.TerminationMessage;
import JsonObjects.WorkerMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Manager {
	
	public final static AtomicInteger currentNumberOfWorkers = new AtomicInteger();
	public final static AtomicInteger currentNumberOfTerminationMessages = new AtomicInteger();
	public volatile static boolean terminated = false;
	
	public static String accessKey;
	public static String secretKey;
	public static ConcurrentHashMap<String, AtomicTasksTracker> mapLocals;
	public static ConcurrentHashMap<String, String> mapLocalsQueueURLS;
	private static Thread localApplicationHandler = null;
	private static Thread WorkersHandler = null;

	public static Boolean isTerminate(){
		return terminated;
	}



	public static void printJsonArray(JSONArray printMe) throws JSONException {
		if(printMe == null) {
			System.out.println("Null");
			return ;
		} else {
			for(int i=0; i<printMe.length();i++) {
				System.out.println(printMe.get(i) + " , ");
			}
		}
	}

	public static void printHashMap(ConcurrentHashMap<String, ArrayList<AtomicTask>> f) {
		for (String s : f.keySet()) {
			String key = s.toString();
			String value = f.get(s).toString();  
			System.out.println("["+key + " -> " + value+"]");  
		} 
	}

	public static void main(String[] args) throws Exception {

		/* credentials handling ...  */

		
		  //C:/Users/Tal Itshayek/Desktop/DistributedSystems/importexport-webservice-tool/AWSCredentials.properties
		//C:/Users/assaf/Downloads/AWSCredentials.properties
	
        Properties prop = new Properties();
        try {
        	prop.load(new FileInputStream(Constants.path));
        } catch (IOException e1) {
            e1.printStackTrace();
        }
		

		accessKey = prop.getProperty("accessKeyId");
		secretKey = prop.getProperty("secretKey");
		mySQS.setAccessAndSecretKey(accessKey, secretKey);

		/* credentials handling ...  */

		/* Set data structures */

		String workersListenerURL = mySQS.getInstance().createQueue(Constants.workersListener);
		String managerListenerURL = mySQS.getInstance().createQueue(Constants.managerListener);
		mapLocals = new ConcurrentHashMap<String, AtomicTasksTracker>();
		mapLocalsQueueURLS = new ConcurrentHashMap<String, String>();
		ExecutorService LocalApplicationHandlerExecutor = Executors.newFixedThreadPool(5); 
		ExecutorService WorkersHandlerExecutor = Executors.newFixedThreadPool(5); 
		/* Set data structures */

		localApplicationHandler = new Thread() {
			public void run(){
				while(!terminated) {
					System.out.println("Manager :: localApplicationHandler :: awaits a message stating the location of the input file on S3.");
					System.out.println("Manager :: localApplicationHandler :: fetching messages from queue: "+ Constants.All_local_applications_queue_name);
					System.out.println("Manager :: localApplicationHandler :: queue URL is: "+ mySQS.getInstance().getQueueUrl(Constants.All_local_applications_queue_name));

					String queueURL = mySQS.getInstance().getQueueUrl(Constants.All_local_applications_queue_name);
					List<com.amazonaws.services.sqs.model.Message> messages = mySQS.getInstance().awaitMessagesFromQueue(queueURL,Constants.LocalApplicationHandlerAwaitMessageDelay,"Manager :: localApplicationHandler");
					System.out.println("Manager :: localApplicationHandler :: got a new message from local application... executing thread to handle the message on thread pool!");

					LocalApplicationHandlerExecutor.execute(new LocalMsgHandlerRunnable(messages, queueURL, workersListenerURL,accessKey,secretKey));
				}
				System.out.println("Manager :: localApplicationHandler :: terminated = true, therefore I`m not running anymore.");
				LocalApplicationHandlerExecutor.shutdown();
				return;
			}
		};

		WorkersHandler = new Thread(){
			public void run(){
				System.out.println("Manager :: workersHandler :: has started running...");
				System.out.println("Manager :: workersHandler :: terminated = " + terminated);
				while(!terminated || !mySQS.getInstance().getMessagesFromQueue(managerListenerURL,"Manager :: workersHandler").isEmpty()) {
							System.out.println("Manager (workersHandler) :: workersListener is not empty, fetching for messages....");
							List<com.amazonaws.services.sqs.model.Message> result = mySQS.getInstance().awaitMessagesFromQueue(managerListenerURL,Constants.WorkersHandlerAwaitMessageDelay,"Manager (workersHandler)");
							WorkersHandlerExecutor.execute(new WorkersMsgHandlerRunnable(result,managerListenerURL,localApplicationHandler,WorkersHandler,workersListenerURL,accessKey,secretKey));
				}
				//gets here only if terminated == true && managerListener is an empty queue.
				System.out.println("Manager :: workersHandler ::  terminated = true, therefore I`m not running anymore.");
				WorkersHandlerExecutor.shutdown();
				return;
			};

		};
						
		localApplicationHandler.start();
		WorkersHandler.start();
	}	
}
