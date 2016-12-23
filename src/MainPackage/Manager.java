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
	public static final String workersListener         = "workersListener";
	public static final String managerListener         = "managerListener";
	public final static AtomicInteger currentNumberOfWorkers = new AtomicInteger();
	private final static AtomicInteger currentNumberOfTerminationMessages = new AtomicInteger();
	public volatile static boolean terminated = false;
	public static final int sealTheDealDelay = 10;	
	public static String accessKey;
	public static String secretKey;
	public static ConcurrentHashMap<String, ArrayList<AtomicTask>> mapLocals;
	public static ConcurrentHashMap<String, String> mapLocalsQueueURLS;

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

	public static void terminate(){
		terminated = true;
	}

	public static Boolean isTerminate(){
		return terminated;
	}

	private static JSONArray concatArray(JSONArray arr1, JSONArray arr2)
			throws JSONException {
		JSONArray result = new JSONArray();
		if(arr1 == null) return arr2;
		if(arr2 == null) return arr1;

		for (int i = 0; i < arr1.length(); i++) {
			result.put(arr1.get(i));
		}
		for (int i = 0; i < arr2.length(); i++) {
			result.put(arr2.get(i));
		}
		return result;
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


	public static void shutDownAllSystem(Thread localApplicationHandler,Thread workersHandler,ConcurrentHashMap<String, ArrayList<AtomicTask>> mapLocals,ConcurrentHashMap<String, String> mapLocalsQueueURLS,String accessKey,String secretKey) throws InterruptedException {
		System.out.println("Manager :: I just got a termination message.");
		terminate();
		Boolean doneForLeftOverLocalApplications = null;
		for (ArrayList<AtomicTask> tasksOfLeftOverLocalApplications : mapLocals.values()) {
			doneForLeftOverLocalApplications = true;
			for (AtomicTask f1 : tasksOfLeftOverLocalApplications) {
				if(f1.getDone() == false) {
					doneForLeftOverLocalApplications = false;
					break;
				}
			} 

			if(doneForLeftOverLocalApplications) {
				String localUUIDofOtherLocals = null;	
				JSONArray AtomicAnalysisResultAsJSONArrayofOtherLocals = null;

				for (AtomicTask f : tasksOfLeftOverLocalApplications) {
					try {
						JSONArray a = new JSONArray(f.getAtomicAnalysisResult());
						AtomicAnalysisResultAsJSONArrayofOtherLocals = concatArray(AtomicAnalysisResultAsJSONArrayofOtherLocals,a);
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					if(localUUIDofOtherLocals == null) { 
						localUUIDofOtherLocals = f.getLocalUUID();
					}
				}
				try {
					setAndPackUpLocalApplication(localUUIDofOtherLocals,AtomicAnalysisResultAsJSONArrayofOtherLocals.toString(),mapLocalsQueueURLS,accessKey,secretKey);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}	
			}
		}	
		//shutDownAllInstancesByTag("worker",accessKey,secretKey);
		//shutDownAllInstancesByTag("manager",accessKey,secretKey);
		System.out.println("Manager :: waiting 10 seconds before deleting queues...");
		Thread.sleep(10*1000);
		System.out.println("Manager :: deleting all queues from the system...");
		mySQS.getInstance().deleteQueueByURL(mySQS.getInstance().getQueueUrl(workersListener));
		mySQS.getInstance().deleteQueueByURL(mySQS.getInstance().getQueueUrl(managerListener));
		mySQS.getInstance().deleteQueueByURL(mySQS.getInstance().getQueueUrl(LocalApplication.All_local_applications_queue_name));
		return;
	}	

	@SuppressWarnings("resource")
	public static void setAndPackUpLocalApplication(String localUUID, String AtomicAnalysisResult,ConcurrentHashMap<String, String> mapLocalsQueueURLS,String accessKey,String secretKey) throws IOException {
		System.out.println("inside setAndPackUpLocalApplication()....");
		System.out.println("AtomicAnalysisResult="+AtomicAnalysisResult);
		String fileNameBeforeHTML = "AsteroidsAnalysis-" + localUUID;

		SummaryFile s = new SummaryFile(AtomicAnalysisResult,localUUID);
		String SummaryFileAsJson = new Gson().toJson(s);


		try{
			PrintWriter writer = new PrintWriter(new File(fileNameBeforeHTML), "UTF-8");
			writer.println(SummaryFileAsJson);
			writer.close();
		} catch (IOException e) {
			// do something
		}

		LocalApplication.uploadFileToS3(fileNameBeforeHTML,accessKey,secretKey);
		String urlToFile = "https://"
				+ LocalApplication.bucketName
				+ "."
				+ "us-east-1"
				+ "."
				+ "amazonaws.com/"
				+ fileNameBeforeHTML;

		SummaryFileReceipt r = new SummaryFileReceipt(urlToFile,fileNameBeforeHTML,"Manager :: I`m done. This is the summary receipt. the file is waiting for you to download from s3."); 
		mySQS.getInstance().sendMessageToQueue(mapLocalsQueueURLS.get(localUUID),new Gson().toJson(r));		
	}

	public static void shutDownAllInstancesByTag(String tag,String accessKey,String secretKey) {
		DescribeInstancesRequest request = new DescribeInstancesRequest();
		DescribeInstancesResult result = new AmazonEC2Client(new BasicAWSCredentials(accessKey,secretKey)).describeInstances(request.withFilters());
		List<Reservation> reservations = result.getReservations();
		ArrayList<String> instancesToTerminate = new ArrayList<String>() ;
		for (Reservation reservation : reservations) {
			List<Instance> instances = reservation.getInstances();
			for (Instance instance : instances) {
				if(instance.getTags().get(0).getValue().equals(tag) && (!instance.getState().getName().equals("terminate")) &&  (instance.getState().getName().equals("running") || instance.getState().getName().equals("pending"))) {
					System.out.println("Manager :: instance name: " + instance.getTags().get(0).getValue() + "id: " + instance.getInstanceId() + ",state: " + instance.getState().getName() + ", is going to terminate!");
					instancesToTerminate.add(instance.getInstanceId());
				}
			}
		}
		terminateInstances(instancesToTerminate,accessKey,secretKey) ;
		System.out.println("Manager :: " + instancesToTerminate.size() + " " + tag + " has been terminated");
	}

	public static void printHashMap(ConcurrentHashMap<String, ArrayList<AtomicTask>> f) {
		for (String s : f.keySet()) {
			String key = s.toString();
			String value = f.get(s).toString();  
			System.out.println("["+key + " -> " + value+"]");  
		} 
	}

	public static void terminateInstances(ArrayList<String> instanceIdList,String accessKey,String secretKey) {
		try {
			TerminateInstancesRequest terminateRequest = new TerminateInstancesRequest(instanceIdList);
			new AmazonEC2Client(new BasicAWSCredentials(accessKey,secretKey)).terminateInstances(terminateRequest);
		} catch (AmazonServiceException e) {
			// Write out any exceptions that may have occurred.
			System.out.println("Error terminating instances");
			System.out.println("Caught Exception: " + e.getMessage());
			System.out.println("Reponse Status Code: " + e.getStatusCode());
			System.out.println("Error Code: " + e.getErrorCode());
			System.out.println("Request ID: " + e.getRequestId());
		}
	}

	public static void main(String[] args) throws Exception {

		/* credentials handling ...  */

		Properties properties = new Properties();
		String path = "C:/Users/assaf/Downloads/AWSCredentials.properties";  //C:/Users/Tal Itshayek/Desktop/DistributedSystems/importexport-webservice-tool/AWSCredentials.properties
		try {
			properties.load(new FileInputStream(path));
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		accessKey = properties.getProperty("accessKeyId");
		secretKey = properties.getProperty("secretKey");
		mySQS.setAccessAndSecretKey(accessKey, secretKey);

		/* credentials handling ...  */

		/* Set data structures */

		String workersListenerURL = mySQS.getInstance().createQueue(workersListener);
		String managerListenerURL = mySQS.getInstance().createQueue(managerListener);
		mapLocals = new ConcurrentHashMap<String, ArrayList<AtomicTask>>();
		mapLocalsQueueURLS = new ConcurrentHashMap<String, String>();
		ExecutorService localApplicationHandlerExecutor = Executors.newFixedThreadPool(5); 
		ExecutorService workersHandlerExecutor = Executors.newFixedThreadPool(5); 

		/* Set data structures */

		Thread localApplicationHandler = new Thread(){
			public void run(){
				while(!terminated) {
					System.out.println("Manager :: localApplicationHandler :: awaits a message stating the location of the input file on S3.");
					System.out.println("Manager :: localApplicationHandler :: fetching messages from queue: "+ LocalApplication.All_local_applications_queue_name);
					System.out.println("Manager :: localApplicationHandler :: queue URL is: "+ mySQS.getInstance().getQueueUrl(LocalApplication.All_local_applications_queue_name));

					String queueURL = mySQS.getInstance().getQueueUrl(LocalApplication.All_local_applications_queue_name);
					List<com.amazonaws.services.sqs.model.Message> messages = mySQS.getInstance().awaitMessagesFromQueue(queueURL,5,"Manager (localApplicationHandler)");
					System.out.println("Manager :: localApplicationHandler :: got a new message from local application... executing thread to handle the message on thread pool!");

					localApplicationHandlerExecutor.execute(new LocalMsgHandlerRunnable(messages, queueURL, workersListenerURL));
				}
			}
		};

		Thread workersHandler = new Thread(){
			public void run(){
				System.out.println("Manager (workersHandler) :: has started running...");
				System.out.println("Manager (workersHandler) :: terminated = " + terminated);
				while(!terminated || !mySQS.getInstance().getMessagesFromQueue(managerListenerURL,"Manager (workersHandler)").isEmpty()) {
							System.out.println("Manager (workersHandler) :: workersListener is not empty, fetching for messages....");
							List<com.amazonaws.services.sqs.model.Message> result = mySQS.getInstance().awaitMessagesFromQueue(managerListenerURL,5,"Manager (workersHandler)");
							workersHandlerExecutor.execute(new WorkersMsgHandlerRunnable(result,managerListenerURL));
				}
				//gets here only if terminated == true && managerListener is an empty queue.
				System.out.println("Manager :: workers handler thread was just stopped. There are no more messages on managerListener queue and we got a termination message.");
				return;
			};

		};

		//This thread is a deamon that runs and looks for FULLY finished tasks.
		//If it founds one, it sends the local application a message back.
		//If it is a termination message and there are no more locals that are waiting for results -> it can kill all workers.
		Thread sealTheDealDaemon = new Thread(){
			public void run() {
				while(!terminated) {
					System.out.println("Manager (sealTheDealDaemon) :: is running, searching for finished tasks to send back to the local application.");
					if(!mapLocals.isEmpty()) {
						System.out.println("Manager (sealTheDealDaemon) :: mapLocals hash map is not empty, I have something to work on...");
						System.out.println("Manager (sealTheDealDaemon) :: printing the mapLocals hash map...");
						printHashMap(mapLocals);
						for(ArrayList<AtomicTask> tasks : mapLocals.values()) {
							if(!tasks.isEmpty()) {
								System.out.println("tasks is not empty and has a size of"+tasks.size() );
								System.out.println("sealTheDealDaemon :: is running over some local application...");
								//Iterate over one local application!!!
								//Checks if done ALL work !!!
								boolean done = true;
								here:
									for (AtomicTask f : tasks) {
										if(f.getDone() == false) {
											System.out.println("sealTheDealDaemon :: "+f.getTaskUUID() +" task is yet to be completed...");
											done = false;
											break here;
										} else {
											System.out.println("sealTheDealDaemon :: "+f.getTaskUUID() +" task is COMPLETED!!!...");
										}
									} 

								if(done) {
									System.out.println("Manager (sealTheDealDaemon) :: this local application is done!!! Well done workers!");
									String localUUID = null;
									JSONArray AtomicAnalysisResultAsJSONArray = null;
									Boolean isTerminationMessage = null;
									System.out.println("isTerminationMessage was" + isTerminationMessage +" here.");
									System.out.println("tasks size here is" + tasks.size() +" here.");
									for (AtomicTask f : tasks) {
										try {
											System.out.println("sealTheDealDaemon :: concatinating....");
											JSONArray a = new JSONArray(f.getAtomicAnalysisResult());
											AtomicAnalysisResultAsJSONArray = concatArray(AtomicAnalysisResultAsJSONArray,a);

										} catch (JSONException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}
										if(localUUID == null) { 
											localUUID = f.getLocalUUID();
										}
										if(isTerminationMessage == null) { 
											isTerminationMessage = f.getIsTerminated();
											System.out.println("isTerminationMessage was" + isTerminationMessage +" here.");
										}
									} 
									System.out.println("AtomicAnalysisResultAsJSONArray with value of"+AtomicAnalysisResultAsJSONArray);
									try {
										setAndPackUpLocalApplication(localUUID,AtomicAnalysisResultAsJSONArray.toString(),mapLocalsQueueURLS,accessKey,secretKey);
									} catch (IOException e1) {
										// TODO Auto-generated catch block
										e1.printStackTrace();
									}
									mapLocals.remove(localUUID);
									System.out.println("removing localUUID "+localUUID+ "from the hash map...");
									//done serving this specific local application. now is it a termination message?
									System.out.println("isTerminationMessage = "+isTerminationMessage);
									System.out.println("currentNumberOfTerminationMessages = "+currentNumberOfTerminationMessages.get());

									if(isTerminationMessage && currentNumberOfTerminationMessages.get()==0) {
										currentNumberOfTerminationMessages.incrementAndGet();
										WorkerMessage t = new WorkerMessage("TerminationMessage",new Gson().toJson(new TerminationMessage(true)));
										for(int i=0; i < currentNumberOfWorkers.get(); i++) {
											System.out.println("Sends " + currentNumberOfWorkers.get()+ " termination messages to workers.");
											mySQS.getInstance().sendMessageToQueue(workersListenerURL,new Gson().toJson(t));
										}
										System.out.println("Manager (sealTheDealDaemon) :: Calling shutDownAllSystem()");
										try {
											shutDownAllSystem(localApplicationHandler,workersHandler,mapLocals,mapLocalsQueueURLS,accessKey,secretKey);
										} catch (InterruptedException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}
									}
								}
							}
						}
					} else {
						if(terminated && currentNumberOfTerminationMessages.get()==0) {
							currentNumberOfTerminationMessages.incrementAndGet();
							WorkerMessage t = new WorkerMessage("TerminationMessage",new Gson().toJson(new TerminationMessage(true)));
							for(int i=0; i < currentNumberOfWorkers.get(); i++) {
								System.out.println("Sends " + currentNumberOfWorkers.get()+ " termination messages to workers.");
								mySQS.getInstance().sendMessageToQueue(workersListenerURL,new Gson().toJson(t));
							}
							System.out.println("Manager (sealTheDealDaemon) :: Calling shutDownAllSystem()");
							try {
								shutDownAllSystem(localApplicationHandler,workersHandler,mapLocals,mapLocalsQueueURLS,accessKey,secretKey);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}

					try {
						Thread.sleep(1000*sealTheDealDelay);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}
				System.out.println("Manager (sealTheDealDaemon) :: terminated = "+terminated+", therefore I`m not running anymore");
			};
		};	

		
		sealTheDealDaemon.start();
		localApplicationHandler.start();
		workersHandler.start();
	}	
}
