package server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import client.ITimeoutEventHandler;
import client.Utils;

public class Server implements ITimeoutEventHandler {

	// --------------- MAIN METHOD ---------------- //
	private static int serverPort = 8002;

	public static void main(String[] args) {
		try {
			Server server = new Server(serverPort);
			server.run();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// --------------- CLASS METHODS ---------------- //

	private int port;
	private DatagramSocket sock;

	private static boolean keepAlive = true;

	public Server(int portArg) {
		this.port = portArg;
	}

	public void run() throws IOException {
		System.out.println("[Server] Opening a socket on port " + port);
		sock = new DatagramSocket(port);

		while (keepAlive) {
			DatagramPacket p = getEmptyPacket();
			try {
				System.out.println("[Server] Waiting for packets...");
				sock.receive(p);
				int seqNo = fourBytes2dec(p.getData()[8],p.getData()[9],p.getData()[10],p.getData()[11]);
				System.out.println("[Server] Packet received from " + p.getAddress() + " with sequence number " + seqNo);
				Thread.sleep(100);

			} catch (IOException | InterruptedException e) {
				// Thread.currentThread().interrupt();
				keepAlive = false;
			}
		}

		System.out.println("Stopped");
	}
	
	private int fourBytes2dec(int a, int b, int c, int d) {
		int seq = 0;
		seq = a << 24;
		seq += b << 16;
		seq += c << 8;
		seq += d;
		return seq;
	}

	private DatagramPacket getEmptyPacket() {
		byte[] data = new byte[HEADERSIZE + DATASIZE];
		return new DatagramPacket(data, data.length);
	}

	static final int HEADERSIZE = 24; // number of header bytes in each packet
	static final int DATASIZE = 128; // max. number of user data bytes in each packet
	static final int K = 255;
	static final int SWS = 25;
	static final int RWS = 25;

	private int LAR = -1;
	private int LFR = -1;
	private int filePointer = 0;
	private int datalen = -1;
	private int sequenceNumber = 0;
	private boolean[] ackedPackets = new boolean[K];
	private boolean lastPacket = false;

	private void sendPacket(byte[] packet) {
		try {
			sock.send(new DatagramPacket(packet, packet.length, InetAddress.getByName("localhost"), port));
		} catch (IOException e) {
			e.printStackTrace();
		}
		// getNetworkLayer().sendPacket(packet);
		System.out.println("Sent one packet with header=" + packet[0]);
		client.Utils.Timeout.SetTimeout(1000, this, packet);
	}

	private boolean inSendingWindow(int packetNumber) {
		return (LAR < packetNumber && packetNumber <= (LAR + SWS))
				|| (LAR + SWS >= K && packetNumber <= (LAR + SWS) % K);
	}

	public boolean inReceivingWindow(int packetNumber) {
		return (LFR < packetNumber && packetNumber <= (LFR + RWS))
				|| (LFR + RWS >= K && packetNumber <= (LFR + RWS) % K);
	}

	private int nextAckPacket() {
		return (LAR + 1) % K;
	}

	public int nextReceivingPacket() {
		return (LFR + 1) % K;
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

}
