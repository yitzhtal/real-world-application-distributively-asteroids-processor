package Runnables;

import JsonObjects.AtomicTask;
import JsonObjects.LocalApplicationMessage;
import JsonObjects.WorkerMessage;
import MainPackage.LocalApplication;
import MainPackage.Manager;
import MainPackage.mySQS;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.sqs.model.Message;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.codec.binary.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by assaf on 23/12/2016.
 */
public class LocalMsgHandlerRunnable implements Runnable{

    private List<Message> messages;
    private String queueURL;
    private String workersListenerURL;


    public LocalMsgHandlerRunnable(List<Message> messages, String queueURL, String workersListenerURL) {
        this.messages = messages;
        this.queueURL = queueURL;
        this.workersListenerURL = workersListenerURL;
    }

    @Override
    public void run() {
        if(!Manager.terminated) {
            String bucketName = null, keyName = null, queueURLtoGetBackTo = null,outputFileName = null,UUID = null;
            int n = 0,d = 0;
            Boolean isTerminated = false;
            Gson gson = new GsonBuilder().create();
            here:
            if(!messages.isEmpty()) {
                for(com.amazonaws.services.sqs.model.Message msg :  messages) {
                    System.out.println("Manager :: moving over the messages...");
                    LocalApplicationMessage m = gson.fromJson(msg.getBody(), LocalApplicationMessage.class);
                    mySQS.getInstance().deleteMessageFromQueue(queueURL,msg);
                    isTerminated = m.getIsTerminatedMessage();
                    bucketName = m.getBucketName();
                    keyName = m.getInputFileName();
                    queueURLtoGetBackTo = m.getQueueURLToGoBackTo();
                    outputFileName = m.getOutputFileName();
                    UUID = m.getUUID();
                    n = m.getN();
                    d = m.getD();
                    //add the queue URL to the hash map
                    Manager.mapLocalsQueueURLS.put(UUID, queueURLtoGetBackTo);
                }
            }
            AmazonS3 s3 = new AmazonS3Client(new BasicAWSCredentials(Manager.accessKey, Manager.secretKey));

            AtomicTask inputFileDetails = new AtomicTask();
            inputFileDetails.setLocalUUID(UUID);
            inputFileDetails.setIsTerminated(isTerminated);

            if(keyName != null && bucketName != null) {
                S3Object s3obj = null;
                s3obj = s3.getObject(new GetObjectRequest(bucketName, keyName));
                InputStream content = null;
                content=s3obj.getObjectContent();
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
            if(!afterThatDate(inputFileDetails.getStartDate(),inputFileDetails.getEndDate())) {
                System.out.println("Manager (localApplicationHandler) :: split the work amongs the workers...");

                ArrayList<AtomicTask> splits = null;
                try {
                    splits = splitWorkAmongsWorkers(inputFileDetails,d);
                } catch (ParseException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                System.out.println("Manager (localApplicationHandler) :: splits.size() = "+splits.size());
                System.out.println("Manager (localApplicationHandler) :: n = "+n);
                double numberOfWorkers = 0;
                int numberOfWorkersToCreate = 0;

                synchronized(this) {
                    numberOfWorkers = (double) splits.size() / n;
                    numberOfWorkersToCreate = (int)numberOfWorkers - Manager.currentNumberOfWorkers.get();
                    if(splits.size() % n > 0) {
                        numberOfWorkersToCreate++;
                    }
                    //puts in the relevant hash map...
                    Manager.mapLocals.put(UUID,splits);
                }

                System.out.println("Manager (localApplicationHandler) :: numberOfWorkers = "+numberOfWorkers);
                System.out.println("Manager (localApplicationHandler) :: numberOfWorkersToCreate = "+numberOfWorkersToCreate);

                for(int i=0; i< numberOfWorkersToCreate; i++) {
                    System.out.println("Manager (localApplicationHandler) :: creating a worker!");
                    //createAndRunWorker(new RunInstancesRequest(),ec2,"t2.micro","hardwell","ami-b73b63a0");
                    Manager.currentNumberOfWorkers.incrementAndGet();

                }
                System.out.println("Manager (localApplicationHandler) :: currentNumberOfWorkers = "+Manager.currentNumberOfWorkers.get());

                System.out.println("Manager (localApplicationHandler) :: currentNumberOfWorkers after calculation is = "+Manager.currentNumberOfWorkers.get());

                // move all splits towards the workers for them to handle...
                for (AtomicTask f : splits) {
                    WorkerMessage w = new WorkerMessage("AtomicTask",new Gson().toJson(f));
                    System.out.println("Manager (localApplicationHandler) :: send all tasks to workers queue...");
                    mySQS.getInstance().sendMessageToQueue(workersListenerURL,new Gson().toJson(w));
                }
            } else {
                System.out.println("Manager (localApplicationHandler) :: the input file I just got doesn`t make sense (Dates Issues");
                return;
            }
        }

    }

    public static void createAndRunWorker(RunInstancesRequest r, AmazonEC2Client ec2, String instancetype, String keyname, String imageid) {
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

    public static String getUserDataScript(){
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("#!/bin/bash");
        lines.add("echo y|sudo yum install java-1.8.0");
        lines.add("echo y|sudo yum remove java-1.7.0-openjdk");
        lines.add("wget https://s3.amazonaws.com/real-world-application-asteroids/AWSCredentials.zip -O AWSCredentialsTEMP.zip");
        lines.add("unzip -P audiocodes AWSCredentialsTEMP.zip");
        lines.add("wget https://s3.amazonaws.com/real-world-application-asteroids/worker.jar -O worker.jar");
        lines.add("java -jar worker.jar");
        String str = new String(org.apache.commons.codec.binary.Base64.encodeBase64(join(lines, "\n").getBytes()));
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

    public static boolean afterThatDate(String currentEndDate, String endDate) {
        if(currentEndDate == null || endDate == null) return false;

        String[] currentEndDateParts = currentEndDate.split("-");

        String currentEndDateYear = currentEndDateParts[0];
        String currentEndDateMonth = currentEndDateParts[1];
        String currentEndDateDay = currentEndDateParts[2];

        String[] endDateParts = endDate.split("-");
        String endDateYear = endDateParts[0];
        String endDateMonth = endDateParts[1];
        String endDateDay = endDateParts[2];

        if(Integer.parseInt(currentEndDateYear) > Integer.parseInt(endDateYear)) return true;
        if((Integer.parseInt(currentEndDateYear) == Integer.parseInt(endDateYear)) && (Integer.parseInt(currentEndDateMonth) > Integer.parseInt(endDateMonth))) return true;
        if((Integer.parseInt(currentEndDateYear) == Integer.parseInt(endDateYear)) && (Integer.parseInt(currentEndDateMonth) == Integer.parseInt(endDateMonth)) &&  (Integer.parseInt(currentEndDateDay) > Integer.parseInt(endDateDay))) return true;
        return false;
    }

    public static ArrayList<AtomicTask> splitWorkAmongsWorkers(AtomicTask input, int d) throws ParseException{
        Calendar cal1 = new GregorianCalendar();
        Calendar cal2 = new GregorianCalendar();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        Date date = null;
        date = sdf.parse(input.getStartDate());
        cal1.setTime(date);
        date = sdf.parse(input.getEndDate());
        cal2.setTime(date);

        int days = howManyDaysBetween(cal1.getTime(),cal2.getTime());
        ArrayList<AtomicTask> tasks = new ArrayList<AtomicTask>();

        int day_per_task = d-1;
        int count = 0;
        String end = null;
        while(count <= days) {

            //the last "work" is not "complete"
            if(days - count < d) {
                day_per_task = days - count;
            }

            String start = sdf.format(cal1.getTime());
            cal1.add(Calendar.DATE, day_per_task);
            end = sdf.format(cal1.getTime());
            AtomicTask split = new AtomicTask(start,end,input.getSpeedThreshold(),input.getDiameterThreshold(),input.getMissThreshold(),input.getIsTerminated());
            split.setDone(false);
            //sets this small piece of work a whole new UUID for the workers to think about working on different tasks!
            split.setLocalUUID(input.getLocalUUID());
            split.setTaskUUID(UUID.randomUUID().toString());
            tasks.add(split);
            count += day_per_task+1;
            cal1.add(Calendar.DATE, 1);
            //if(afterThatDate(sdf.format(cal1.getTime()),input.getEndDate())) {
            //System.out.println(sdf.format(cal1.getTime()) + "is after the time"+input.getEndDate() );
            //return tasks;
            //} else {
            // System.out.println(sdf.format(cal1.getTime()) + "is before the time"+input.getEndDate() );
            //}
        }
        return tasks;
    }

    public static int howManyDaysBetween(Date d1, Date d2){
        return (int)( (d2.getTime() - d1.getTime()) / (1000 * 60 * 60 * 24));
    }

}
