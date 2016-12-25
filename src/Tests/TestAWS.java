package Tests;

import static org.junit.Assert.*;

import org.junit.Test;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;

import Runnables.LocalMsgHandlerRunnable;

public class TestAWS {

	@Test
	public void testGetCurrentAmountOfInstances() {
			assertEquals(0,LocalMsgHandlerRunnable.getCurrentAmountOfRunningInstances(new AmazonEC2Client(new BasicAWSCredentials("AKIAJAYPPW22636YFN6A","R4qxLzWNtLA8fWoby0T6A7ICRlypo3e0HASeIkDR"))));
	}

}
