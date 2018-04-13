package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;
import java.util.Queue;
import java.util.HashMap;
import java.util.LinkedList;

import general.Task;
import general.Config;
import general.Header;

public class Client implements ITimeoutEventHandler {

	// server address
	private InetAddress host;

	// server port
	private int port;
	private DatagramSocket sock;
	private TUI tui;
	private Map<Integer, Task> tasks;
	private Queue<Task> requestedUps;
	private Queue<Task> requestedDowns;

	// whether the simulation is finished
	private boolean simulationFinished = false;
	private static boolean keepAlive = true;
	
	public Client(InetAddress serverAddress, int serverPort) throws IOException {
		this.host = serverAddress;
		this.port = serverPort;
		this.tasks = new HashMap<Integer, Task>();
		this.requestedUps = new LinkedList<>();
		this.requestedDowns = new LinkedList<>();
		this.sock = new DatagramSocket(52123);
		this.tui = new TUI(this, System.in);
		Thread tuiThread = new Thread(tui);
		tuiThread.start();
		receive();
	}

	
	public void receive() {
		while (keepAlive) {
			DatagramPacket p = getEmptyPacket();
			try {
				System.out.println("[Client] Waiting for packets...");
				sock.receive(p);
				handlePacket(p);
				Thread.sleep(100);

			} catch (IOException | InterruptedException e) {
				// Thread.currentThread().interrupt();
				keepAlive = false;
			}
		}

		System.out.println("Stopped");
	}
	
	private void handlePacket(DatagramPacket packet) {
		byte[] pkt = packet.getData();
		int taskId = Header.twoBytes2int(pkt[0],pkt[1]);
		int checksum = Header.twoBytes2int(pkt[2],pkt[3]);
		int seqNo = Header.twoBytes2int(pkt[4],pkt[5]);
		int ackNo = Header.twoBytes2int(pkt[6],pkt[7]);
		byte flags = pkt[8];
		int windowSize = Header.twoBytes2int(pkt[10],pkt[11]);
		
		System.out.println("[Server] Packet received from " + packet.getSocketAddress() 
		+ " :\ntaskID: "  + taskId 
		+ "\nchecksum: " + checksum 
		+ "\nseqNo: " + seqNo 
		+ "\nackNo: " + ackNo 
		+ "\nflags: " + Integer.toBinaryString(flags) 
		+ "\nwindowSize: " + windowSize);
		
		byte[] data = new byte[pkt.length - Config.HEADERSIZE];
		System.arraycopy(pkt, Config.HEADERSIZE, data, 0, pkt.length - Config.HEADERSIZE);
		
		if ((flags & Config.REQ_DOWN) == Config.REQ_DOWN && (flags & Config.ACK) == Config.ACK) {
			if (!requestedDowns.isEmpty()) {
				Task task = requestedDowns.poll();
				tasks.put(taskId, task);
			}
			System.out.println("REQ_DOWN + ACK");
		} else if ((flags & Config.REQ_UP) == Config.REQ_UP && (flags & Config.ACK) == Config.ACK) {
			if (!requestedUps.isEmpty()) {
				Task t = requestedUps.poll();
				t.setId(taskId);
				tasks.put(t.getTaskId(), t);
				t.start();
			}
			System.out.println("REQ_UP + ACK");
		} else if ((flags & Config.UP) == Config.UP && (flags & Config.ACK) == Config.ACK) {
			Task task = tasks.get(taskId);
			task.acked(ackNo);
			System.out.println("UP + ACK");
		} else if ((flags & Config.DOWN) == Config.DOWN) {
			//TODO store file contents
			
			byte[] header = Header.ftp(taskId, 3, seqNo + 1, Config.ACK | Config.DOWN, 0xffffffff);//TODO send correct ackNo (% K)
			this.sendPacket(header);
			System.out.println("DOWN");
		} else if ((flags & Config.STATS) == Config.STATS) {
			System.out.println("Packet has STATS flag set"); 
		}
	}
	
	/**
	 * @return whether the simulation has finished
	 */
	public boolean isFinished() {
		return simulationFinished;
	}
	// ----------- methods for TUI -------------//
	
	
	public void askForFiles() {
		System.out.println("asking for files..");
	}
	
	public void askForStatistics() {
		System.out.println("asking for statistics..");
	}
	
	public void askForProgress() {
		System.out.println("asking for progress..");
	}
	
	public void uploadFile(String fileName) {
		int sequenceNumber = 3; //TODO think about seqNo
		int fileSize = Utils.getFileSize(fileName);
		
		byte[] header = Header.ftp(0,sequenceNumber, 0,Config.REQ_UP, 0xffffffff);
		byte[] upHeader = Header.upload(fileSize);
		byte[] pkt = Utils.mergeArrays(header, upHeader, fileName.getBytes());
		System.out.println("Sending packet with seq_no " + sequenceNumber + " and fileSize " + fileSize);
		
		Task newTask = new Task(Task.Type.SEND_FILE, fileName, sock, host, port, fileSize);
		requestedUps.add(newTask);
		
		sendPacket(pkt);
		sequenceNumber = (sequenceNumber + 1) % Config.K;
	}
	
	private DatagramPacket getEmptyPacket() {
		byte[] data = new byte[Config.HEADERSIZE + Config.DATASIZE];
		return new DatagramPacket(data, data.length);
	}
	
	private void sendPacket(byte[] packet) {
		try {
			sock.send(new DatagramPacket(packet, packet.length, host, port));
		} catch (IOException e) {
			e.printStackTrace();
		}
		client.Utils.Timeout.SetTimeout(1000, this, packet);
	}

	@Override
	public void TimeoutElapsed(Object tag) {
//		int numberPacketSent = ((byte[]) tag)[0];
//		if (inSendingWindow(numberPacketSent) && !receivedAck(numberPacketSent)) { TODO check if request is still open.
			sendPacket((byte[]) tag);
//		}
	}
	
	public void shutDown() {
		simulationFinished = true;
	}

}
