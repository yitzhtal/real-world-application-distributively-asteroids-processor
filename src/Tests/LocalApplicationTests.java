package Tests;

import Runnables.LocalMsgHandlerRunnable;
import org.junit.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;

import MainPackage.LocalApplication;
import MainPackage.Manager;
import MainPackage.mySQS;

import static com.sun.xml.internal.ws.dump.LoggingDumpTube.Position.Before;
import static org.junit.Assert.*;


import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties; 

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;


@Component
public class LocalApplicationTests {

	@Autowired
	private LocalApplication l;
	private String accessKey;
	private String secretKey;
	private AmazonEC2Client ec2;
	private Manager m;
	
	@Before
	public void initialize() {
		Properties properties = new Properties();
		String path = "C:/Users/Tal Itshayek/Desktop/DistributedSystems/importexport-webservice-tool/AWSCredentials.properties";
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
	    ec2 = new AmazonEC2Client(new BasicAWSCredentials(accessKey,secretKey));
	}
	
	@Test
	public void testGetUserDataScript() {
		  String returned = "IyEvYmluL2Jhc2gKZWNobyB5fHN1ZG8geXVtIGluc3RhbGwgamF2YS0xLjguMAplY2hvIHl8c3VkbyB5dW0gcmVtb3ZlIGphdmEtMS43LjAtb3Blbmpkawp3Z2V0IGh0dHBzOi8vczMuYW1hem9uYXdzLmNvbS9yZWFsLXdvcmxkLWFwcGxpY2F0aW9uLWFzdGVyb2lkcy9BV1NDcmVkZW50aWFscy56aXAgLU8gQVdTQ3JlZGVudGlhbHNURU1QLnppcAp1bnppcCAtUCBhdWRpb2NvZGVzIEFXU0NyZWRlbnRpYWxzVEVNUC56aXAKd2dldCBodHRwczovL3MzLmFtYXpvbmF3cy5jb20vcmVhbC13b3JsZC1hcHBsaWNhdGlvbi1hc3Rlcm9pZHMvbWFuYWdlci5qYXIgLU8gbWFuYWdlci5qYXIKamF2YSAtamFyIG1hbmFnZXIuamFy";
		  assertEquals(LocalApplication.getUserDataScript(),returned);
	}
	
	
	//assuming no instances are running at the current moment
	@Test
	public void testHasManager() {
		  assertEquals(LocalApplication.hasManager(ec2),false);
		  LocalApplication.createManager(new RunInstancesRequest(),ec2,"t2.micro","hardwell","ami-b73b63a0"); 
		  //waits for the instance to get to a running state - 10 seconds sleep...
		  try {
			    System.out.println("testHasManager() is going to sleep. wait for the instance to wake up.");
			    Thread.sleep(50000);    
		  } catch(InterruptedException ex) {
			    Thread.currentThread().interrupt();
		  }
		  System.out.println("testHasManager() is awake!");
		  assertEquals(LocalApplication.hasManager(ec2),true);
	}
	
}
