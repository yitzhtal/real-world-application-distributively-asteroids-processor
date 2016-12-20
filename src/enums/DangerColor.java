package enums;

public enum DangerColor {
    RED(0),YELLOW(1),GREEN(2),DEFAULT(3);
    
    private final int id;
	
	private DangerColor(int id) {
		this.id = id;
	}
	
	public int getId() {
		return this.id;
	}
}

