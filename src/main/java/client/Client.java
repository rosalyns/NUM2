package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.ArrayList;

import general.Task;
import general.Config;
import general.Header;

public class Client implements ITimeoutEventHandler {

	// server address
	private InetAddress host;

	// server port
	private int port;
	private DatagramSocket socket;
	private TUI tui;
	private List<Task> tasks;

	// whether the simulation is finished
	private boolean simulationFinished = false;

	public Client(InetAddress serverAddress, int serverPort) throws IOException {
		this.host = serverAddress;
		this.port = serverPort;
		this.tasks = new ArrayList<Task>();
		this.socket = new DatagramSocket();
		this.tui = new TUI(this, System.in);
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
		
		byte[] pkt = new byte[Config.HEADERSIZE + fileName.length()];
		byte[] header = Header.ftp(0,sequenceNumber, 0,Config.REQ_UP, 0xffffffff);
		System.out.println("Sending packet with seq_no " + sequenceNumber);
		System.arraycopy(header, 0, pkt, 0, Config.HEADERSIZE);
		System.arraycopy(fileName.getBytes(), 0, pkt, Config.HEADERSIZE, fileName.length());
		sequenceNumber = (sequenceNumber + 1) % Config.K;
		sendPacket(pkt);
		
		tasks.add(new Task(Task.Type.UPLOAD, fileName));
	}
	
	private void sendFile(byte[] file) {
		while (!lastPacket && !simulationFinished) {
			while (filePointer < file.length && inSendingWindow(sequenceNumber)) {
				datalen = Math.min(Config.DATASIZE, file.length - filePointer);
				lastPacket = datalen < Config.DATASIZE;

				byte[] pkt = new byte[Config.HEADERSIZE + datalen];
				byte[] header = Header.ftp(0, sequenceNumber,0, Config.UP, 0xffffffff);
				System.out.println("Sending packet with seq_no " + sequenceNumber);
				
				System.arraycopy(header, 0, pkt, 0, Config.HEADERSIZE);
				System.arraycopy(file, filePointer, pkt, Config.HEADERSIZE, datalen);
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
	
	public void shutDown() {
		simulationFinished = true;
	}

}
