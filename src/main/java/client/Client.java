package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.ArrayList;
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
		int taskId = Header.fourBytes2dec(pkt[0],pkt[1],pkt[2],pkt[3]);
		int checksum = Header.fourBytes2dec(pkt[4],pkt[5],pkt[6],pkt[7]);
		int seqNo = Header.fourBytes2dec(pkt[8],pkt[9],pkt[10],pkt[11]);
		int ackNo = Header.fourBytes2dec(pkt[12],pkt[13],pkt[14],pkt[15]);
		byte flags = pkt[16];
		int windowSize = Header.fourBytes2dec(pkt[20],pkt[21],pkt[22],pkt[23]);
		
		System.out.println("[Server] Packet received from " + packet.getSocketAddress() + " with sequence number " + seqNo);
		
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
				Task task = requestedUps.poll();
				tasks.put(taskId, task);
				task.start();
			}
			System.out.println("REQ_UP + ACK");
		} else if ((flags & Config.UP) == Config.UP && (flags & Config.ACK) == Config.ACK) {
			System.out.println("Packet has UP flag set");
			Task task = tasks.get(taskId);
			task.acked(ackNo);
			System.out.println("UP + ACK");
		} else if ((flags & Config.DOWN) == Config.DOWN) {
			System.out.println("Packet has DOWN flag set");
			//TODO store file contents
			
			sendAck(packet.getSocketAddress(), taskId, seqNo + data.length); //TODO send correct ackNo (% K)
			System.out.println("DOWN");
		} else if ((flags & Config.STATS) == Config.STATS) {
			System.out.println("Packet has STATS flag set");
		}
	}
	
	private void sendAck(SocketAddress addr, int taskId, int ackNo) {
		//TODO upload or download flag
		System.out.println("Sending ACK " + ackNo + " to " + addr);
		byte[] header = Header.ftp(taskId, 0, ackNo, Config.ACK, 0xffffffff);
		try {
			sock.send(new DatagramPacket(header, header.length, addr));
		} catch (IOException e) {
			e.printStackTrace();
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
		byte[] pkt = new byte[Config.HEADERSIZE + fileName.length()];
		byte[] header = Header.ftp(0,sequenceNumber, 0,Config.REQ_UP, 0xffffffff);
		System.out.println("Sending packet with seq_no " + sequenceNumber);
		System.arraycopy(header, 0, pkt, 0, Config.HEADERSIZE);
		System.arraycopy(fileName.getBytes(), 0, pkt, Config.HEADERSIZE, fileName.length());
		sequenceNumber = (sequenceNumber + 1) % Config.K;
		sendPacket(pkt);
		
		Task newTask = new Task(Task.Type.UPLOAD, fileName, sock, host, port);
		requestedUps.add(newTask);
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
		int numberPacketSent = ((byte[]) tag)[0];
//		if (inSendingWindow(numberPacketSent) && !receivedAck(numberPacketSent)) { TODO check if request is still open.
			sendPacket((byte[]) tag);
//		}
	}
	
	public void shutDown() {
		simulationFinished = true;
	}

}
