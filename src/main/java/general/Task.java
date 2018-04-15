package general;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

public class Task extends Thread implements ITimeoutEventHandler {

	public enum Type {
		STORE_ON_CLIENT, SEND_FROM_CLIENT, STORE_ON_SERVER, SEND_FROM_SERVER
	}
	public static boolean actuallyStoreFile = true;

	private int id;
	private Task.Type type;
	private DatagramSocket sock;
	private InetAddress addr;
	private int port;
	private File downloadedFile;
	private FileOutputStream downloadedFileStream;
	private byte[][] storedPackets;
	private int totalFileSize;
	
	private int LAR = Config.FIRST_PACKET - 1;
	private int LFR = -1;
	private int sequenceNumber = Config.FIRST_PACKET;
	
	private boolean[] ackedPackets = new boolean[Config.K];
	private boolean lastPacket = false;
	private boolean waitingForAcks = true;

	public Task(Task.Type type, String fileName, DatagramSocket sock, InetAddress addr, int port, int fileSize) {
		this.setName(fileName);
		this.type = type;
		this.sock = sock;
		this.addr = addr;
		this.port = port;
		this.totalFileSize = fileSize;
		
		if (type == Task.Type.STORE_ON_CLIENT || type == Task.Type.STORE_ON_SERVER) {
			this.storedPackets = new byte[Config.K][Config.DATASIZE];
			Arrays.fill(this.storedPackets, null);
			this.downloadedFile = new File(String.format(fileName));
			try {
				this.downloadedFileStream = new FileOutputStream(this.downloadedFile);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void run() {
		int offset = 0;
		int datalen = -1;
		
		while (!lastPacket) {
			while (offset < totalFileSize && inSendingWindow(sequenceNumber)) {
				datalen = Math.min(Config.DATASIZE, this.totalFileSize - offset);
				lastPacket = offset + datalen >= totalFileSize;
				
				byte[] header = Header.ftp(this.id, sequenceNumber, 0, Config.UP, 0xffffffff);
				byte[] data = Utils.getFileContents(this.getName(), offset);
				byte[] pkt = Utils.mergeArrays(header, data);
				sendPacket(pkt);
				
				System.out.println("Sending packet with seq_no " + sequenceNumber);
				sequenceNumber = (sequenceNumber + 1) % Config.K;
				offset += datalen;

//				try {
//					Thread.sleep(10);
//				} catch (InterruptedException e) {
//				}
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
		System.out.println("[Hello] finished sending task!!!!!");//boolean finished
	}

//	private DatagramPacket getEmptyPacket() {
//		byte[] data = new byte[Config.HEADERSIZE + Config.DATASIZE];
//		return new DatagramPacket(data, data.length);
//	}
	
	public void setId(int id) {
		this.id = id;
	}

	private void sendPacket(byte[] packet) {
		try {
			sock.send(new DatagramPacket(packet, packet.length, addr, port));
		} catch (IOException e) {
			e.printStackTrace();
		}
		Utils.Timeout.SetTimeout(3000, this, packet);
	}

	private boolean inSendingWindow(int packetNumber) {
		return (LAR < packetNumber && packetNumber <= (LAR + Config.SWS))
				|| (LAR + Config.SWS >= Config.K && packetNumber <= (LAR + Config.SWS) % Config.K);
	}

	public boolean inReceivingWindow(int packetNumber) {
		return (LFR < packetNumber && packetNumber <= (LFR + Config.RWS))
				|| (LFR + Config.RWS >= Config.K && packetNumber <= (LFR + Config.RWS) % Config.K);
	}

	public int nextExpectedAck() {
		return (LAR + 1) % Config.K;
	}

	public int nextExpectedPacket() {
		if (LFR == -1) {
			LFR = Config.FIRST_PACKET - 1;
			return Config.FIRST_PACKET;
		}
		return (LFR + 1) % Config.K;
	}

	private boolean receivedAck(int packetNumber) {
		return ackedPackets[packetNumber];
	}

	public void acked(int ackNo) {
		System.out.println("ACK " + ackNo + " received.");
		if (ackNo == nextExpectedAck()) {
			LAR = ackNo;
			waitingForAcks = false;
			System.out.println("LAR is now " + LAR + ".");
		} else if (inSendingWindow(ackNo)) {
			ackedPackets[ackNo] = true;
		}

		while (ackedPackets[nextExpectedAck()]) {
			LAR = nextExpectedAck();
			System.out.println("LAR is now " + LAR + ". (from previous acks)");
			ackedPackets[LAR] = false;
		}
	}
	
	public void addContent(int seqNo, byte[] data) {
		if (seqNo == nextExpectedPacket()) {
			Utils.setContents(this.downloadedFileStream, data);
			System.out.println("added " + seqNo + " to filecontent.");
			if (this.downloadedFile.length() == this.totalFileSize) {	// means COMPLETE
				lastPacket = true;
				System.out.println("finished file..");
				try {
					this.downloadedFileStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			LFR++;
		} else if (inReceivingWindow(seqNo)) {
			storedPackets[seqNo] = data;
		}
		
		while (storedPackets[nextExpectedPacket()] != null) {
			Utils.setContents(this.downloadedFileStream, storedPackets[nextExpectedPacket()]);
			System.out.println("added " + nextExpectedPacket() + " to filecontent.");
			
			if (this.downloadedFile.length() == this.totalFileSize) {	// means COMPLETE
				lastPacket = true;
				System.out.println("finished file..");

				try {
					this.downloadedFileStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			storedPackets[nextExpectedPacket()] = null;
			LFR++;
		}
	}
	
	@Override
	public void TimeoutElapsed(Object tag) { //TODO timeouts van boven LAR nog niet stoppen
		byte[] pkt = (byte[]) tag;
		
		int seqNo = Header.twoBytes2int(pkt[4],pkt[5]);
		if (inSendingWindow(seqNo) && !receivedAck(seqNo)) {
			System.out.println("retransmission of packet " + seqNo);
			sendPacket(pkt);
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
	
	public int getCurrentFileSize() {
		return (int) this.downloadedFile.length();
	}

	public boolean finished() {
		return this.lastPacket;
	}

}
