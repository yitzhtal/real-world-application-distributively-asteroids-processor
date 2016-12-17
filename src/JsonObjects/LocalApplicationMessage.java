package JsonObjects;

public class LocalApplicationMessage {
	    private String bucketName;
		private String inputFileName;
		private String queueURLToGoBackTo;
		private String outputFileName;
        private String UUID;
    	public Boolean isTerminatedMessage;
		int n;
		int d;
		
		public LocalApplicationMessage(String bucketName,String inputFileName, String queueURLToGoBackTo,String outputFileName,int n,int d,String UUID,Boolean isTerminatedMessage) {
			this.bucketName = bucketName;
			this.inputFileName = inputFileName;
			this.queueURLToGoBackTo = queueURLToGoBackTo;
			this.outputFileName = outputFileName;
			this.n = n;
			this.d = d;
			this.UUID = UUID;
			this.isTerminatedMessage = isTerminatedMessage;
		}

		public Boolean getIsTerminatedMessage() {
			return isTerminatedMessage;
		}

		public void setIsTerminatedMessage(Boolean isTerminatedMessage) {
			this.isTerminatedMessage = isTerminatedMessage;
		}

		public String getBucketName() {
			return this.bucketName;
		}
		
		public String getInputFileName() {
			return this.inputFileName;
		}
		
		public String getQueueURLToGoBackTo() {
			return this.queueURLToGoBackTo;
		}
		
		public String getOutputFileName() {
			return this.outputFileName;
		}
		
		public int getN() {
			return this.n;
		}
		
		public String getUUID() {
			return this.UUID;
		}
		
		public int getD() {
			return this.d;
		}
}
