package JsonObjects;

import enums.WorkerMessageType;

public class WorkerMessage {

	private WorkerMessageType type; 
	private String content; //can containt 2 kinds of messages: AtomicTask and TerminationMessage

	public WorkerMessage(WorkerMessageType type, String content) {
		super();
		this.type = type;
		this.content = content;
	}

	public WorkerMessageType getType() {
		return type;
	}
	public void setType(WorkerMessageType type) {
		this.type = type;
	}
	public String getContent() {
		return content;
	}
	public void setContent(String content) {
		this.content = content;
	}
}
