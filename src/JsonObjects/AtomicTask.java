package JsonObjects;

import java.util.UUID;

public class AtomicTask {
           private String startDate;
           private String endDate;
           private int speedThreshold;
           private int diameterThreshold;
           private double missThreshold;
           private Boolean done;
           private String taskUUID;
           private String AtomicAnalysisResult;
           private String localUUID;
           private Boolean isTerminated;
           
           public AtomicTask() {

           }  

		   public AtomicTask(String startDate,String endDate,int speedThreshold,int diameterThreshold,double missThreshold,Boolean isTerminated) {
		       	this.startDate = startDate;
		       	this.endDate = endDate;
		       	this.speedThreshold = speedThreshold;
		        this.diameterThreshold = diameterThreshold;
		        this.done = false;
		        this.taskUUID = UUID.randomUUID().toString();
		        this.isTerminated = isTerminated;
		        this.missThreshold = missThreshold;
           }
		   
		   public AtomicTask(Boolean isTerminated) {
		        this.isTerminated = isTerminated;
           }
			
            public String getAtomicAnalysisResult() {
			return AtomicAnalysisResult;
			}
	
			public void setAtomicAnalysisResult(String atomicAnalysisResult) {
				AtomicAnalysisResult = atomicAnalysisResult;
			}
	
			public void setTaskUUID(String taskUUID) {
				this.taskUUID = taskUUID;
			}
			
	        public void setStartDate(String startDate) {
				this.startDate = startDate;
			}
	
			public void setEndDate(String endDate) {
				this.endDate = endDate;
			}
	
			public void setLocalUUID(String localUUID) {
				this.localUUID = localUUID;
			}
			
			
			public void setSpeedThreshold(int speedThreshold) {
				this.speedThreshold = speedThreshold;
			}
	
			public void setDiameterThreshold(int diameterThreshold) {
				this.diameterThreshold = diameterThreshold;
			}
	
			public void setMissThreshold(double missThreshold) {
				this.missThreshold = missThreshold;
			}
			
			public void setDone(Boolean flag) {
				this.done = flag;
			}
			

			public String getStartDate() {
				return startDate;
			}
			
			public String getLocalUUID() {
				return this.localUUID;
			}
			
			public String getTaskUUID() {
				return this.taskUUID;
			}
	
			public Boolean getDone() {
				return this.done;
			}
			
			public String getEndDate() {
				return endDate;
			}
	
			public int getSpeedThreshold() {
				return speedThreshold;
			}
	
			public int getDiameterThreshold() {
				return diameterThreshold;
			}
	
			public double getMissThreshold() {
				return missThreshold;
			}

			public Boolean getIsTerminated() {
				return isTerminated;
			}

			public void setIsTerminated(Boolean isTerminated) {
				this.isTerminated = isTerminated;
			}
}