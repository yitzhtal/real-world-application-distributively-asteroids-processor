package JsonObjects;

public class SummaryFile {
	
	public StringBuilder HTMLResult;
	public String localUUID;
	
	
	public SummaryFile(String fileNameBeforeHTML,StringBuilder HTMLResult,String localUUID) {
		this.HTMLResult = HTMLResult;
		this.localUUID = localUUID;
	}
		
	public StringBuilder getHTMLResult() {
		return HTMLResult;
	}
	
	public void setHTMLResult(StringBuilder HTMLResult) {
		this.HTMLResult = HTMLResult;
	}
	
	public String getLocalUUID() {
		return localUUID;
	}
	
	public void setLocalUUID(String localUUID) {
		this.localUUID = localUUID;
	}
}
