package server;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import general.*;

public class Server {

	// --------------- MAIN METHOD ---------------- //
	private static int serverPort = 8002;
	private static String path = "home/pi/files/"; // computerPath
//	private static String path = "/home/pi/files/"; // PIpath

	public static void main(String[] args) {
		try {
			Server server = new Server(serverPort);
			server.run();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// --------------- CLASS METHODS ---------------- //

	private int port;
	private DatagramSocket sock;
	private Map<Integer, Task> tasks;
	private int currentTaskId = 1;
	private File folder;
	private File[] allFiles;

	private static boolean keepAlive = true;
	private static int RANDOM_SEQ = 25;

	public Server(int portArg) {
		this.folder = new File(path);
		
		this.port = portArg;
		this.tasks = new HashMap<Integer, Task>();
		Thread discoveryThread = new Thread(DiscoveryThread.getInstance());
		discoveryThread.start();
		
		Utils.Timeout.Start();
	}

	public void run() throws IOException {
		System.out.println("[Server] Opening a socket on port " + port);
		sock = new DatagramSocket(port);

		while (keepAlive) {
			DatagramPacket p = getEmptyPacket();
			try {
				sock.receive(p);
				handlePacket(convertPacket(p));

			} catch (IOException e) {
				keepAlive = false;
			}
		}  
		
		Utils.Timeout.Stop();
		System.out.println("Stopped");
	}
	
	
	private void handlePacket(Packet packet) {
		String packetString = new String(packet.getData()).trim();
		if (packetString.equals("DISCOVER_REQUEST")) {
			byte[] sndData = "DISCOVER_RESPONSE Hello, I'm a raspberry Pi!".getBytes();
			Packet sndPkt = new Packet(sndData);
			this.sendPacket(sndPkt, packet.getAddress(), packet.getPort());

			System.out.println(
					getClass().getName() + ">>>Sent packet to: " + packet.getAddress().getHostAddress());
			return;
		}
		
		FTPHeader ftpHeader = packet.getFtpHeader();
		
		if (ftpHeader.hasFlag(Flag.REQ_DOWN)) {
			handleDownloadRequest(packet);
		} else if (ftpHeader.hasFlag(Flag.REQ_UP)) {
			handleUploadRequest(packet);
		} else if (ftpHeader.hasFlag(Flag.TRANSFER) && ftpHeader.hasFlag(Flag.ACK)) {
			handleAck(ftpHeader);
		} else if (ftpHeader.hasFlag(Flag.TRANSFER)) {
			handleData(packet);
		} else if (ftpHeader.hasFlag(Flag.STATS)) {
			handleStatsRequest(packet);
		} else if (ftpHeader.hasFlag(Flag.LIST)) {
			handleListRequest(packet);
		} else if (ftpHeader.hasFlag(Flag.PAUSE)) {
			handlePauseRequest(packet);
		}
	}
	
	private void handleStatsRequest(Packet packet) {
		//TODO implement stats
	}

	private void handleListRequest(Packet packet) {
		FTPHeader ftpHeader = packet.getFtpHeader();
		this.allFiles = folder.listFiles();
		
		FTPHeader ftp = new FTPHeader(0, RANDOM_SEQ, ftpHeader.getSeqNo(), Flag.ACK.getValue() | Flag.LIST.getValue(), 0xffffffff);
		byte[] sndData = listFiles().getBytes();
		Packet sndPkt = new Packet(ftp, sndData);
		this.sendPacket(sndPkt, packet.getAddress(), packet.getPort());
	}
	
	private void handlePauseRequest(Packet packet) {
		//TODO implement pause
	}

	private void handleData(Packet packet) {
		FTPHeader ftpHeader = packet.getFtpHeader();

		StoreTask t = (StoreTask) tasks.get(ftpHeader.getTaskId());
		t.addToQueue(new DataFragment(ftpHeader.getSeqNo(), packet.getData()));
		
		Packet sndPkt = new Packet(new FTPHeader(t.getId(), RANDOM_SEQ, ftpHeader.getSeqNo(), Flag.ACK.getValue() | Flag.TRANSFER.getValue(), 0xffffffff));
		this.sendPacket(sndPkt, packet.getAddress(), packet.getPort());
	}

	private void handleAck(FTPHeader ftpHeader) {
		SendTask task = (SendTask) tasks.get(ftpHeader.getTaskId());
		task.acked(ftpHeader.getAckNo());
	}

	private void handleUploadRequest(Packet packet) {
		FTPHeader ftpHeader = packet.getFtpHeader();

		byte[] data = new byte[packet.getData().length - Config.FILESIZE_HEADERSIZE];
		System.arraycopy(packet.getData(), Config.FILESIZE_HEADERSIZE, data, 0, packet.getData().length - Config.FILESIZE_HEADERSIZE);
		
		int fileSize = Header.bytes2int(packet.getData()[0], packet.getData()[1], packet.getData()[2], packet.getData()[3]);
		if (Config.systemOuts) System.out.println("File has size " + fileSize + " bytes!");
		//TODO check if enough space
		//TODO don't overwrite other files, check if name already exists
		
		File file = new File(path + new String(data));
		StoreTask t = new StoreTask(file, sock, packet.getAddress(), packet.getPort(), fileSize);
		t.setId(currentTaskId);
		tasks.put(t.getId(), t);
		currentTaskId++;
		
		Packet sndPkt = new Packet(new FTPHeader(t.getId(), RANDOM_SEQ, ftpHeader.getSeqNo(), Flag.ACK.getValue() | Flag.REQ_UP.getValue(), 0xffffffff));
		this.sendPacket(sndPkt, packet.getAddress(), packet.getPort());
		
		Thread taskThread = new Thread(t);
		taskThread.start();
	}

	private void handleDownloadRequest(Packet packet) {
		FTPHeader ftpHeader = packet.getFtpHeader();

		int fileSize = Utils.getFileSize(path + new String(packet.getData()));
		File file = new File(path + new String(packet.getData()));
		SendTask t = new SendTask(file, sock, packet.getAddress(), packet.getPort(), fileSize);
		t.setId(currentTaskId);
		tasks.put(t.getId(), t);
		currentTaskId++;
		
		byte[] sndSizeHeader = Header.fileSizeInBytes(fileSize);
		
		FTPHeader ftp = new FTPHeader(t.getId(), RANDOM_SEQ, ftpHeader.getSeqNo(), Flag.ACK.getValue() | Flag.REQ_DOWN.getValue(), 0xffffffff);
		Packet sndPkt = new Packet(ftp, sndSizeHeader);
		this.sendPacket(sndPkt, packet.getAddress(), packet.getPort());
		
		Thread taskThread = new Thread(t);
		taskThread.start();
	}
	
	/**
	 * Dissects packet and returns a byte array with the header and the data separated.
	 * @param pkt to be dissected
	 * @return byte array of size 2 with [byte[] header, byte[] data]
	 */
	private Packet convertPacket(DatagramPacket pkt) {
		byte[] rcvPkt = Arrays.copyOfRange(pkt.getData(), 0, pkt.getLength());
		byte[] ftpBytes = Arrays.copyOfRange(rcvPkt, 0, Config.FTP_HEADERSIZE);
		Packet result = new Packet(Header.bytesToFTP(ftpBytes), Arrays.copyOfRange(rcvPkt, Config.FTP_HEADERSIZE, rcvPkt.length));
		result.setAddress(pkt.getAddress());
		result.setPort(pkt.getPort());
		return result;
	}
	
	private String listFiles() {
		String result = "";
		for (File file : allFiles) {
			result += " " + file.getName();
		}
		return result;
	}

	private DatagramPacket getEmptyPacket() {
		byte[] data = new byte[Config.FTP_HEADERSIZE + Config.DATASIZE];
		return new DatagramPacket(data, data.length);
	}
	
	private void sendPacket(Packet packet, InetAddress addr, int port) {
		try {
			sock.send(new DatagramPacket(packet.getData(), packet.getDataLength(), addr, port));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
