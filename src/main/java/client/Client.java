package client;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Map;
import java.util.Queue;

import client.progressview.GUI;
import client.view.FTPGUI;
import client.view.FTPView;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;

import general.*;

public class Client implements ITimeoutEventHandler {

	// ---------- START CLIENT ------------ //
	
	private static int serverPort = 8002;
	
	public static void main(String[] args) {
        try {
			Client client = new Client(serverPort);
			client.run();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
	
	// server address
	private InetAddress serverIp;
	private int port;
	private DatagramSocket sock;
	private FTPView view;
	private Map<Integer, Task> tasks;
	private Queue<Task> requestedUps;
	private Queue<Task> requestedDowns;

	// whether the simulation is finished
	private boolean simulationFinished = false;
	private static boolean keepAlive = true;
	private static int RANDOM_SEQ = 3; //TODO not random?
	
	public Client(int serverPort) throws IOException {
		this.port = serverPort;
		this.tasks = new HashMap<Integer, Task>();
		this.requestedUps = new LinkedList<>();
		this.requestedDowns = new LinkedList<>();
		this.sock = new DatagramSocket(52123);
		this.sock.setBroadcast(true);
		this.view = new FTPGUI(this);
	}
	
	public void run() {
		Utils.Timeout.Start();
		discover();
		Thread viewThread = new Thread(view);
		viewThread.start();
		receive();
	}
	
	public void setServerIp(InetAddress addr) {
		this.serverIp = addr;
	}
	
	DatagramSocket c;

	public void discover() {
		try {
			// Open a random port to send the package
			c = new DatagramSocket();
			c.setBroadcast(true);

			byte[] sendData = "DISCOVER_REQUEST".getBytes();

			// Try the 255.255.255.255 first
			try {
				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
						InetAddress.getByName("255.255.255.255"), 8888);
				c.send(sendPacket);
				System.out.println(getClass().getName() + ">>> Request packet sent to: 255.255.255.255 (DEFAULT)");
			} catch (Exception e) {
			}

			// Broadcast the message over all the network interfaces
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				NetworkInterface networkInterface = interfaces.nextElement();

				if (networkInterface.isLoopback() || !networkInterface.isUp()) {
					continue; // Don't want to broadcast to the loopback interface
				}

				for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
					InetAddress broadcast = interfaceAddress.getBroadcast();
					if (broadcast == null) {
						continue;
					}

					// Send the broadcast package!
					try {
						DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, broadcast, 8888);
						c.send(sendPacket);
					} catch (Exception e) {
					}

					System.out.println(getClass().getName() + ">>> Request packet sent to: "
							+ broadcast.getHostAddress() + "; Interface: " + networkInterface.getDisplayName());
				}
			}

			System.out.println(
					getClass().getName() + ">>> Done looping over all network interfaces. Now waiting for a reply!");

			// Wait for a response
			byte[] recvBuf = new byte[15000];
			DatagramPacket receivePacket = new DatagramPacket(recvBuf, recvBuf.length);
			c.receive(receivePacket);

			// We have a response
			System.out.println(getClass().getName() + ">>> Broadcast response from server: "
					+ receivePacket.getAddress().getHostAddress());

			// Check if the message is correct
			String message = new String(receivePacket.getData()).trim();

			if (message.contains("DISCOVER_RESPONSE") && (message.toLowerCase().contains("raspberry pi")
					|| message.toLowerCase().contains("raspberrypi"))) {
				setServerIp(receivePacket.getAddress());
				System.out.println("discovered the pi!");
			}

			// Close the port!
			c.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void receive() {
		while (keepAlive) {
			DatagramPacket p = getEmptyPacket();
			try {
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
			if (Config.systemOuts) System.out.println("checksum not correct");
//			return;
		} else {
			if (Config.systemOuts) System.out.println("checksum correct");
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
			if (Config.systemOuts) System.out.println("filesize is " + fileSize);
			if (!requestedDowns.isEmpty()) {
				Task t = requestedDowns.poll();
				t.setFileSize(fileSize);
				t.setId(taskId);
				tasks.put(t.getTaskId(), t);
				
				GUI progressBar = new GUI("Downloading "+ t.getName());
				Thread guiThread = new Thread(progressBar);
				guiThread.start();
				t.setGUI(progressBar);
			}
			
		} else if ((flags & Config.REQ_UP) == Config.REQ_UP && (flags & Config.ACK) == Config.ACK) {
			if (!requestedUps.isEmpty()) {
				Task t = requestedUps.poll();
				t.setId(taskId);
				tasks.put(t.getTaskId(), t);
				t.start();
				
				GUI progressBar = new GUI("Uploading " + t.getName());
				Thread guiThread = new Thread(progressBar);
				guiThread.start();
				t.setGUI(progressBar);
			}
		} else if ((flags & Config.TRANSFER) == Config.TRANSFER && (flags & Config.ACK) == Config.ACK) {
			Task t = tasks.get(taskId);
			t.acked(ackNo);
			t.updateProgressGUI();
		} else if ((flags & Config.TRANSFER) == Config.TRANSFER) {
			Task t = tasks.get(taskId);
			t.addContent(seqNo, data);
			t.updateProgressGUI();
			
			byte[] header = Header.ftp(taskId, 3, seqNo, Config.ACK | Config.TRANSFER, 0xffffffff);//TODO send correct ackNo (% K)
			byte[] pktWithChecksum = Header.addChecksum(header, Header.crc16(header));
			this.sendPacket(pktWithChecksum);
			
		} else if ((flags & Config.STATS) == Config.STATS) {
			System.out.println("Packet has STATS flag set"); 
		} else if ((flags & Config.LIST) == Config.LIST && (flags & Config.ACK) == Config.ACK) {
			String files = new String(data);
			view.showFilesOnServer(files.split(" "));
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
	
	public void uploadFile(File file) {
		int fileSize = Utils.getFileSize(file.getPath());
		
		byte[] header = Header.ftp(0, RANDOM_SEQ, 0,Config.REQ_UP, 0xffffffff);
		byte[] sizeHeader = Header.fileSize(fileSize);
		byte[] pkt = Utils.mergeArrays(header, sizeHeader, file.getName().getBytes());
		byte[] pktWithChecksum = Header.addChecksum(pkt, Header.crc16(pkt));
		if (Config.systemOuts) System.out.println("Sending packet with seq_no " + RANDOM_SEQ + " and fileSize " + fileSize);
		
		Task t = new Task(Task.Type.SEND_FROM_CLIENT, file, sock, serverIp, port, fileSize);
		requestedUps.add(t);
		
		sendPacket(pktWithChecksum);
	}
	
	public void downloadFile(File file) {
		byte[] header = Header.ftp(0, RANDOM_SEQ, 0, Config.REQ_DOWN,	0xffffffff);
		byte[] pkt = Utils.mergeArrays(header, file.getName().getBytes());
		byte[] pktWithChecksum = Header.addChecksum(pkt, Header.crc16(pkt));
		Task t = new Task(Task.Type.STORE_ON_CLIENT, file, sock, serverIp, port, 0);
		requestedDowns.add(t);
		
		sendPacket(pktWithChecksum);
	}
	
	private DatagramPacket getEmptyPacket() {
		byte[] data = new byte[Config.FTP_HEADERSIZE + Config.DATASIZE];
		return new DatagramPacket(data, data.length);
	}
	
	private void sendPacket(byte[] packet) {
		try {
			sock.send(new DatagramPacket(packet, packet.length, serverIp, port));
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
