package JsonObjects;

public enum Color {
	   GREEN(1),
	   RED(2),
	   YELLOW(3),
	   DEFAULT(4);
	   
	   private int value;
	   
	   private Color(int value) {
	      this.value = value;
	   }
	   public int getValue() {
	      return value;
	   }
}