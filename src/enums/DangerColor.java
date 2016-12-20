package enums;

public enum DangerColor {
    RED(0),GREEN(2),YELLOW(1),DEFAULT(3);
    
    private final int id;
	
	private DangerColor(int id) {
		this.id = id;
	}
	
	public int getId() {
		return this.id;
	}
}

