package MainPackage;

public class LocalApplicationMessage {
	    private String bucketName;
		private String inputFileName;
		private String queueURLToGoBackTo;
		private String outputFileName;
		int n;
		int d;
		
		public LocalApplicationMessage(String bucketName,String inputFileName, String queueURLToGoBackTo,String outputFileName,int n,int d) {
			this.bucketName = bucketName;
			this.inputFileName = inputFileName;
			this.queueURLToGoBackTo = queueURLToGoBackTo;
			this.outputFileName = outputFileName;
			this.n = n;
			this.d = d;
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
		
		public int getD() {
			return this.d;
		}
}
