package general;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class StoreTask extends Task {
	private Queue<DataFragment> queue;
	
	private File transferFile;
	private FileOutputStream downloadedFileStream;
	
	private int LFR = -1;
	private byte[][] storedPackets;

	private boolean firstPacket = true;
	
	public StoreTask(File file, DatagramSocket sock, InetAddress addr, int port, int fileSize) {
		super(file, sock, addr, port, fileSize);
		
		
		
		this.transferFile = file;
		this.totalFileSize = fileSize;

		this.storedPackets = new byte[Config.K][];
		Arrays.fill(this.storedPackets, null);

		try {
			this.downloadedFileStream = new FileOutputStream(this.transferFile);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		this.queue = new ConcurrentLinkedQueue<>();
	}
	
	@Override
	public void run() {
		while(true) {
			DataFragment tuple = queue.poll();
			if (tuple != null) {
				addContent(tuple.getSeqNo(), tuple.getData());
			} else {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public synchronized void addContent(int seqNo, byte[] data) {
		if (firstPacket) {
			firstPacket = false;
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
				
				int percentageProgress = (int) ((this.getCurrentFileSize() / (double) this.totalFileSize) * 100);
				this.setChanged();
				this.notifyObservers(percentageProgress);
				
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
	
	private boolean inReceivingWindow(int packetNumber) {
		return (LFR < packetNumber && packetNumber <= (LFR + Config.RWS))
				|| (LFR + Config.RWS >= Config.K && packetNumber <= (LFR + Config.RWS) % Config.K);
	}

	private int getCurrentFileSize() {
		return (int) this.transferFile.length();
	}
	
	public int getTransmissionTimeSeconds() {
		if (this.beginTimeSeconds != -1 && this.endTimeSeconds != -1) {
			return this.endTimeSeconds - this.beginTimeSeconds;
		}
		return -1;
	}

	private int nextExpectedPacket() {
		if (LFR == -1) {
			LFR = Config.FIRST_PACKET - 1;
			return Config.FIRST_PACKET;
		}
		return Utils.incrementNumberModuloK(LFR);
	}
	
	public void addToQueue(DataFragment data) {
		queue.add(data);
	}
	
	public void setFileSize(int size) {
		this.totalFileSize = size;
	}
	
}
