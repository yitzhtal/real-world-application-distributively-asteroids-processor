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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;

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
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Message;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import JsonObjects.InputFile;
import JsonObjects.LocalApplicationMessage;
import JsonObjects.SummaryFile;
import JsonObjects.SummaryFileReceipt;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import org.jets3t.service.S3ServiceException;
import org.jets3t.service.ServiceException;
import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.security.AWSCredentials;

public class Manager {

	public static final String workersListener         = "workersListener";
	public static final String managerListener         = "managerListener";
	private final static AtomicInteger currentNumberOfWorkers = new AtomicInteger();
	private volatile static boolean terminated = false;
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
    
	public static String getUserDataScript(){
		ArrayList<String> lines = new ArrayList<String>();
		lines.add("#!/bin/bash");
		lines.add("echo y|sudo yum install java-1.8.0");
		lines.add("echo y|sudo yum remove java-1.7.0-openjdk");
		lines.add("wget https://s3.amazonaws.com/real-world-application-asteroids/AWSCredentials.zip -O AWSCredentialsTEMP.zip");
		lines.add("unzip -P audiocodes AWSCredentialsTEMP.zip");
		lines.add("wget https://s3.amazonaws.com/real-world-application-asteroids/worker.jar -O worker.jar");
		lines.add("java -jar worker.jar");
		String str = new String(Base64.encodeBase64(join(lines, "\n").getBytes()));
		return str;
	}

