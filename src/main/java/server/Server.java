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
		Thread writingThread = new Thread(WritingThread.getInstance());
		discoveryThread.start();
		writingThread.start();
		
		Utils.Timeout.Start();
	}

	public void run() throws IOException {
		System.out.println("[Server] Opening a socket on port " + port);
		sock = new DatagramSocket(port);

		while (keepAlive) {
			DatagramPacket p = getEmptyPacket();
			try {
				sock.receive(p);
				handlePacket(p);

			} catch (IOException e) {
				keepAlive = false;
			}
		}  
		
		Utils.Timeout.Stop();
		System.out.println("Stopped");
	}
	
	
	private void handlePacket(DatagramPacket packet) {
		String packetString = new String(packet.getData()).trim();
		if (packetString.equals("DISCOVER_REQUEST")) {
			byte[] sendData = "DISCOVER_RESPONSE Hello, I'm a raspberry Pi!".getBytes();
			this.sendPacket(sendData, packet.getAddress(), packet.getPort());

			System.out.println(
					getClass().getName() + ">>>Sent packet to: " + packet.getAddress().getHostAddress());
			return;
		}
		
		byte[] rcvPkt = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
		byte[] rcvHeader = Arrays.copyOfRange(packet.getData(), 0, Config.FTP_HEADERSIZE);
		byte[] rcvData = new byte[rcvPkt.length - Config.FTP_HEADERSIZE];
		System.arraycopy(rcvPkt, Config.FTP_HEADERSIZE, rcvData, 0, rcvPkt.length - Config.FTP_HEADERSIZE);
		FTPHeader ftp = Header.dissectFTPBytes(rcvHeader);
		
		if (hasFlag(ftp.getFlags(), Config.REQ_DOWN)) {
			int fileSize = Utils.getFileSize(path + new String(rcvData));
			File file = new File(path + new String(rcvData));
			Task t = new Task(Task.Type.SEND_FROM_SERVER, file, sock, packet.getAddress(), packet.getPort(), fileSize);
			t.setId(currentTaskId);
			tasks.put(t.getTaskId(), t);
			currentTaskId++;
			
			byte[] sndHeader = Header.ftp(new FTPHeader(t.getTaskId(), RANDOM_SEQ, ftp.getSeqNo(), Config.ACK | Config.REQ_DOWN, 0xffffffff));//TODO think about seqNo?
			byte[] sndSizeHeader = Header.fileSize(fileSize);
			byte[] sndPkt = Utils.mergeArrays(sndHeader, sndSizeHeader);
			this.sendPacket(sndPkt, packet.getAddress(), packet.getPort());
			
			Thread taskThread = new Thread(t);
			taskThread.start();
			
		} else if (hasFlag(ftp.getFlags(), Config.REQ_UP)) {
			rcvData = new byte[rcvPkt.length - Config.FTP_HEADERSIZE - Config.FILESIZE_HEADERSIZE];
			System.arraycopy(rcvPkt, Config.FTP_HEADERSIZE+Config.FILESIZE_HEADERSIZE, rcvData, 0, rcvPkt.length - Config.FTP_HEADERSIZE - Config.FILESIZE_HEADERSIZE);
			
			int fileSize = Header.fourBytes2int(rcvPkt[Config.FTP_HEADERSIZE], rcvPkt[Config.FTP_HEADERSIZE+1], rcvPkt[Config.FTP_HEADERSIZE+2], rcvPkt[Config.FTP_HEADERSIZE+3]);
			if (Config.systemOuts) System.out.println("File has size " + fileSize + " bytes!");
			//TODO check if enough space
			//TODO don't overwrite other files, check if name already exists
			
			File file = new File(path + new String(rcvData));
			Task t = new Task(Task.Type.STORE_ON_SERVER, file, sock, packet.getAddress(), packet.getPort(), fileSize);
			t.setId(currentTaskId);
			tasks.put(t.getTaskId(), t);
			currentTaskId++;
			
			byte[] sndHeader = Header.ftp(new FTPHeader(t.getTaskId(), RANDOM_SEQ, ftp.getSeqNo(), Config.ACK | Config.REQ_UP, 0xffffffff));
			this.sendPacket(sndHeader, packet.getAddress(), packet.getPort());
			
		} else if (hasFlag(ftp.getFlags(), Config.TRANSFER) && hasFlag(ftp.getFlags(), Config.ACK)) {
			
			Task task = tasks.get(ftp.getTaskId());
			task.acked(ftp.getAckNo());
			
		} else if (hasFlag(ftp.getFlags(), Config.TRANSFER)) {
			
			Task t = tasks.get(ftp.getTaskId());
			WritingThread.getInstance().addToQueue(new DataFragment(t, ftp.getSeqNo(), rcvData));
			
			byte[] sndHeader = Header.ftp(new FTPHeader(t.getTaskId(), RANDOM_SEQ, ftp.getSeqNo(), Config.ACK | Config.TRANSFER, 0xffffffff));
			this.sendPacket(sndHeader, packet.getAddress(), packet.getPort());
			
		} else if (hasFlag(ftp.getFlags(), Config.STATS)) {
			//TODO
		} else if (hasFlag(ftp.getFlags(), Config.LIST)) {
			this.allFiles = folder.listFiles();
			byte[] sndHeader = Header.ftp(new FTPHeader(0, RANDOM_SEQ, ftp.getSeqNo(), Config.ACK | Config.LIST, 0xffffffff));
			byte[] sndData = listFiles().getBytes();
			byte[] sndPkt = Utils.mergeArrays(sndHeader, sndData);
			this.sendPacket(sndPkt,  packet.getAddress(), packet.getPort());
		} else if (hasFlag(ftp.getFlags(), Config.PAUSE)) {
			//TODO
		}
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
	
	private boolean hasFlag(int allFlags, int flag) {
		return (allFlags & flag) == flag;
	}
	
	private void sendPacket(byte[] packet, InetAddress addr, int port) {
		try {
			sock.send(new DatagramPacket(packet, packet.length, addr, port));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
