package JsonObjects;


public class WorkerMessage {

	private String type; 
	private String content; //can containt 2 kinds of messages: AtomicTask and TerminationMessage

	public WorkerMessage(String type, String content) {
		super();
		this.type = type;
		this.content = content;
	}

	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public String getContent() {
		return content;
	}
	public void setContent(String content) {
		this.content = content;
	}
}
