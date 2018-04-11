package client;

import java.io.InputStream;
import java.util.List;
import java.util.Scanner;

public class TUI implements Runnable {

	/**
	 * State is used to decide what user input is allowed in what state of the menu.
	 * @author Rosalyn.Sleurink
	 *
	 */
	public enum State {
		INMENU, WANT_TO_UPLOAD, WANT_TO_DOWNLOAD, CHOOSE_DOWNLOAD, STATISTICS, PROGRESS
	}

	private State state;
	private Client client;
	private Scanner in;
	private boolean running = true;

	private String menuText = "\nMENU\n" 
			+ "1: Upload a file\n" 
			+ "2: Download a file\n" 
			+ "3: Show statistics\n" 
			+ "4: Show currently uploading/downloading\n"
			+ "5: Quit";
	
			
	public TUI(Client client, InputStream systemIn) {
		this.client = client;
		this.state = State.INMENU;
		this.in = new Scanner(systemIn);
	}

	@Override
	public void run() {
		showMenu();
		while (running) {
			String line = readString();
			String[] words = line.split(" ");
			
			switch(state) {
			case INMENU: 
				if (words.length == 1 && words[0].equalsIgnoreCase("1")) {
					state = State.WANT_TO_UPLOAD;
					print("Put the file you want to upload in the current folder and type the name of the file:");
				} else if (words.length == 1 && words[0].equalsIgnoreCase("2")) {
					state = State.WANT_TO_DOWNLOAD;
				} else if (words.length == 1 && words[0].equalsIgnoreCase("3")) {
					state = State.STATISTICS;
				} else if (words.length == 1 && words[0].equalsIgnoreCase("4")) {
					state = State.PROGRESS;
				} else if (words.length == 1 && words[0].equalsIgnoreCase("5")) {
					client.shutDown();
				} else {
					print("Enter a number from 1 to 5.");
				}
				break;
			case WANT_TO_UPLOAD:
				client.uploadFile(words[0]);
				break;
			case WANT_TO_DOWNLOAD:
				client.askForFiles();
				break;
			case CHOOSE_DOWNLOAD:
				
				break;
			case STATISTICS:
				client.askForStatistics();
				break;
			case PROGRESS:
				client.askForProgress();
				break;
			default: 
				print("default");
			}
			
		}
	}
	
	public void askForName() {
		print("The name you entered is already in use on the server. Enter a different name: ");
	}
	
	public void askForSettings() {
		print("A game is starting with you as the first player. Please choose a color and "
				+ "boardsize by entering: SETTINGS <color> <boardSize>. Possible colors are "
				+ "BLACK or WHITE, boardsize should be between 5 and 19.");
	}

	public void showFilesOnServer(List<String> files) {
		String filesStr = "Files:";
		boolean filesAdded = false;
		for (String fileName : files) {
			filesStr += " " + fileName;
			filesAdded = true;
		}
		if (filesAdded) {
			print("You can challenge one of the following players by typing REQUEST <playername>.\n"
					+ "Type REQUEST RANDOM if you don't want to challenge a specific player. ");
			print(filesStr);
		} else {
			print("There are currently no players you can challenge. You can type REQUEST RANDOM"
					+ " to be next in line to play a game.");
		}
	}

	public void showChatMessage(String playerName, String message) {
		print(playerName + ": " + message);
	}

	public void showChallengedBy(String playerName) {
		print("You have been challenged by " + playerName + ". ACCEPT or DECLINE?");
	}

	public void showChallengeDeclined(String playerName) {
		print(playerName + " declined your challenge.");
	}

	public void showPass(String playerName) {
		print(playerName + " passed.");
	}
	
	public void showInvalidMove() {
		print("This is not a valid move. Try again.");
	}
	
	public void showNotYourTurn() {
		print("Wait till it is your turn.");
	}

	public void showError(String type, String message) {
		//print("[Server] ERROR ");
		
	}

	public void showMenu() {
		print(menuText);
	}
	
	private String readString() {
		String result = null;
		if (in.hasNextLine()) {
			result = in.nextLine();
		}
		return result;
	}

	private static void print(String message) {
		System.out.println(message);
	}

	public void shutdown() {
		print("Closing connection to server.");
	}

}
