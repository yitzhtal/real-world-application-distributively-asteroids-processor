package MainPackage;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.*;

import java.io.FileInputStream;
import java.util.List;
import java.util.Properties;

public class mySQS {
	    private BasicAWSCredentials credentials;
	    private AmazonSQS sqs;
	    private static volatile mySQS awssqsUtil = new mySQS();

	    private mySQS() {
	    	try{
	    		Properties properties = new Properties();
	    		String path = "C:/Users/Tal Itshayek/Desktop/DistributedSystems/importexport-webservice-tool/AwsCredentials.properties";
	    		properties.load(new FileInputStream(path));
	    		this.credentials = new BasicAWSCredentials(properties.getProperty("accessKeyId"),properties.getProperty("secretKey"));
	    		this.sqs = new AmazonSQSClient(this.credentials);

	    	}catch(Exception e){
	    		System.out.println("exception while creating awss3client : " + e);
	    	}
	    }

	    public static mySQS getInstance(){
	        return awssqsUtil;
	    }

	    public AmazonSQS getAWSSQSClient(){
	         return awssqsUtil.sqs;
	    }

	    public String createQueue(String queueName){
	        CreateQueueRequest createQueueRequest = new CreateQueueRequest(queueName);
	        String queueUrl = this.sqs.createQueue(createQueueRequest).getQueueUrl();
	        return queueUrl;
	    }

	    public String getQueueUrl(String queueName){
	        GetQueueUrlRequest getQueueUrlRequest = new GetQueueUrlRequest(queueName);
	        return this.sqs.getQueueUrl(getQueueUrlRequest).getQueueUrl();
	    }


	    public ListQueuesResult listQueues(){
	       return this.sqs.listQueues();
	    }

	    public void sendMessageToQueue(String queueUrl, String message){
	        SendMessageResult messageResult =  this.sqs.sendMessage(new SendMessageRequest(queueUrl, message));
	        System.out.println(messageResult.toString());
	    }

	    public List<Message> getMessagesFromQueue(String queueUrl){
	       ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(queueUrl);
	       List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
	       return messages;
	    }

	    public void deleteMessageFromQueue(String queueUrl, Message message){
	        String messageRecieptHandle = message.getReceiptHandle();
	        System.out.println("message deleted : " + message.getBody() + "." + message.getReceiptHandle());
	        sqs.deleteMessage(new DeleteMessageRequest(queueUrl, messageRecieptHandle));
	    }
	}
