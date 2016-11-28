package MainPackage;

import java.util.List;

import com.amazonaws.services.sqs.model.Message;

public class Worker {
	  
	  public void run() {
		  outerloop:
		  while(true) {
		      List<Message> l1 = mySQS.getInstance().getMessagesFromQueue(mySQS.getInstance().getQueueUrl("MagrissoGilad23"));
		      for(int i=0; i<l1.size(); i++) {
		    	    if(l1.get(i).getBody().equals("ani sahi")) {
		    	    	 System.out.println("ata beemet sahi");
		    	    	 break outerloop;
		    	    }
		      }
		  }
	  }
}
