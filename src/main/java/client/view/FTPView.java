package client.view;

public interface FTPView extends Runnable {
	
	public void run();
	public void showFilesOnServer(String[] words);
//	public Client getClient();
}
