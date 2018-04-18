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
//	private static String path = "home/pi/files/";
	private static String path = "/home/pi/files/"; // PIpath

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
//				Thread.sleep(100);

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
		}
		
		
		byte[] pkt = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
		int taskId = Header.twoBytes2int(pkt[0],pkt[1]);
		int checksum = Header.twoBytes2int(pkt[2],pkt[3]);
		int seqNo = Header.twoBytes2int(pkt[4],pkt[5]);
		int ackNo = Header.twoBytes2int(pkt[6],pkt[7]);
		byte flags = pkt[8];
//		int windowSize = Header.twoBytes2int(pkt[10],pkt[11]);
		
		byte[] checksumPkt = Arrays.copyOf(pkt, pkt.length);
		checksumPkt[2] = 0x00;
		checksumPkt[3] = 0x00;
		if (!Header.checksumCorrect(checksumPkt, checksum)) {
			if (Config.systemOuts) System.out.println("checksum not correct");
			return;
		} else {
			if (Config.systemOuts) System.out.println("checksum correct");
		}
		
		byte[] data = new byte[pkt.length - Config.FTP_HEADERSIZE];
		System.arraycopy(pkt, Config.FTP_HEADERSIZE, data, 0, pkt.length - Config.FTP_HEADERSIZE);
		
		
		if ((flags & Config.REQ_DOWN) == Config.REQ_DOWN) {
			int fileSize = Utils.getFileSize(path + new String(data));
			File file = new File(path + new String(data));
			Task t = new Task(Task.Type.SEND_FROM_SERVER, file, sock, packet.getAddress(), packet.getPort(), fileSize);
			t.setId(currentTaskId);
			tasks.put(t.getTaskId(), t);
			currentTaskId++;
			
			byte[] header = Header.ftp(t.getTaskId(), RANDOM_SEQ, seqNo, Config.ACK | Config.REQ_DOWN, 0xffffffff);//TODO think about seqNo?
			byte[] sizeHeader = Header.fileSize(fileSize);
			byte[] packetToSend = Utils.mergeArrays(header, sizeHeader);
			byte[] pktWithChecksum = Header.addChecksum(packetToSend, Header.crc16(packetToSend));
			this.sendPacket(pktWithChecksum, packet.getAddress(), packet.getPort());
			
			t.start();
			
		} else if ((flags & Config.REQ_UP) == Config.REQ_UP) {
			data = new byte[pkt.length - Config.FTP_HEADERSIZE - Config.FILESIZE_HEADERSIZE];
			System.arraycopy(pkt, Config.FTP_HEADERSIZE+Config.FILESIZE_HEADERSIZE, data, 0, pkt.length - Config.FTP_HEADERSIZE - Config.FILESIZE_HEADERSIZE);
			
			int fileSize = Header.fourBytes2int(pkt[Config.FTP_HEADERSIZE], pkt[Config.FTP_HEADERSIZE+1], pkt[Config.FTP_HEADERSIZE+2], pkt[Config.FTP_HEADERSIZE+3]);
			if (Config.systemOuts) System.out.println("File has size " + fileSize + " bytes!");
			//TODO check if enough space
			//TODO don't overwrite other files, check if name already exists
			
			File file = new File(path + new String(data));
			Task t = new Task(Task.Type.STORE_ON_SERVER, file, sock, packet.getAddress(), packet.getPort(), fileSize);
			t.setId(currentTaskId);
			tasks.put(t.getTaskId(), t);
			currentTaskId++;
			
			byte[] header = Header.ftp(t.getTaskId(), RANDOM_SEQ, seqNo, Config.ACK | Config.REQ_UP, 0xffffffff);
			byte[] pktWithChecksum = Header.addChecksum(header, Header.crc16(header));
			this.sendPacket(pktWithChecksum, packet.getAddress(), packet.getPort());
			
		} else if ((flags & Config.TRANSFER) == Config.TRANSFER && (flags & Config.ACK) == Config.ACK) {
			
			Task task = tasks.get(taskId);
			task.acked(ackNo);
			
		} else if ((flags & Config.TRANSFER) == Config.TRANSFER) {
			
			Task t = tasks.get(taskId);
			WritingThread.getInstance().addToQueue(new DataFragment(t, seqNo, data));
			
			byte[] header = Header.ftp(t.getTaskId(), RANDOM_SEQ, seqNo, Config.ACK | Config.TRANSFER, 0xffffffff);
			byte[] pktWithChecksum = Header.addChecksum(header, Header.crc16(header));
			this.sendPacket(pktWithChecksum, packet.getAddress(), packet.getPort());
			
		} else if ((flags & Config.STATS) == Config.STATS) {
		} else if ((flags & Config.LIST) == Config.LIST) {
			this.allFiles = folder.listFiles();
			byte[] header = Header.ftp(0, RANDOM_SEQ, seqNo, Config.ACK | Config.LIST, 0xffffffff);
			byte[] files = listFiles().getBytes();
			byte[] packetToSend = Utils.mergeArrays(header, files);
			byte[] pktWithChecksum = Header.addChecksum(packetToSend, Header.crc16(packetToSend));
			this.sendPacket(pktWithChecksum,  packet.getAddress(), packet.getPort());
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
	
	private void sendPacket(byte[] packet, InetAddress addr, int port) {
		try {
			sock.send(new DatagramPacket(packet, packet.length, addr, port));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
