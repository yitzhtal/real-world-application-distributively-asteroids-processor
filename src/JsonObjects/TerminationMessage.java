package JsonObjects;

public class TerminationMessage {
		public boolean isTerminate() {
		return terminate;
	}

	public void setTerminate(boolean terminate) {
		this.terminate = terminate;
	}

		public boolean terminate = true;

		public TerminationMessage(boolean terminate) {
			super();
			this.terminate = terminate;
		}
}
