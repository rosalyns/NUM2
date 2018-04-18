package server;

import general.Task;

public class DataFragment {
	private Task task;
	private int seqNo;
	private byte[] data;
	
	public DataFragment(Task task, int seqNo, byte[] data) {
		this.task = task;
		this.seqNo = seqNo;
		this.data = data;
	}
	
	public Task getTask() {
		return this.task;
	}
	public int getSeqNo() {
		return this.seqNo;
	}
	
	public byte[] getData() {
		return this.data;
	}

}
