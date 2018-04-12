package general;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

import client.ITimeoutEventHandler;
import client.Utils;

public class Task extends Thread implements ITimeoutEventHandler {
	public static int ID = 0;

	public enum Type {
		DOWNLOAD, UPLOAD
	}

	private int id;
//	private String fileName;
	private Task.Type type;
	private DatagramSocket sock;
	private InetAddress addr;
	private int port;
	private byte[] file;
	private byte[][] storedPackets;
	private int totalFileSize;

	public Task(Task.Type type, String fileName, DatagramSocket sock, InetAddress addr, int port, int fileSize) {
//		this.fileName = fileName;
		this.type = type;
		this.sock = sock;
		this.addr = addr;
		this.port = port;
		this.totalFileSize = fileSize;
		this.id = ID;
		ID++;
		
		if(type == Task.Type.UPLOAD) {
			Integer[] fileContents = Utils.getFileContents(fileName);
			file = new byte[fileContents.length];
			for (int i = 0; i < fileContents.length; i++) {
				file[i] = (byte) (int)fileContents[i];
			}
		} else if (type == Task.Type.DOWNLOAD) {
			storedPackets = new byte[Config.K][Config.DATASIZE];
			Arrays.fill(storedPackets, null);
			file = new byte[0];
			
		}
	}

	private int LAR = -1;
	private int LFR = -1;
	private int offset = 0;
	private int datalen = -1;
	private int sequenceNumber = 0;
	private boolean[] ackedPackets = new boolean[Config.K];
	private boolean lastPacket = false;
	private boolean canSendAgain = false;

	@Override
	public void run() {
		while (!lastPacket) {
			while (offset < file.length && inSendingWindow(sequenceNumber)) {
				datalen = Math.min(Config.DATASIZE, file.length - offset);
				lastPacket = datalen < Config.DATASIZE;

				byte[] pkt = new byte[Config.HEADERSIZE + datalen];
				byte[] header = Header.ftp(0, sequenceNumber, 0, Config.UP, 0xffffffff);
				System.out.println("Sending packet with seq_no " + sequenceNumber);

				System.arraycopy(header, 0, pkt, 0, Config.HEADERSIZE);
				System.arraycopy(file, offset, pkt, Config.HEADERSIZE, datalen);
				sequenceNumber = (sequenceNumber + 1) % Config.K;
				offset += datalen;
				sendPacket(pkt);

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
			}
			canSendAgain = false;
			while (!canSendAgain) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

//	private DatagramPacket getEmptyPacket() {
//		byte[] data = new byte[Config.HEADERSIZE + Config.DATASIZE];
//		return new DatagramPacket(data, data.length);
//	}

	private void sendPacket(byte[] packet) {
		try {
			sock.send(new DatagramPacket(packet, packet.length, addr, port));
		} catch (IOException e) {
			e.printStackTrace();
		}
		client.Utils.Timeout.SetTimeout(1000, this, packet);
	}

	private boolean inSendingWindow(int packetNumber) {
		return (LAR < packetNumber && packetNumber <= (LAR + Config.SWS))
				|| (LAR + Config.SWS >= Config.K && packetNumber <= (LAR + Config.SWS) % Config.K);
	}

	public boolean inReceivingWindow(int packetNumber) {
		return (LFR < packetNumber && packetNumber <= (LFR + Config.RWS))
				|| (LFR + Config.RWS >= Config.K && packetNumber <= (LFR + Config.RWS) % Config.K);
	}

	private int nextAckPacket() {
		return (LAR + 1) % Config.K;
	}

	public int nextReceivingPacket() {
		return (LFR + 1) % Config.K;
	}

	private boolean receivedAck(int packetNumber) {
		return ackedPackets[packetNumber];
	}

	public void acked(int ackNo) {
		System.out.println("ACK" + ackNo + " received.");
		if (ackNo == nextAckPacket()) {
			LAR = ackNo;
			canSendAgain = true;
			System.out.println("LAR is now " + LAR + " .");
		} else if (inSendingWindow(ackNo)) {
			ackedPackets[ackNo] = true;
		}

		while (ackedPackets[nextAckPacket()]) {
			LAR = nextAckPacket();
			ackedPackets[LAR] = false;
			System.out.println(LAR + " was already acked.");
		}
	}
	
	public void addContent(int seqNo, byte[] data) {
		if (seqNo == nextReceivingPacket()) {
			int oldlength = file.length;
			int datalen = data.length;
			file = Arrays.copyOf(file, oldlength + datalen);
			System.arraycopy(data, 0, file, oldlength, datalen);
			System.out.println("added " + seqNo + " to filecontent.");
			if (file.length == this.totalFileSize) {	// means COMPLETE
				lastPacket = true;
				System.out.println("finished file..");
			}
			LFR++;
		} else if (inReceivingWindow(seqNo)) {
			storedPackets[seqNo] = data;
		}
		
		while (storedPackets[nextReceivingPacket()] != null) {
			int oldlength = file.length;
			int datalen = storedPackets[nextReceivingPacket()].length;
			file = Arrays.copyOf(file, oldlength + datalen);
			System.arraycopy(storedPackets[nextReceivingPacket()], 0, file, oldlength, datalen);
			System.out.println("added " + nextReceivingPacket() + " to filecontent.");
			
			if (file.length == this.totalFileSize) {	// means COMPLETE
				lastPacket = true;
				System.out.println("finished file..");
			}
			storedPackets[nextReceivingPacket()] = null;
			LFR++;
		}
	}
	
	@Override
	public void TimeoutElapsed(Object tag) {
		int numberPacketSent = ((byte[]) tag)[0];
		if (inSendingWindow(numberPacketSent) && !receivedAck(numberPacketSent)) {
			sendPacket((byte[]) tag);
		}
	}

	public int getTaskId() {
		return this.id;
	}

	public Task.Type getType() {
		return this.type;
	}
	
	public int getTotalFileSize() {
		return this.totalFileSize;
	}

	public boolean finished() {
		return this.lastPacket;
	}

}
