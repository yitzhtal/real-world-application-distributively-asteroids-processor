package JsonObjects;

public class SummaryFile {
	
	public SummaryFile(String atomicAnalysisResult, String localUUID) {
		super();
		AtomicAnalysisResult = atomicAnalysisResult;
		this.localUUID = localUUID;
	}
	public String getAtomicAnalysisResult() {
		return AtomicAnalysisResult;
	}
	public void setAtomicAnalysisResult(String atomicAnalysisResult) {
		AtomicAnalysisResult = atomicAnalysisResult;
	}
	public String getLocalUUID() {
		return localUUID;
	}
	public void setLocalUUID(String localUUID) {
		this.localUUID = localUUID;
	}
	public String AtomicAnalysisResult;
	public String localUUID;
	

}
