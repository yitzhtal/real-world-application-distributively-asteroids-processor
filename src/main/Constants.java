package main;

import java.util.ArrayList;

public final class Constants  {

	  /**
	   The caller references the constants using <tt>Consts.EMPTY_STRING</tt>, 
	   and so on. Thus, the caller should be prevented from constructing objects of 
	   this class, by declaring this private constructor. 
	  */
	  private Constants() {
	    //this prevents even the native class from 
	    //calling this ctor as well :
	    throw new AssertionError();
	  }
	  
	//Queues related constants
	public static final String All_local_applications_queue_name     = "all_local_applications_to_manager";  
	public static final String workersListener         				 = "workersListener";
	public static final String managerListener         				 = "managerListener";
	public static final String statisticsData         				 = "statisticsData";
	
	public static final String[] NasaAPICacheList = { "FoXjWGh0jah5YCDDGpAQxNLOtZdifWJfXOsLpqif",
														"cvRQ9vyblxapj5N49OSxhY8aJHPZVADeLIOzh90J",
														"VA53l5L2G12FNaOPDdeKBFgz04u0U7dWuiugDO4P"};
	
	
	public static final int amountOfAttempsToReconnectToNasa      =   3;
	//AwaitMessage Delays
	//Sets the amount of times this specific thread shall check for a new message in their relevant queues.
	public static final int LocalApplicationHandlerAwaitMessageDelay      = 1; //in seconds
	public static final int WorkersHandlerAwaitMessageDelay               = 0; //in seconds
	public static final int LocalApplicationAwaitMessageDelay             = 0; //in seconds
	public static final int WorkerAwaitMessageDelay        				  = 0; //in seconds
	public static final int deleteingQueuesDelay                   		  = 2; //in seconds
	
	public static final int LocalApplicationHandlerFixedSizeThreadPool     = 30; //in seconds
	public static final int WorkersHandlerFixedSizeThreadPool              = 30; //in seconds
	
	//Credentials related constants
	public static final String AWSCredentialsProperties 			= "AWSCredentials.properties";
	public static final String credentialsURLFromS3 				= "https://s3.amazonaws.com/real-world-application-asteroids/AWSCredentials.zip";
	public static final String ZipFileName 							= "AWSCredentials.zip";
	public static final String ZipFilePassword						= "audiocodes";
	
	//HTML Related constants
	//public static final String beginningFromS3 = "https://s3.amazonaws.com/real-world-application-asteroids/beginning.html";
	public static final String beginningName = "beginning.html";
	//public static final String endFromS3 = "https://s3.amazonaws.com/real-world-application-asteroids/end.html";
	public static final String endName = "end.html";

	
	//S3 related constants
	public static final String bucketName           = "real-world-application-asteroids";
	
	
	//Instance realted constants
	public static final String ImageID 		= "ami-b73b63a0";
	public static final String InstanceType = "t2.micro";
	public static final String KeyName 		= "hardwell";
	
	//Total restriction is 20 instances.
	public static final int AmountOfInstancesRestrictionOnLocalApplication = 1;
	public static final int AmountOfInstancesRestrictionOnManager = 19;
	
}