package JsonObjects;

public class SummaryFileReceipt {
	public String summaryFileURL;
	public String summaryFileName;
	public String message;
	
	public SummaryFileReceipt(String summaryFileURL, String summaryFileName,String message) {
		super();
		this.summaryFileURL = summaryFileURL;
		this.summaryFileName = summaryFileName;
		this.message = message;
	}
	
	public String getSummaryFileURL() {
		return summaryFileURL;
	}
	
	public String getMessage() {
		return message;
	}

	public void setSummaryFileURL(String summaryFileURL) {
		this.summaryFileURL = summaryFileURL;
	}
	
	public String getSummaryFileName() {
		return summaryFileName;
	}
	
	public void setSummaryFileName(String summaryFileName) {
		this.summaryFileName = summaryFileName;
	}
}
