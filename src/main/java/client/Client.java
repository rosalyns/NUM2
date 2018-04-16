package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;
import java.util.Queue;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

import general.*;

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
	private static int RANDOM_SEQ = 3; //TODO not random?
	
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
		Utils.Timeout.Start();
		receive();
	}

	
	public void receive() {
		while (keepAlive) {
			DatagramPacket p = getEmptyPacket();
			try {
//				System.out.println("[Client] Waiting for packets...");
				sock.receive(p);
				handlePacket(p);
				Thread.sleep(100);

			} catch (IOException | InterruptedException e) {
				// Thread.currentThread().interrupt();
				keepAlive = false;
			}
		}
		Utils.Timeout.Stop();
		System.out.println("Stopped");
		
	}
	
	private void handlePacket(DatagramPacket packet) {
		byte[] pkt = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
		int taskId = Header.twoBytes2int(pkt[0],pkt[1]);
		int checksum = Header.twoBytes2int(pkt[2],pkt[3]);
		int seqNo = Header.twoBytes2int(pkt[4],pkt[5]);
		int ackNo = Header.twoBytes2int(pkt[6],pkt[7]);
		byte flags = pkt[8];
		int windowSize = Header.twoBytes2int(pkt[10],pkt[11]);
		
		byte[] checksumPkt = Arrays.copyOf(pkt, pkt.length);
		checksumPkt[2] = 0x00;
		checksumPkt[3] = 0x00;   
		if (!Header.checksumCorrect(checksumPkt, checksum)) {
			System.out.println("checksum not correct");
//			return;
		} else {
			System.out.println("checksum correct");
		}
		
		byte[] data;
		if ((flags & Config.REQ_DOWN) == Config.REQ_DOWN && (flags & Config.ACK) == Config.ACK) {
			data = new byte[pkt.length - Config.FTP_HEADERSIZE - Config.FILESIZE_HEADERSIZE];
			System.arraycopy(pkt, Config.FTP_HEADERSIZE+Config.FILESIZE_HEADERSIZE, data, 0, pkt.length - Config.FTP_HEADERSIZE - Config.FILESIZE_HEADERSIZE);
		} else {
			data = new byte[pkt.length - Config.FTP_HEADERSIZE];
			System.arraycopy(pkt, Config.FTP_HEADERSIZE, data, 0, pkt.length - Config.FTP_HEADERSIZE);
		}
		
//		System.out.println("[Client] Packet received from " + packet.getSocketAddress() 
//		+ " :\ntaskID: "  + taskId 
//		+ "\nchecksum: " + checksum 
//		+ "\nseqNo: " + seqNo 
//		+ "\nackNo: " + ackNo 
//		+ "\nflags: " + Integer.toBinaryString(flags) 
//		+ "\nwindowSize: " + windowSize);
		
		if ((flags & Config.REQ_DOWN) == Config.REQ_DOWN && (flags & Config.ACK) == Config.ACK) {
			int fileSize = Header.fourBytes2int(pkt[Config.FTP_HEADERSIZE],pkt[Config.FTP_HEADERSIZE + 1],pkt[Config.FTP_HEADERSIZE+2],pkt[Config.FTP_HEADERSIZE+3]);
			System.out.println("filesize is " + fileSize);
			if (!requestedDowns.isEmpty()) {
				Task t = requestedDowns.poll();
				t.setFileSize(fileSize);
				t.setId(taskId);
				tasks.put(t.getTaskId(), t);
			}
			System.out.println("REQ_DOWN + ACK");
		} else if ((flags & Config.REQ_UP) == Config.REQ_UP && (flags & Config.ACK) == Config.ACK) {
			if (!requestedUps.isEmpty()) {
				Task t = requestedUps.poll();
				t.setId(taskId);
				tasks.put(t.getTaskId(), t);
				t.start();
			}
		} else if ((flags & Config.TRANSFER) == Config.TRANSFER && (flags & Config.ACK) == Config.ACK) {
			Task task = tasks.get(taskId);
			task.acked(ackNo);
		} else if ((flags & Config.TRANSFER) == Config.TRANSFER) {
			Task t = tasks.get(taskId);
			t.addContent(seqNo, data);
			
			byte[] header = Header.ftp(taskId, 3, seqNo, Config.ACK | Config.TRANSFER, 0xffffffff);//TODO send correct ackNo (% K)
			byte[] pktWithChecksum = Header.addChecksum(header, Header.crc16(header));
			this.sendPacket(pktWithChecksum);
			
		} else if ((flags & Config.STATS) == Config.STATS) {
			System.out.println("Packet has STATS flag set"); 
		} else if ((flags & Config.LIST) == Config.LIST && (flags & Config.ACK) == Config.ACK) {
			String files = new String(data);
			tui.showFilesOnServer(files.split(" "));
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
		byte[] header = Header.ftp(0, RANDOM_SEQ, 0, Config.LIST, 0xffffffff);
		byte[] pktWithChecksum = Header.addChecksum(header, Header.crc16(header));
		sendPacket(pktWithChecksum);
	}
	
	public void askForStatistics() {
		byte[] header = Header.ftp(0, RANDOM_SEQ, 0, Config.STATS, 0xffffffff);
		byte[] pktWithChecksum = Header.addChecksum(header, Header.crc16(header));
		sendPacket(pktWithChecksum);
	}
	
	public void askForProgress() {
		System.out.println("asking for progress..");
	}
	
	public void uploadFile(String fileName) {
		int fileSize = Utils.getFileSize("downloads/" + fileName);
		
		byte[] header = Header.ftp(0, RANDOM_SEQ, 0,Config.REQ_UP, 0xffffffff);
		byte[] sizeHeader = Header.fileSize(fileSize);
		byte[] pkt = Utils.mergeArrays(header, sizeHeader, fileName.getBytes());
		byte[] pktWithChecksum = Header.addChecksum(pkt, Header.crc16(pkt));
		System.out.println("Sending packet with seq_no " + RANDOM_SEQ + " and fileSize " + fileSize);
		
		Task t = new Task(Task.Type.SEND_FROM_CLIENT, "downloads/"+fileName, sock, host, port, fileSize);
		requestedUps.add(t);
		
		sendPacket(pktWithChecksum);
	}
	
	public void downloadFile(String fileName) {
		byte[] header = Header.ftp(0, RANDOM_SEQ, 0, Config.REQ_DOWN,	0xffffffff);
		byte[] pkt = Utils.mergeArrays(header, fileName.getBytes());
		byte[] pktWithChecksum = Header.addChecksum(pkt, Header.crc16(pkt));
		Task t = new Task(Task.Type.STORE_ON_CLIENT, "downloads/"+fileName, sock, host, port, 200000000); //TODO think about fileSize (last param)
		requestedDowns.add(t);
		
		sendPacket(pktWithChecksum);
	}
	
	private DatagramPacket getEmptyPacket() {
		byte[] data = new byte[Config.FTP_HEADERSIZE + Config.DATASIZE];
		return new DatagramPacket(data, data.length);
	}
	
	private void sendPacket(byte[] packet) {
		try {
			sock.send(new DatagramPacket(packet, packet.length, host, port));
		} catch (IOException e) {
			e.printStackTrace();
		}
		Utils.Timeout.SetTimeout(1000, this, packet);
	}

	@Override
	public void TimeoutElapsed(Object tag) {
		byte[] pkt = (byte[]) tag;
		byte flags = pkt[8];
		
		if ((flags & Config.REQ_UP) == Config.REQ_UP && !requestedUps.isEmpty()) {
			sendPacket((byte[]) tag);
		} else if ((flags & Config.REQ_DOWN) == Config.REQ_DOWN && !requestedDowns.isEmpty()) {
			sendPacket((byte[]) tag);
		}
	}
	
	public void shutDown() {
		simulationFinished = true;
	}

}
