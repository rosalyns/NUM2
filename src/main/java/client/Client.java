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

import client.FTPview.FTPGUI;
import client.FTPview.FTPView;
import client.progressview.ProgressGUI;

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
	private Queue<SendTask> requestedUps;
	private Queue<StoreTask> requestedDowns;

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

			} catch (IOException e) {
				keepAlive = false;
			}
		}
		Utils.Timeout.Stop();
		System.out.println("Stopped");
		
	}
	
	private void handlePacket(DatagramPacket packet) {
		byte[] rcvPkt = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
		byte[] rcvHeader = Arrays.copyOfRange(packet.getData(), 0, Config.FTP_HEADERSIZE);
		byte[] rcvData = new byte[rcvPkt.length - Config.FTP_HEADERSIZE];
		System.arraycopy(rcvPkt, Config.FTP_HEADERSIZE, rcvData, 0, rcvPkt.length - Config.FTP_HEADERSIZE);
		FTPHeader ftp = Header.dissectFTPBytes(rcvHeader);
		
		byte[] data;
		if (hasFlag(ftp.getFlags(), Config.REQ_DOWN) && hasFlag(ftp.getFlags(), Config.ACK)) {
			data = new byte[rcvPkt.length - Config.FTP_HEADERSIZE - Config.FILESIZE_HEADERSIZE];
			System.arraycopy(rcvPkt, Config.FTP_HEADERSIZE+Config.FILESIZE_HEADERSIZE, data, 0, rcvPkt.length - Config.FTP_HEADERSIZE - Config.FILESIZE_HEADERSIZE);
		} else {
			data = new byte[rcvPkt.length - Config.FTP_HEADERSIZE];
			System.arraycopy(rcvPkt, Config.FTP_HEADERSIZE, data, 0, rcvPkt.length - Config.FTP_HEADERSIZE);
		}
		
		if (hasFlag(ftp.getFlags(), Config.REQ_DOWN) && hasFlag(ftp.getFlags(), Config.ACK)) {
			int fileSize = Header.bytes2int(rcvPkt[Config.FTP_HEADERSIZE],rcvPkt[Config.FTP_HEADERSIZE + 1],rcvPkt[Config.FTP_HEADERSIZE+2],rcvPkt[Config.FTP_HEADERSIZE+3]);
			if (Config.systemOuts) System.out.println("filesize is " + fileSize);
			if (!requestedDowns.isEmpty()) {
				StoreTask t = requestedDowns.poll();
				t.setFileSize(fileSize);
				t.setId(ftp.getTaskId());
				tasks.put(t.getTaskId(), t);
				
				Thread taskThread = new Thread(t);
				taskThread.start();
				
				ProgressGUI progressBar = new ProgressGUI("Downloading "+ t.getName());
				Thread guiThread = new Thread(progressBar);
				guiThread.start();
				t.addObserver(progressBar);
			}
			
		} else if (hasFlag(ftp.getFlags(), Config.REQ_UP) && hasFlag(ftp.getFlags(), Config.ACK)) {
			if (!requestedUps.isEmpty()) {
				SendTask t = requestedUps.poll();
				t.setId(ftp.getTaskId());
				tasks.put(t.getTaskId(), t);
				
				Thread taskThread = new Thread(t);
				taskThread.start();
				
				ProgressGUI progressBar = new ProgressGUI("Uploading " + t.getName());
				Thread guiThread = new Thread(progressBar);
				guiThread.start();
				t.addObserver(progressBar);
			}
		} else if (hasFlag(ftp.getFlags(), Config.TRANSFER) && hasFlag(ftp.getFlags(), Config.ACK)) {
			SendTask t = (SendTask) tasks.get(ftp.getTaskId());
			t.acked(ftp.getAckNo());
		} else if (hasFlag(ftp.getFlags(), Config.TRANSFER)) {
			StoreTask t = (StoreTask) tasks.get(ftp.getTaskId());
			if (t == null) {
				System.out.println("Cannot find task #" + ftp.getTaskId());
				tasks.keySet().forEach((key) -> System.out.println(key));
			} else {
				t.addToQueue(new DataFragment(ftp.getSeqNo(), data));
				
				byte[] sndHeader = Header.ftp(new FTPHeader(ftp.getTaskId(), 3, ftp.getSeqNo(), Config.ACK | Config.TRANSFER, 0xffffffff));
				this.sendPacket(sndHeader);
			}
			
		} else if (hasFlag(ftp.getFlags(), Config.STATS)) {
			System.out.println("Packet has STATS flag set"); 
		} else if (hasFlag(ftp.getFlags(), Config.LIST) && hasFlag(ftp.getFlags(), Config.ACK)) {
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
		byte[] sndHeader = Header.ftp(new FTPHeader(0, RANDOM_SEQ, 0, Config.LIST, 0xffffffff));
		sendPacket(sndHeader);
	}
	
	public void askForStatistics() {
		byte[] sndHeader = Header.ftp(new FTPHeader(0, RANDOM_SEQ, 0, Config.STATS, 0xffffffff));
		sendPacket(sndHeader);
	}
	
	public void askForProgress() {
		System.out.println("asking for progress..");
	}
	
	public void uploadFile(File file) {
		int fileSize = Utils.getFileSize(file.getPath());
		
		byte[] sndHeader = Header.ftp(new FTPHeader(0, RANDOM_SEQ, 0,Config.REQ_UP, 0xffffffff));
		byte[] sndHeader2 = Header.fileSize(fileSize);
		byte[] sndPkt = Utils.mergeArrays(sndHeader, sndHeader2, file.getName().getBytes());
		if (Config.systemOuts) System.out.println("Sending packet with seq_no " + RANDOM_SEQ + " and fileSize " + fileSize);
		
		SendTask t = new SendTask(file, sock, serverIp, port, fileSize);
		
		requestedUps.add(t);
		
		sendPacket(sndPkt);
	}
	
	public void downloadFile(File file) {
		byte[] sndHeader = Header.ftp(new FTPHeader(0, RANDOM_SEQ, 0, Config.REQ_DOWN,	0xffffffff));
		byte[] sndPkt = Utils.mergeArrays(sndHeader, file.getName().getBytes());
		StoreTask t = new StoreTask(file, sock, serverIp, port, 0);
		requestedDowns.add(t);
		
		sendPacket(sndPkt);
	}
	
	private DatagramPacket getEmptyPacket() {
		byte[] data = new byte[Config.FTP_HEADERSIZE + Config.DATASIZE];
		return new DatagramPacket(data, data.length);
	}
	
	private boolean hasFlag(int allFlags, int flag) {
		return (allFlags & flag) == flag;
	}
	
	private void sendPacket(byte[] packet) {
		try {
			sock.send(new DatagramPacket(packet, packet.length, serverIp, port));
		} catch (IOException e) {
			e.printStackTrace();
		}
		Utils.Timeout.SetTimeout(Config.TIMEOUT_REQUEST, this, packet);
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
