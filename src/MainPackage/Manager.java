package MainPackage;

public class Manager {	
	public void run() {
		   String url = mySQS.getInstance().createQueue("MagrissoGilad23");
		   
		   for(int i=0; i < 15; i++) {
			   mySQS.getInstance().sendMessageToQueue(url,"Hi! My name is Manager.");
			   if(i == 6) {
				   mySQS.getInstance().sendMessageToQueue(url,"ani sahi");
			   }
		   }
	}
}
