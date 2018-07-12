package general;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SendTask extends Task implements ITimeoutEventHandler{

	private File transferFile;
	private RandomAccessFile fileToUpload;
	private int totalFileSize;
	private int retransmissions = 0;
	private int acksReceived = 0;
	private int LAR = Config.FIRST_PACKET - 1;
	private int sequenceNumber = Config.FIRST_PACKET;
	private boolean[] ackedPackets = new boolean[Config.K];
	private boolean waitingForAcks = true;
	private Lock l;
	private Condition ackReceived;
	
	
	public SendTask(File file, DatagramSocket sock, InetAddress addr, int port, int fileSize) {
		super(file, sock, addr, port, fileSize);
		
		this.name = file.getName();
		this.sock = sock;
		this.addr = addr;
		this.port = port;
		this.transferFile = file;
		this.totalFileSize = fileSize;
		
		l = new ReentrantLock();
		ackReceived = l.newCondition();
	}
	
	
	@Override
	public void run() {
		this.beginTimeSeconds = (int) System.currentTimeMillis() / 1000;
		
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

				byte[] sndHeader = Header.ftp(new FTPHeader(this.id, sequenceNumber, 0, Flag.TRANSFER, 0xffffffff));
				byte[] sndData = null;
				try {
					sndData = Utils.getNextContents(fileToUpload);
					
				} catch (IOException e) {
					e.printStackTrace();
					lastPacket = true;
				}
				
				byte[] sndPkt = Utils.mergeArrays(sndHeader, sndData);
				sendPacket(sndPkt);
				Utils.Timeout.SetTimeout(Config.TIMEOUT, this, sndPkt);
				retransmissions++;

				try {
					lastPacket = fileToUpload.getFilePointer() == fileToUpload.length();
				} catch (IOException e) {
					e.printStackTrace();
				}
//						sndData.length < Config.DATASIZE;

				if (Config.systemOuts) System.out.println("Sending packet with seq_no " + sequenceNumber);
				sequenceNumber = Utils.incrementNumberModuloK(sequenceNumber);

			}

			waitingForAcks = true;
			l.lock();
			while (waitingForAcks) {
				try { 
					ackReceived.await();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			l.unlock();
		}

		try {
			fileToUpload.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		this.endTimeSeconds = (int) System.currentTimeMillis() / 1000;
		System.out.println("Finished uploading " + this.name + ".");// boolean finished
		double percentagePacketLoss = (double) (this.retransmissions / (Math.ceil(this.totalFileSize / (double) Config.DATASIZE))) * 100;
		System.out.println("Percentage packet loss: " + percentagePacketLoss);
	}
	
	public void acked(int ackNo) {
		if (Config.systemOuts) System.out.println("ACK " + ackNo + " received.");
		
		if (inSendingWindow(ackNo)) {
			ackedPackets[ackNo] = true;
		}

		while (ackedPackets[nextExpectedAck()]) {
			l.lock();
			ackReceived.signal();
			waitingForAcks = false;
			l.unlock();
			
			LAR = nextExpectedAck();
			acksReceived++;
			
			if (Config.systemOuts) System.out.println("LAR is now " + LAR + ".");
			ackedPackets[LAR] = false;
		}
		
		int percentageProgress = (int) ((acksReceived / (Math.ceil(this.totalFileSize / (double) Config.DATASIZE))) * 100);
		this.setChanged();
		this.notifyObservers(percentageProgress);
	}
	
	private int nextExpectedAck() {
		return Utils.incrementNumberModuloK(LAR);
	}
	
	private boolean inSendingWindow(int packetNumber) {
		return (LAR < packetNumber && packetNumber <= (LAR + Config.SWS))
				|| (LAR + Config.SWS >= Config.K && packetNumber <= (LAR + Config.SWS) % Config.K);
	}

	

	private boolean receivedAck(int packetNumber) {
		return ackedPackets[packetNumber];
	}
	
	@Override
	public void TimeoutElapsed(Object tag) {
		byte[] pkt = (byte[]) tag;

		int seqNo = Header.bytes2int(pkt[2], pkt[3]);
		if (inSendingWindow(seqNo) && !receivedAck(seqNo)) {
			if (Config.systemOuts) System.out.println("retransmission of packet " + seqNo);
			sendPacket(pkt);
			Utils.Timeout.SetTimeout(Config.TIMEOUT, this, pkt);
			retransmissions++;
		}
	}

}
