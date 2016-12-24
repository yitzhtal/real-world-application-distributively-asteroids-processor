package enums;

public enum WorkerMessageType {
	AtomicTask(0),TerminationMessage(1);
	
    private final int id;
	
	private WorkerMessageType(int id) {
		this.id = id;
	}
	
	public int getId() {
		return this.id;
	}
}