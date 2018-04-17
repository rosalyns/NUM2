package client.progressview;

public interface View extends Runnable {
	
	public void run();
	public void updateProgress(int percentage);
}
