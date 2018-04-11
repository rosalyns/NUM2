package general;

public class Task extends Thread {
	public static int ID = 0;
	
	public enum Type {
		DOWNLOAD, UPLOAD
	}
	
	private boolean finished;
	private int id;
	private int offset;
	private String fileName;
	private Task.Type type;

	public Task(Task.Type type, String fileName) {
		this.fileName = fileName;
		this.type = type;
		this.id = ID;
		ID++;
	}
	
	public int id() {
		return this.id;
	}
	
	public Task.Type type() {
		return this.type();
	}
	
	public boolean finished() {
		return this.finished;
	}

}
