package Runnables;

import JsonObjects.AtomicTask;
import JsonObjects.SummaryFile;
import JsonObjects.SummaryFileReceipt;
import JsonObjects.TerminationMessage;
import JsonObjects.WorkerMessage;
import main.AtomicTasksTracker;
import main.Constants;
import main.LocalApplication;
import main.Manager;
import main.mySQS;
import enums.WorkerMessageType;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.sqs.model.Message;
import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONException;

public class WorkersMsgHandlerRunnable implements Runnable{

    private List<Message> result;
    private String managerListenerURL;
    private Thread localApplicationHandler;
    private Thread workersHandler;
    private String workersListenerURL;
    private String accessKey;
    private String secretKey;
    
    public WorkersMsgHandlerRunnable(List<Message> result, String managerListenerURL,Thread localApplicationHandler,Thread workersHandler,String workersListenerURL,String accessKey,String secretKey) {
        this.result = result;
        this.managerListenerURL = managerListenerURL;
        this.localApplicationHandler = localApplicationHandler;
        this.workersHandler = workersHandler;
        this.workersListenerURL = workersListenerURL;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
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
				+ Constants.bucketName
				+ "."
				+ "us-east-1"
				+ "."
				+ "amazonaws.com/"
				+ fileNameBeforeHTML;

		SummaryFileReceipt r = new SummaryFileReceipt(urlToFile,fileNameBeforeHTML,"Manager :: I`m done. This is the summary receipt. the file is waiting for you to download from s3."); 
		mySQS.getInstance().sendMessageToQueue(mapLocalsQueueURLS.get(localUUID),new Gson().toJson(r));		
	}
	
	public static void terminate(){
		Manager.terminated = true;
	}
	
