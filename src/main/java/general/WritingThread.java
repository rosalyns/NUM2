package general;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import server.DataFragment;

public class WritingThread implements Runnable {
	private Queue<DataFragment> queue;
	
	private WritingThread() {
		this.queue = new ConcurrentLinkedQueue<>();
	}

	@Override
	public void run() {
		
		while(true) {
			DataFragment tuple = queue.poll();
			if (tuple != null) {
				tuple.getTask().addContent(tuple.getSeqNo(), tuple.getData());
			} else {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
	}
	
	public void addToQueue(DataFragment data) {
		queue.add(data);
	}
	
	public static WritingThread getInstance() {
		return WritingThreadHolder.INSTANCE;
	}
	
	private static class WritingThreadHolder {
		private static final WritingThread INSTANCE = new WritingThread();
	}
	
	

}
