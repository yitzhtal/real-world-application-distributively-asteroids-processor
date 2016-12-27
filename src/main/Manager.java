package main;


import java.util.ArrayList;

import java.util.List;


import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;


import Runnables.LocalMsgHandlerRunnable;
import Runnables.WorkersMsgHandlerRunnable;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;

import JsonObjects.AtomicTask;

import org.json.JSONArray;
import org.json.JSONException;

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
		
		PropertiesCredentials p = new PropertiesCredentials(LocalApplication.class.getResourceAsStream("/" + Constants.AWSCredentialsProperties));
	    
		accessKey = p.getAWSAccessKeyId();
		secretKey = p.getAWSSecretKey();

		mySQS.setAccessAndSecretKey(accessKey, secretKey);

		/* credentials handling ...  */

		
		
		/* Set data structures */

		String workersListenerURL = mySQS.getInstance().createQueue(Constants.workersListener);
		String managerListenerURL = mySQS.getInstance().createQueue(Constants.managerListener);
		mySQS.getInstance().createQueue(Constants.statisticsData);
		mapLocals = new ConcurrentHashMap<String, AtomicTasksTracker>();
		mapLocalsQueueURLS = new ConcurrentHashMap<String, String>();
		ExecutorService LocalApplicationHandlerExecutor = Executors.newFixedThreadPool(Constants.LocalApplicationHandlerFixedSizeThreadPool); 
		ExecutorService WorkersHandlerExecutor = Executors.newFixedThreadPool(Constants.WorkersHandlerFixedSizeThreadPool); 
		
		//substract one here, because the manager itself is running on an ec2 node...
		//LOCALcurrentNumberOfWorkers.set(LocalMsgHandlerRunnable.getCurrentAmountOfRunningOrPendingInstances(new AmazonEC2Client(new BasicAWSCredentials(accessKey,secretKey))) - 1);
		
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

		WorkersHandler = new Thread() {
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
