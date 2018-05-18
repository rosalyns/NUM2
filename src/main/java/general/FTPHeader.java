package general;

public class FTPHeader {
	private int taskId;
	private int seqNo; 
	private int ackNo;
	private int flags; 
	private int windowSize;

	public FTPHeader(int taskId, int seqNo, int ackNo, int flags, int windowSize) {
		this.taskId = taskId;
		this.seqNo = seqNo;
		this.ackNo = ackNo;
		this.flags = flags;
		this.windowSize = windowSize;
	}
	
	public int getTaskId() {
		return taskId;
	}

	public int getSeqNo() {
		return seqNo;
	}

	public int getAckNo() {
		return ackNo;
	}

	public int getFlags() {
		return flags;
	}

	public int getWindowSize() {
		return windowSize;
	}
	
	public boolean hasFlag(int flag) {
		return (this.flags & flag) == flag;
	}
	
}
