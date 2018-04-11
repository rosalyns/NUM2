package server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import client.ITimeoutEventHandler;
import client.Utils;

public class Server {

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
	static final int HEADERSIZE = 24; // number of header bytes in each packet
	static final int DATASIZE = 128;

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
				handlePacket(p);
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
	
	private void handlePacket(DatagramPacket packet) {
		
	}

	private DatagramPacket getEmptyPacket() {
		byte[] data = new byte[HEADERSIZE + DATASIZE];
		return new DatagramPacket(data, data.length);
	}




}
