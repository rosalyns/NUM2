package general;

import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Observable;

import client.progressview.ProgressGUI;

public class Task extends Observable implements ITimeoutEventHandler, Runnable {

	public enum Type {
		STORE_ON_CLIENT, SEND_FROM_CLIENT, STORE_ON_SERVER, SEND_FROM_SERVER
	}

	private Task.Type type;
	private DatagramSocket sock;
	private InetAddress addr;
	private File transferFile;
	private RandomAccessFile fileToUpload;
	private FileOutputStream downloadedFileStream;
	private ProgressGUI progressBar;
	private String name;
	private int id;
	private int port;
	private int totalFileSize;
	private int retransmissions = 0;
	private int acksReceived = 0;
	private int LAR = Config.FIRST_PACKET - 1;
	private int LFR = -1;
	private int sequenceNumber = Config.FIRST_PACKET;
	private int beginTimeSeconds = -1;
	private int endTimeSeconds = -1;

	private byte[][] storedPackets;
	private boolean[] ackedPackets = new boolean[Config.K];

	private boolean firstAck = true;
	private boolean waitingForAcks = true;

	public Task(Task.Type type, File file, DatagramSocket sock, InetAddress addr, int port, int fileSize) {
		this.name = file.getName();
		this.type = type;
		this.sock = sock;
		this.addr = addr;
		this.port = port;
		this.transferFile = file;
		this.totalFileSize = fileSize;

		if (type == Task.Type.STORE_ON_CLIENT || type == Task.Type.STORE_ON_SERVER) {
			this.storedPackets = new byte[Config.K][];
			Arrays.fill(this.storedPackets, null);

			try {
				this.downloadedFileStream = new FileOutputStream(this.transferFile);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void run() {
		try {
			fileToUpload = new RandomAccessFile(this.transferFile, "r");
			fileToUpload.seek(0);
		} catch (IOException e1) {
			e1.printStackTrace();
			System.out.println("Stopping the upload.");
			return;
		}

		boolean lastPacket = false;

		while (!lastPacket) {
			while (!lastPacket && inSendingWindow(sequenceNumber)) {

				byte[] header = Header.ftp(new FTPHeader(this.id, sequenceNumber, 0, Config.TRANSFER, 0xffffffff));
				byte[] data = null;
				try {
					data = Utils.getNextContents(fileToUpload);
					
				} catch (IOException e) {
					e.printStackTrace();
					lastPacket = true;
				}
				
				byte[] pkt = Utils.mergeArrays(header, data);
				byte[] pktWithChecksum = Header.addChecksum(pkt, Header.crc16(pkt));
				sendPacket(pktWithChecksum);

				lastPacket = data.length < Config.DATASIZE;

				if (Config.systemOuts)
					System.out.println("Sending packet with seq_no " + sequenceNumber);
				sequenceNumber = Utils.incrementNumberModuloK(sequenceNumber);

			}

			while (waitingForAcks) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			waitingForAcks = true;
		}

		try {
			fileToUpload.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Finished uploading " + this.name + ".");// boolean finished
		double percentagePacketLoss = (double) (this.retransmissions / (Math.ceil(this.totalFileSize / (double) Config.DATASIZE))) * 100;
		System.out.println("Percentage packet loss: " + percentagePacketLoss);
		if (this.progressBar != null) {
			this.progressBar.dispatchEvent(new WindowEvent(this.progressBar, WindowEvent.WINDOW_CLOSING));
		}
	}

	public void setId(int id) {
		this.id = id;
	}

	public void setFileSize(int size) {
		if (this.type == Task.Type.STORE_ON_CLIENT) {
			this.totalFileSize = size;
		}
	}

	public void setGUI(ProgressGUI progressGUI) {
		this.progressBar = progressGUI;
	}

	public void updateProgressBar() {
		if (this.type == Task.Type.SEND_FROM_CLIENT) {
			progressBar.updateProgress(
					(int) ((acksReceived / (Math.ceil(this.totalFileSize / (double) Config.DATASIZE))) * 100));
		} else if (this.type == Task.Type.STORE_ON_CLIENT) {
			progressBar.updateProgress((int) ((this.getCurrentFileSize() / (double) this.totalFileSize) * 100));
		}
	}

	private boolean inSendingWindow(int packetNumber) {
		return (LAR < packetNumber && packetNumber <= (LAR + Config.SWS))
				|| (LAR + Config.SWS >= Config.K && packetNumber <= (LAR + Config.SWS) % Config.K);
	}

	private boolean inReceivingWindow(int packetNumber) {
		return (LFR < packetNumber && packetNumber <= (LFR + Config.RWS))
				|| (LFR + Config.RWS >= Config.K && packetNumber <= (LFR + Config.RWS) % Config.K);
	}

	private int nextExpectedAck() {
		return Utils.incrementNumberModuloK(LAR);
	}

	private int nextExpectedPacket() {
		if (LFR == -1) {
			LFR = Config.FIRST_PACKET - 1;
			return Config.FIRST_PACKET;
		}
		return Utils.incrementNumberModuloK(LFR);
	}

	private boolean receivedAck(int packetNumber) {
		return ackedPackets[packetNumber];
	}

	public void acked(int ackNo) {
		if (Config.systemOuts) System.out.println("ACK " + ackNo + " received.");
		
		if (inSendingWindow(ackNo)) {
			ackedPackets[ackNo] = true;
		}

		while (ackedPackets[nextExpectedAck()]) {
			LAR = nextExpectedAck();
			acksReceived++;
			waitingForAcks = false;
			if (Config.systemOuts)
				System.out.println("LAR is now " + LAR + ".");
			ackedPackets[LAR] = false;
		}
	}

	public synchronized void addContent(int seqNo, byte[] data) {
		if (firstAck) {
			firstAck = false;
			this.beginTimeSeconds = (int) System.currentTimeMillis() / 1000;
		}
		if (inReceivingWindow(seqNo) && !this.finished()) {
			storedPackets[seqNo] = data;
		}

		while (storedPackets[nextExpectedPacket()] != null) {
			if (Config.systemOuts) System.out.println("added " + nextExpectedPacket() + " to filecontent.");
			
			Utils.setFileContents(this.downloadedFileStream, storedPackets[nextExpectedPacket()]);

			if (this.transferFile.length() == this.totalFileSize) { // means COMPLETE
				System.out.println("Finished downloading " + this.name + ".");
				this.endTimeSeconds = (int) System.currentTimeMillis() / 1000;
				System.out.println("Download took " + this.getTransmissionTimeSeconds() + " seconds.");
//				
//				System.out.println("Transmissions: "+ this.retransmissions);
//				System.out.println("Total filesize: " + this.totalFileSize);
//				System.out.println("DATASIZE " + Config.DATASIZE);
				
				if (this.progressBar != null) {
					this.progressBar.dispatchEvent(new WindowEvent(this.progressBar, WindowEvent.WINDOW_CLOSING));
				}

				try {
					this.downloadedFileStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			storedPackets[nextExpectedPacket()] = null;
			LFR = Utils.incrementNumberModuloK(LFR);
		}
	}

	private void sendPacket(byte[] packet) {
		
		try {
			sock.send(new DatagramPacket(packet, packet.length, addr, port));
		} catch (IOException e) {
			e.printStackTrace();
		}
		retransmissions++;
		Utils.Timeout.SetTimeout(Config.TIMEOUT, this, packet);
	}

	@Override
	public void TimeoutElapsed(Object tag) {
		byte[] pkt = (byte[]) tag;

		int seqNo = Header.twoBytes2int(pkt[4], pkt[5]);
		if (inSendingWindow(seqNo) && !receivedAck(seqNo)) {
			if (Config.systemOuts) System.out.println("retransmission of packet " + seqNo);
			sendPacket(pkt);
			
		}
	}

	public int getTaskId() {
		return this.id;
	}

	public Task.Type getType() {
		return this.type;
	}
	
	public String getName() {
		return this.name;
	}

	public int getTotalFileSize() {
		return this.totalFileSize;
	}

	public int getCurrentFileSize() {
		return (int) this.transferFile.length();
	}

//	public int getRetransmissions() {
//		if (this.type == Task.Type.STORE_ON_CLIENT) {
//			return this.retransmissions;
//		}
//		return -1;
//	}

	public int getTransmissionTimeSeconds() {
		if ((this.type == Task.Type.STORE_ON_CLIENT || this.type == Task.Type.STORE_ON_SERVER)
				&& this.beginTimeSeconds != -1 && this.endTimeSeconds != -1) {
			return this.endTimeSeconds - this.beginTimeSeconds;
		}
		return -1;
	}

	public boolean finished() {
		return this.beginTimeSeconds != -1 && this.endTimeSeconds != -1;
	}

}
