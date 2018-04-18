package client.FTPview;

public interface FTPView extends Runnable {
	
	public void run();
	public void showFilesOnServer(String[] words);
//	public Client getClient();
}
