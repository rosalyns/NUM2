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
				handlePacket(convertPacket(p));

			} catch (IOException e) {
				keepAlive = false;
			}
		}
		Utils.Timeout.Stop();
		System.out.println("Stopped");
		
	}
	
	private void handlePacket(Packet packet) {
		FTPHeader ftpHeader = packet.getFtpHeader();
		
		if (ftpHeader.hasFlag(Flag.REQ_DOWN) && ftpHeader.hasFlag(Flag.ACK)) {
			handleDownRequestAck(packet);
		} else if (ftpHeader.hasFlag(Flag.REQ_UP) && ftpHeader.hasFlag(Flag.ACK)) {
			handleUpRequestAck(ftpHeader);
		} else if (ftpHeader.hasFlag(Flag.TRANSFER) && ftpHeader.hasFlag(Flag.ACK)) {
			handleDataAck(ftpHeader);
		} else if (ftpHeader.hasFlag(Flag.TRANSFER)) {
			handleData(packet);
		} else if (ftpHeader.hasFlag(Flag.STATS)) {
			handleStatsRequest(); 
		} else if (ftpHeader.hasFlag(Flag.LIST) && ftpHeader.hasFlag(Flag.ACK)) {
			handleListAck(packet);
		}
	}

	private void handleListAck(Packet packet) {
		String files = new String(packet.getData());
		view.showFilesOnServer(files.split(" "));
	}

	private void handleStatsRequest() {
		System.out.println("Packet has STATS flag set");
	}

	private void handleData(Packet packet) {
		FTPHeader ftpHeader = packet.getFtpHeader();
		StoreTask t = (StoreTask) tasks.get(ftpHeader.getTaskId());
		if (t == null) {
			System.out.println("Cannot find task #" + ftpHeader.getTaskId());
			tasks.keySet().forEach((key) -> System.out.println(key));
		} else {
			t.addToQueue(new DataFragment(ftpHeader.getSeqNo(), packet.getData()));
			
			Packet sndPkt = new Packet(new FTPHeader(ftpHeader.getTaskId(), 3, ftpHeader.getSeqNo(), Flag.ACK.getValue() | Flag.TRANSFER.getValue(), 0xffffffff));
			this.sendPacket(sndPkt);
		}
	}

	private void handleDataAck(FTPHeader ftpHeader) {
		SendTask t = (SendTask) tasks.get(ftpHeader.getTaskId());
		t.acked(ftpHeader.getAckNo());
	}

	private void handleUpRequestAck(FTPHeader ftpHeader) {
		if (!requestedUps.isEmpty()) {
			SendTask t = requestedUps.poll();
			t.setId(ftpHeader.getTaskId());
			tasks.put(t.getId(), t);
			
			Thread taskThread = new Thread(t);
			taskThread.start();
			
			ProgressGUI progressBar = new ProgressGUI("Uploading " + t.getName());
			Thread guiThread = new Thread(progressBar);
			guiThread.start();
			t.addObserver(progressBar);
		}
	}

	private void handleDownRequestAck(Packet packet) {
		FTPHeader ftpHeader = packet.getFtpHeader();
		byte[] data = new byte[packet.getData().length - Config.FILESIZE_HEADERSIZE];
		System.arraycopy(packet.getData(), Config.FILESIZE_HEADERSIZE, data, 0, packet.getData().length - Config.FILESIZE_HEADERSIZE);
		
		int fileSize = Header.bytes2int(packet.getData()[0], packet.getData()[1], packet.getData()[2], packet.getData()[3]);
		if (Config.systemOuts) System.out.println("filesize is " + fileSize);
		if (!requestedDowns.isEmpty()) {
			StoreTask t = requestedDowns.poll();
			t.setFileSize(fileSize);
			t.setId(ftpHeader.getTaskId());
			tasks.put(t.getId(), t);
			
			Thread taskThread = new Thread(t);
			taskThread.start();
			
			ProgressGUI progressBar = new ProgressGUI("Downloading "+ t.getName());
			Thread guiThread = new Thread(progressBar);
			guiThread.start();
			t.addObserver(progressBar);
		}
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
		return result;
	}
	
	/**
	 * @return whether the simulation has finished
	 */
	public boolean isFinished() {
		return simulationFinished;
	}
	// ----------- methods for TUI -------------//
	
	
	public void askForFiles() {
		Packet sndPkt = new Packet(new FTPHeader(0, RANDOM_SEQ, 0, Flag.LIST.getValue(), 0xffffffff));
		sendPacket(sndPkt);
	}
	
	public void askForStatistics() {
		Packet sndPkt = new Packet(new FTPHeader(0, RANDOM_SEQ, 0, Flag.STATS.getValue(), 0xffffffff));
		sendPacket(sndPkt);
	}
	
	public void askForProgress() {
		System.out.println("asking for progress..");
	}
	
	public void uploadFile(File file) {
		int fileSize = Utils.getFileSize(file.getPath());
		
		FTPHeader ftp = new FTPHeader(0, RANDOM_SEQ, 0, Flag.REQ_UP.getValue(), 0xffffffff);
		byte[] fileSizeHeader = Header.fileSizeInBytes(fileSize);
		byte[] sndData = file.getName().getBytes();
		Packet sndPkt = new Packet(ftp, fileSizeHeader, sndData);
		
		if (Config.systemOuts) System.out.println("Sending packet with seq_no " + RANDOM_SEQ + " and fileSize " + fileSize);
		
		SendTask t = new SendTask(file, sock, serverIp, port, fileSize);
		
		requestedUps.add(t);
		
		sendPacket(sndPkt);
	}
	
	public void downloadFile(File file) {
		FTPHeader ftp = new FTPHeader(0, RANDOM_SEQ, 0, Flag.REQ_DOWN.getValue(),	0xffffffff);
		byte[] sndData = file.getName().getBytes();
		Packet sndPkt = new Packet(ftp, sndData);
		
		StoreTask t = new StoreTask(file, sock, serverIp, port, 0);
		requestedDowns.add(t);
		
		sendPacket(sndPkt);
	}
	
	private DatagramPacket getEmptyPacket() {
		byte[] data = new byte[Config.FTP_HEADERSIZE + Config.DATASIZE];
		return new DatagramPacket(data, data.length);
	}
	
	private void sendPacket(Packet packet) {
		try {
			sock.send(new DatagramPacket(packet.getData(), packet.getDataLength(), serverIp, port));
		} catch (IOException e) {
			e.printStackTrace();
		}
		Utils.Timeout.SetTimeout(Config.TIMEOUT_REQUEST, this, packet);
	}

	@Override
	public void TimeoutElapsed(Object tag) {
		Packet pkt = (Packet) tag;
		FTPHeader ftpHeader = pkt.getFtpHeader();
		
		if (ftpHeader.hasFlag(Flag.REQ_UP) && !requestedUps.isEmpty()) {
			sendPacket(pkt);
		} else if (ftpHeader.hasFlag(Flag.REQ_DOWN) && !requestedDowns.isEmpty()) {
			sendPacket(pkt);
		}
	}
	
	public void shutDown() {
		simulationFinished = true;
	}

}
