package client.progressview;

import java.util.Observer;

public interface ProgressView extends Runnable, Observer {
	
	public void run();
}
