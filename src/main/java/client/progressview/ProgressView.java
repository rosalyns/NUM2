package client.progressview;

public interface ProgressView extends Runnable {
	
	public void run();
	public void updateProgress(int percentage);
}
