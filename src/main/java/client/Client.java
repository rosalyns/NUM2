package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import general.Config;

public class Client implements ITimeoutEventHandler {

	// server address
	private InetAddress host;

	// server port
	private int port;
	private DatagramSocket socket;
	private TUI tui;

	// whether the simulation is finished
	private boolean simulationFinished = false;

	public Client(InetAddress serverAddress, int serverPort) throws IOException {
		this.host = serverAddress;
		this.port = serverPort;
		socket = new DatagramSocket();
		tui = new TUI(this, System.in);
		Thread tuiThread = new Thread(tui);
		tuiThread.start();
	}

	/**
	 * @return whether the simulation has finished
	 */
	public boolean isFinished() {
		return simulationFinished;
	}
	// ----------- methods for TUI -------------//
	
	private int LAR = -1;
	private int LFR = -1;
	private int filePointer = 0;
	private int datalen = -1;
	private int sequenceNumber = 0;
	private boolean[] ackedPackets = new boolean[Config.K];
	private boolean lastPacket = false;
	
	public void askForFiles() {
		System.out.println("asking for files..");
	}
	
	public void askForStatistics() {
		System.out.println("asking for statistics..");
	}
	
	public void askForProgress() {
		System.out.println("asking for progress..");
	}
	
	public void uploadFile(String fileName) {
		Integer[] fileContents = Utils.getFileContents(fileName);
		byte[] fileBytes = new byte[fileContents.length];
		for (int i = 0; i < fileContents.length; i++) {
			fileBytes[i] = (byte) (int)fileContents[i];
		}
		
		while (!lastPacket && !simulationFinished) {
			while (filePointer < fileContents.length && inSendingWindow(sequenceNumber)) {
				datalen = Math.min(Config.DATASIZE, fileContents.length - filePointer);
				lastPacket = datalen < Config.DATASIZE;

				byte[] pkt = new byte[Config.HEADERSIZE + datalen];
				byte[] header = this.createHeader(sequenceNumber);
				System.out.println("Sending file with seq_no " + sequenceNumber);
				
				System.arraycopy(header, 0, pkt, 0, Config.HEADERSIZE);
				System.arraycopy(fileBytes, filePointer, pkt, Config.HEADERSIZE, datalen);
				sequenceNumber = (sequenceNumber + 1) % Config.K;
				filePointer += datalen;
				sendPacket(pkt);
				
				 try {
				 Thread.sleep(1000);
				 } catch (InterruptedException e) {
				 }
			}
			boolean canSendAgain = false;
			while (!canSendAgain) {
				DatagramPacket p = getEmptyPacket();
				try {
					socket.receive(p);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				byte[] packet = p.getData();
				// p.getAddress()
				// Integer[] packet = getNetworkLayer().receivePacket();
				if (packet != null) {
					System.out.println("ACK" + packet[0] + " received.");
					if (packet[0] == nextAckPacket()) {
						LAR = packet[0];
						canSendAgain = true;
						System.out.println("LAR is now " + LAR + " .");
					} else if (inSendingWindow(packet[0])) {
						ackedPackets[packet[0]] = true;
					}

					while (ackedPackets[nextAckPacket()]) {
						LAR = nextAckPacket();
						ackedPackets[LAR] = false;
						System.out.println(LAR + " was already acked.");
					}
				} else {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
					}
				}
			}
		}
	}
	
	private DatagramPacket getEmptyPacket() {
		byte[] data = new byte[Config.HEADERSIZE + Config.DATASIZE];
		return new DatagramPacket(data, data.length);
	}
	
	private void sendPacket(byte[] packet) {
		try {
			socket.send(new DatagramPacket(packet, packet.length, host, port));
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

	@Override
	public void TimeoutElapsed(Object tag) {
		int numberPacketSent = ((byte[]) tag)[0];
		if (inSendingWindow(numberPacketSent) && !receivedAck(numberPacketSent)) {
			sendPacket((byte[]) tag);
		}
	}
	
	private byte[] createHeader(int seqNo) {
		byte[] header = new byte[Config.HEADERSIZE];

		//task_id
		header[0] = 0x00;
		header[1] = 0x00;
		header[2] = 0x00;
		header[3] = 0x00;
		
		//checksum
		header[4] = 0x00;
		header[5] = 0x00;
		header[6] = 0x00;
		header[7] = 0x00;
		
		//seq_number
		byte[] bytes = dec2fourBytes(seqNo);
		header[8] = bytes[0];
		header[9] = bytes[1];
		header[10] = bytes[2];
		header[11] = bytes[3];
		
		//ack_number
		header[12] = 0x00;
		header[13] = 0x00;
		header[14] = 0x00;
		header[15] = 0x00;
		
		//flags: req_upload/upload/req_download/download/stats
		header[16] = 0x00;
		header[17] = 0x00;
		header[18] = 0x00;
		header[19] = 0x00;
		
		//window_size
		header[20] = (byte) 0xff;
		header[21] = (byte) 0xff;
		header[22] = (byte) 0xff;
		header[23] = (byte) 0xff;
		
		return header;
	}
	
	private byte[] dec2fourBytes(int no) {
		byte[] seqBytes = new byte[4];
		seqBytes[0] = (byte) (no >> 24);
		seqBytes[1] = (byte) (no >> 16);
		seqBytes[2] = (byte) (no >> 8);
		seqBytes[3] = (byte) no;
		return seqBytes;
	}
	
	public void shutDown() {
		simulationFinished = true;
	}

}
