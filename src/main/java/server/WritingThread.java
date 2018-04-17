package server;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class WritingThread implements Runnable {
	private Queue<DataTuple> queue;
	
	private WritingThread() {
		this.queue = new ConcurrentLinkedQueue<>();
	}

	@Override
	public void run() {
		
		while(true) {
			DataTuple tuple = queue.poll();
			if (tuple != null) {
				tuple.getTask().addContent(tuple.getSeqNo(), tuple.getData());
			} else {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
	}
	
	public void addToQueue(DataTuple data) {
		queue.add(data);
	}
	
	public static WritingThread getInstance() {
		return WritingThreadHolder.INSTANCE;
	}
	
	private static class WritingThreadHolder {
		private static final WritingThread INSTANCE = new WritingThread();
	}
	
	

}