	public static void shutDownAllSystem(Thread localApplicationHandler,Thread workersHandler,ConcurrentHashMap<String, AtomicTasksTracker> mapLocals,ConcurrentHashMap<String, String> mapLocalsQueueURLS,String accessKey,String secretKey) throws InterruptedException {
		System.out.println("Manager :: I just got a termination message.");
		terminate();
		Boolean doneForLeftOverLocalApplications = null;
		for (AtomicTasksTracker t : mapLocals.values()) {
			ArrayList<AtomicTask> tasksOfLeftOverLocalApplications = t.getTasks();
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
		System.out.println("Manager :: waiting " + Constants.deleteingQueuesDelay +" seconds before deleting queues...");
		Thread.sleep(Constants.deleteingQueuesDelay*1000);
		System.out.println("Manager :: deleting all queues from the system...");
		mySQS.getInstance().deleteQueueByURL(mySQS.getInstance().getQueueUrl(Constants.workersListener));
		mySQS.getInstance().deleteQueueByURL(mySQS.getInstance().getQueueUrl(Constants.managerListener));
		mySQS.getInstance().deleteQueueByURL(mySQS.getInstance().getQueueUrl(Constants.All_local_applications_queue_name));
		
		shutDownAllInstancesByTag("worker",accessKey,secretKey);
		shutDownAllInstancesByTag("manager",accessKey,secretKey);
		return;
	}
	
	public static void main(String[] args) { }
	
    @Override
    public void run() {
        if(!Manager.terminated) {
            AtomicTask AtomicTaskFromWorker = null;
            for(com.amazonaws.services.sqs.model.Message msg : result) {
                AtomicTaskFromWorker = new Gson().fromJson(msg.getBody(), AtomicTask.class);

                //deletes message from queue...
                mySQS.getInstance().deleteMessageFromQueue(managerListenerURL,msg);

                AtomicTasksTracker tracker = Manager.mapLocals.get(AtomicTaskFromWorker.getLocalUUID());
                System.out.println("Tracker here is with getDone = " + tracker.getDone() + ",and size()="+tracker.getTasks().size());
                ArrayList<AtomicTask> tasks = tracker.getTasks();

                if(tasks != null) {
                    for (AtomicTask f : tasks) {
                        if(f.getTaskUUID().equals(AtomicTaskFromWorker.getTaskUUID())) {
                            if(!f.getDone()) {
                                System.out.println("Manager :: workersMsgHandlerRunnable :: " + f.getTaskUUID() + " task is marked as DONE !!!");
                                tracker.incrementTaskDone();
                                f.setDone(true); 
                                f.setAtomicAnalysisResult(AtomicTaskFromWorker.getAtomicAnalysisResult());
                            } else {
                                System.out.println("Manager :: workersMsgHandlerRunnable :: " + f.getTaskUUID() + " task is already marked as done.");
                            }
                        }
                    }
                }
                System.out.println("Manager :: workersMsgHandlerRunnable :: Tracker asks if "+tracker.getTasks().size() +" == "+tracker.getDone());
                if(tracker.isDone()) {
                	System.out.println("Manager :: workersMsgHandlerRunnable :: this local application is done! Well done workers!");
                	String localUUID = null;
                	JSONArray AtomicAnalysisResultAsJSONArray = null;
                	Boolean isTerminationMessage = null;
                	System.out.println("Manager :: workersMsgHandlerRunnable ::isTerminationMessage was" + isTerminationMessage +" here.");
                	System.out.println("Manager :: workersMsgHandlerRunnable :: tasks size here is" + tasks.size() +" here.");
                	for (AtomicTask f : tasks) {
                		try {
                			System.out.println("Manager :: workersMsgHandlerRunnable :: concatinating JSON`s....");
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
                			System.out.println("Manager :: workersMsgHandlerRunnable :: isTerminationMessage was" + isTerminationMessage +" here.");
                		}
                	} 
                	System.out.println("Manager :: workersMsgHandlerRunnable :: AtomicAnalysisResultAsJSONArray with value of"+AtomicAnalysisResultAsJSONArray);
                	try {
                		setAndPackUpLocalApplication(localUUID,AtomicAnalysisResultAsJSONArray.toString(),Manager.mapLocalsQueueURLS,Manager.accessKey,Manager.secretKey);
                	} catch (IOException e1) {
                		// TODO Auto-generated catch block
                		e1.printStackTrace();
                	}
                	Manager.mapLocals.remove(localUUID);
                	System.out.println("Manager :: workersMsgHandlerRunnable :: removing localUUID "+localUUID+ "from the hash map...");
                	//done serving this specific local application. now is it a termination message?
                	System.out.println("Manager :: workersMsgHandlerRunnable :: isTerminationMessage = "+isTerminationMessage);
                	System.out.println("Manager :: workersMsgHandlerRunnable :: currentNumberOfTerminationMessages = "+Manager.currentNumberOfTerminationMessages.get());

                	if(isTerminationMessage && Manager.currentNumberOfTerminationMessages.get()==0) {
                		Manager.currentNumberOfTerminationMessages.incrementAndGet();
                		WorkerMessage t1 = new WorkerMessage(WorkerMessageType.TerminationMessage,new Gson().toJson(new TerminationMessage(true)));
                		for(int i=0; i < Manager.currentNumberOfWorkers.get(); i++) {
                			System.out.println("Sends " + Manager.currentNumberOfWorkers.get()+ " termination messages to workers.");
                			mySQS.getInstance().sendMessageToQueue(workersListenerURL,new Gson().toJson(t1));
                		}
                		System.out.println("Manager :: workersMsgHandlerRunnable :: Calling shutDownAllSystem()");
                		try {
                			shutDownAllSystem(localApplicationHandler,workersHandler,Manager.mapLocals,Manager.mapLocalsQueueURLS,accessKey,secretKey);
                		} catch (InterruptedException e) {
                			// TODO Auto-generated catch block
                			e.printStackTrace();
                		}
                	}
                }      
            }
        }
    }
}