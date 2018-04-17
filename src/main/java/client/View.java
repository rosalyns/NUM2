package client;

public interface View extends Runnable {
	
	public void run();
	public void showFilesOnServer(String[] words);
//	public Client getClient();
}