	public static String join(Collection<String> s, String delimiter) {
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

	public static int howManyDaysBetween(Date d1, Date d2){
		return (int)( (d2.getTime() - d1.getTime()) / (1000 * 60 * 60 * 24));
	}

	public static void createAndRunWorker(RunInstancesRequest r,AmazonEC2Client ec2,String instancetype,String keyname,String imageid) {
		r.setInstanceType(instancetype);
		r.setMinCount(1);
		r.setMaxCount(1);
		r.setImageId(imageid);
		r.setKeyName(keyname);
		r.setUserData(getUserDataScript());
		RunInstancesResult runInstances = ec2.runInstances(r);  
		List<com.amazonaws.services.ec2.model.Instance> instances=runInstances.getReservation().getInstances();
		CreateTagsRequest createTagsRequest = new CreateTagsRequest();
		createTagsRequest.withResources(instances.get(0).getInstanceId()).withTags(new Tag("name","worker"));
		ec2.createTags(createTagsRequest);
	}

	public static ArrayList<InputFile> splitWorkAmongsWorkers(InputFile input, int d){
		Calendar cal1 = new GregorianCalendar();
		Calendar cal2 = new GregorianCalendar();

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

		Date date = null;
		try {
			date = sdf.parse(input.getStartDate());
		} catch (ParseException e) {
			e.printStackTrace();
		}

		cal1.setTime(date);
		try {
			date = sdf.parse(input.getEndDate());
		} catch (ParseException e) {
			e.printStackTrace();
		}
		cal2.setTime(date);

		int days = howManyDaysBetween(cal1.getTime(),cal2.getTime());
		ArrayList<InputFile> tasks = new ArrayList<InputFile>();

		int day_per_task = d;
		int count = 0;
		while(count != days) {

			//the last "work" is not "complete"
			if(days - count < d) {
				day_per_task = days % d;
			}

			String start = sdf.format(cal1.getTime());
			cal1.add(Calendar.DATE, day_per_task);
			String end = sdf.format(cal1.getTime());
			InputFile split = new InputFile(start,end,input.getSpeedThreshold(),input.getDiameterThreshold(),input.getDiameterThreshold(),input.getIsTerminated());
			split.setDone(false);
			split.setLocalUUID(input.getLocalUUID());
			tasks.add(split);
			//System.out.println(start+" - "+ end);
			count += day_per_task;
		}
		return tasks;
	}
	
	
	public static void setAndPackUpLocalApplication(String localUUID, StringBuilder allHTMLOutput,ConcurrentHashMap<String, String> mapLocalsQueueURLS) {
			String fileNameBeforeHTML = "AsteroidsAnalysis-" + localUUID;
			SummaryFile s = new  SummaryFile(fileNameBeforeHTML,allHTMLOutput,localUUID);
			File f = new File(fileNameBeforeHTML);
			FileWriter fileWriter = null;
			BufferedWriter bufferedWriter = null;
			try {
				fileWriter = new FileWriter(f);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			bufferedWriter = new BufferedWriter(fileWriter);
			try {
				bufferedWriter.write(new Gson().toJson(s));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			LocalApplication.uploadFileToS3(fileNameBeforeHTML);
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
	public static void printHashMap(ConcurrentHashMap<String, ArrayList<InputFile>> f) {
		for (String s : f.keySet()) {
	        String key = s.toString();
	        String value = f.get(s).toString();  
	        System.out.println("["+key + " -> " + value+"]");  
	    } 
	}
	
	public static void terminateInstances(ArrayList<String> instanceIdList,String accessKey,String secretKey)
    {
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
		String path = "C:/Users/Tal Itshayek/Desktop/DistributedSystems/importexport-webservice-tool/AWSCredentials.properties";  //"C:/Users/assaf/Downloads/AWSCredentials.properties";
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

		/* Set data structures */

		String workersListenerURL = mySQS.getInstance().createQueue(workersListener);
		String managerListenerURL = mySQS.getInstance().createQueue(managerListener);
		ConcurrentHashMap<String, ArrayList<InputFile>> mapLocals = new ConcurrentHashMap<String, ArrayList<InputFile>>();
		ConcurrentHashMap<String, String> mapLocalsQueueURLS = new ConcurrentHashMap<String, String>();

		/* Set data structures */

		Thread localApplicationHandler = new Thread(){
			public void run(){
				while(!terminated) {
								System.out.println("Manager :: awaits a message stating the location of the input file on S3.");
								System.out.println("Manager :: fetching messages from queue: "+ LocalApplication.All_local_applications_queue_name);
								System.out.println("Manager :: queue URL is: "+ mySQS.getInstance().getQueueUrl(LocalApplication.All_local_applications_queue_name));
				
								String queueURL = mySQS.getInstance().getQueueUrl(LocalApplication.All_local_applications_queue_name);
								List<com.amazonaws.services.sqs.model.Message> messages = mySQS.getInstance().awaitMessagesFromQueue(queueURL,5,"Manager (localApplicationHandler)");
				
								String bucketName = null, keyName = null, queueURLtoGetBackTo = null,outputFileName = null,UUID = null;
								int n = 0,d = 0;	
								Boolean isTerminated = false;
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
										UUID = m.getUUID();
										n = m.getN();
										d = m.getD();
										isTerminated = m.getIsTerminatedMessage();
										//add the queue URL to the hash map
										mapLocalsQueueURLS.put(UUID, queueURLtoGetBackTo);
									}
								}
				
								RestS3Service s3Service = new RestS3Service(new AWSCredentials(accessKey, secretKey));
								AmazonEC2Client ec2 = new AmazonEC2Client(new BasicAWSCredentials(accessKey,secretKey));
				
								InputFile inputFileDetails = new InputFile();
								inputFileDetails.setLocalUUID(UUID);
								inputFileDetails.setIsTerminated(isTerminated);
								String FileInput = null;
								if(keyName != null && bucketName != null) {
									S3Object s3obj = null;
									try {
										s3obj = s3Service.getObject(bucketName,keyName);
									} catch (S3ServiceException e1) {
										// TODO Auto-generated catch block
										e1.printStackTrace();
									}
									mySQS.getInstance().deleteMessageFromQueue(queueURL,msgObject);
									InputStream content = null;
									try {
										content = s3obj.getDataInputStream();
									} catch (ServiceException e1) {
										// TODO Auto-generated catch block
										e1.printStackTrace();
									}
									BufferedReader br = new BufferedReader(new InputStreamReader(content));
									String strLine=null,part1=null,part2=null;
				
									try {
										while ((strLine = br.readLine()) != null)   {
											String[] parts = strLine.split(": ");
											part1 = parts[0]; 
											part2 = parts[1]; 	
											if(part1.equals("start-date")) { inputFileDetails.setStartDate(part2); }
											if(part1.equals("end-date")) {inputFileDetails.setEndDate(part2);}
											if(part1.equals("speed-threshold")) {inputFileDetails.setSpeedThreshold(Integer.parseInt(part2)); }
											if(part1.equals("diameter-threshold")) {inputFileDetails.setDiameterThreshold(Integer.parseInt(part2)); }
											if(part1.equals("miss-threshold")) {inputFileDetails.setMissThreshold(Double.parseDouble(part2)); break;}
										}
									} catch (IOException e) {
										e.printStackTrace();
									}
				
									try {
										br.close();
									} catch (IOException e) {
										e.printStackTrace();
									}	
				
								} else {
									System.out.println("Manager :: Error! keyName = "+keyName + ", bucketName = "+bucketName);
								}
								
								System.out.println("Manager (localApplicationHandler) :: split the work amongs the workers...");
				
								ArrayList<InputFile> splits = splitWorkAmongsWorkers(inputFileDetails,d);
								int numberOfWorkers = splits.size() / n;
								int numberOfWorkersToCreate = numberOfWorkers - currentNumberOfWorkers.get();
				
								mapLocals.put(UUID,splits);
								
								System.out.println("Manager (localApplicationHandler) :: printing the hash map:");
								printHashMap(mapLocals);
								
								for(int i=0; i< numberOfWorkersToCreate; i++) {
									System.out.println("Manager (localApplicationHandler) :: creating a worker!");
									createAndRunWorker(new RunInstancesRequest(),ec2,"t2.micro","hardwell","ami-b73b63a0");
									currentNumberOfWorkers.incrementAndGet();
								}
				
								// move all splits towards the workers for them to handle...
								for (InputFile f : splits) {
									System.out.println("Manager (localApplicationHandler) :: send all tasks to workers queue...");
									mySQS.getInstance().sendMessageToQueue(workersListenerURL,new Gson().toJson(f));
								}
				}
				System.out.println("Mangaer :: locals thread was just stopped. I`m done on accepting new local application requests.");
			}
		};

		Thread workersHandler = new Thread(){
			public void run(){
				System.out.println("Manager (workersHandler) :: is running...");
				System.out.println("Manager (workersHandler) :: terminated = " + terminated);
				while(!terminated || !(mySQS.getInstance().getMessagesFromQueue(mySQS.getInstance().getQueueUrl(Manager.managerListener)).isEmpty())) {
							System.out.println("Manager (workersHandler) :: workersListener is not empty, fetching for messages....");
							List<com.amazonaws.services.sqs.model.Message> result = mySQS.getInstance().awaitMessagesFromQueue(mySQS.getInstance().getQueueUrl(Manager.managerListener),5,"Manager (workersHandler)");
							InputFile split = null;
							StringBuilder HTMLResult = null;
							for(com.amazonaws.services.sqs.model.Message msg : result) { 
										String m = msg.getBody();
										split = new Gson().fromJson(m, InputFile.class);
										System.out.println();
										System.out.println("Manager :: split =>" + new Gson().toJson(split));
										System.out.println();
										HTMLResult = split.getHTMLOutput();
										ArrayList<InputFile> splits = mapLocals.get(split.getLocalUUID());
										
										//*  problem was HERE !!!!!!!!!!!!!! //*
										//*  problem was HERE !!!!!!!!!!!!!! //*
										//*  problem was HERE !!!!!!!!!!!!!! //*
										//*  problem was HERE !!!!!!!!!!!!!! //*
										//*  problem was HERE !!!!!!!!!!!!!! //*
										//*  problem was HERE !!!!!!!!!!!!!! //*
										//*  problem was HERE !!!!!!!!!!!!!! //*
										//*  problem was HERE !!!!!!!!!!!!!! //*
										//*  problem was HERE !!!!!!!!!!!!!! //*
										//*  problem was HERE !!!!!!!!!!!!!! //*
										//*  problem was HERE !!!!!!!!!!!!!! //*
										//*  problem was HERE !!!!!!!!!!!!!! //*
										
										
									
					                    if(splits != null) {
												System.out.println("Manager (workersHandler) :: now splits is with the size of " + splits.size());
												for (InputFile f : splits) {
													System.out.println("Manager (workersHandler): "+f.getTaskUUID()+" task is with the status done? "+f.getDone());
												}
												for (InputFile f : splits) {
														if(f.getTaskUUID().equals(split.getTaskUUID())) {
															if(f.getDone() == false) {
																f.setDone(true);
																f.setHTMLOutput(HTMLResult);
															}
														}
												}
					                    } else {
					                    		System.out.println("Manager (workersHandler): splits is empty! the workers are not done yet.");
					                    }
							}  
				}
				//gets here only if terminated == true && managerListener is an empty queue.
				System.out.println("Manager :: workers handler thread was just stopped. There are no more messages on managerListener queue and we got a termination message.");
			}
		};



		//This thread is a deamon that runs and looks for FULLY finished tasks.
		//If it founds one, it sends the local application a message back.
		//If it is a termination message and there are no more locals that are waiting for results -> it can kill all workers.
		Thread sealTheDealDaemon = new Thread(){
			public void run() {
				System.out.println("Manager (sealTheDealDaemon) :: is running, searching for finished tasks to send back to the local application.");
				if(!mapLocals.isEmpty()) {
							System.out.println("Manager (sealTheDealDaemon) :: mapLocals hash map is not empty, I have something to work on...");
							System.out.println("Manager (sealTheDealDaemon) :: printing the mapLocals hash map...");
							printHashMap(mapLocals);
							for(ArrayList<InputFile> splits : mapLocals.values()) {
								System.out.println("sealTheDealDaemon :: is running over some local application...");
								//Iterate over one local application!!!
								//Checks if done ALL work !!!
								boolean done = true;
								for (InputFile f : splits) {
									done = true;
									if(f.getDone() == false) {
										System.out.println("sealTheDealDaemon :: "+f.getTaskUUID() +" task is yet to be completed...");
										done = false;
										break;
									}
								} 
			
								if(done) {
									System.out.println("Manager (sealTheDealDaemon) :: this local application is done!!! Well done workers!");
									StringBuilder allHTMLOutput = new StringBuilder();
									String localUUID = null;
									Boolean isTerminatedMessage = null;
									for (InputFile f : splits) {
										allHTMLOutput.append(f.getHTMLOutput());
										if(localUUID == null) { 
											localUUID = f.getLocalUUID();
										}
										if(isTerminatedMessage == null) {
											isTerminatedMessage = f.getIsTerminated();
										}
									} 
			
									setAndPackUpLocalApplication(localUUID,allHTMLOutput,mapLocalsQueueURLS);
									
									//done serving this specific local application. now is it a termination message?
									if(isTerminatedMessage) {
											    terminate();
											    System.out.println("Manager :: I just got a termination message. localApplicationHandler should finish in 50 sec.");
											    //lets the local application handler enought time to finish...
										  	    //sleeps for 50 seconds, this is the time we set for the workers to finish.
											    //if they are not done within this amount of time - their screwed
											    try {
													Thread.sleep(50000);
												} catch (InterruptedException e) {
													// TODO Auto-generated catch block
													e.printStackTrace();
												} 
											    
												localApplicationHandler.interrupt();
												workersHandler.interrupt();
			
											    Boolean done1 = true;
												for (ArrayList<InputFile> splits1 : mapLocals.values()) {
													done1 = true;
													for (InputFile f1 : splits1) {
														if(f1.getDone() == false) {
															done1 = false;
															break;
														}
													} 
			
													if(done1) {
														StringBuilder allHTMLOutput1 = null;
														String localUUID1 = null;	
														allHTMLOutput1 = new StringBuilder();
														
														for (InputFile f : splits) {
															allHTMLOutput1.append(f.getHTMLOutput());
															if(localUUID1 == null) { 
																localUUID1 = f.getLocalUUID();
															}
														}
														setAndPackUpLocalApplication(localUUID1,allHTMLOutput1,mapLocalsQueueURLS);	
													}
												}	
												shutDownAllInstancesByTag("worker",accessKey,secretKey);
												shutDownAllInstancesByTag("manager",accessKey,secretKey);
												Thread.currentThread().interrupt();
												return;
									}		
								}
							}
				}
			}
		};
		
	    sealTheDealDaemon.start();
		localApplicationHandler.start();
	    workersHandler.start();
	}
}