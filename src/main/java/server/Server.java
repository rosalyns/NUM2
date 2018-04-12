package server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

import client.ITimeoutEventHandler;
import general.Config;
import general.Header;
import general.Task;

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
	private List<Task> tasks;

	private static boolean keepAlive = true;

	public Server(int portArg) {
		this.port = portArg;
		this.tasks = new ArrayList<Task>();
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
				Thread.sleep(100);

			} catch (IOException | InterruptedException e) {
				// Thread.currentThread().interrupt();
				keepAlive = false;
			}
		}

		System.out.println("Stopped");
	}
	
	
	private void handlePacket(DatagramPacket packet) {
		byte[] pkt = packet.getData();
		int taskId = Header.fourBytes2dec(pkt[0],pkt[1],pkt[2],pkt[3]);
		int checksum = Header.fourBytes2dec(pkt[4],pkt[5],pkt[6],pkt[7]);
		int seqNo = Header.fourBytes2dec(pkt[8],pkt[9],pkt[10],pkt[11]);
		int ackNo = Header.fourBytes2dec(pkt[12],pkt[13],pkt[14],pkt[15]);
		byte flags = pkt[16];
		int windowSize = Header.fourBytes2dec(pkt[20],pkt[21],pkt[22],pkt[23]);
		
		System.out.println("[Server] Packet received from " + packet.getSocketAddress() + " with sequence number " + seqNo);
		
		byte[] data = new byte[pkt.length - Config.HEADERSIZE];
		System.arraycopy(pkt, Config.HEADERSIZE, data, 0, pkt.length - Config.HEADERSIZE);
		
		if ((flags & Config.REQ_DOWN) == Config.REQ_DOWN) {
			System.out.println("Packet has REQ_DOWN flag set");
			tasks.add(new Task(Task.Type.DOWNLOAD, new String(data), sock, packet.getAddress(), packet.getPort()));
		} else if ((flags & Config.REQ_UP) == Config.REQ_UP) {
			System.out.println("Packet has REQ_UP flag set");
			//check if enough space
			Task newTask = new Task(Task.Type.UPLOAD, "file1.png", sock, packet.getAddress(), packet.getPort());
			tasks.add(newTask);
			byte[] header = Header.ftp(newTask.id(), 3, seqNo + 1, Config.ACK | Config.REQ_UP, 0xffffffff);
			this.sendPacket(header, packet.getAddress(), packet.getPort());
		} else if ((flags & Config.UP) == Config.UP) {
			//TODO store file contents
			System.out.println("Packet has UP flag set");
		} else if ((flags & Config.DOWN) == Config.DOWN) {
			System.out.println("Packet has DOWN flag set");
		} else if ((flags & Config.STATS) == Config.STATS) {
			System.out.println("Packet has STATS flag set");
		}
	}

	private DatagramPacket getEmptyPacket() {
		byte[] data = new byte[Config.HEADERSIZE + Config.DATASIZE];
		return new DatagramPacket(data, data.length);
	}
	
	private void sendPacket(byte[] packet, InetAddress addr, int port) {
		try {
			sock.send(new DatagramPacket(packet, packet.length, addr, port));
			System.out.println("Sending something back to " + addr + ":" + port);
		} catch (IOException e) {
			e.printStackTrace();
		}
		client.Utils.Timeout.SetTimeout(1000, this, packet);
	}
	
	@Override
	public void TimeoutElapsed(Object tag) { //TODO make sense
		int numberPacketSent = ((byte[]) tag)[0];
//		if (inSendingWindow(numberPacketSent) && !receivedAck(numberPacketSent)) { TODO check if request is still open.
//			sendPacket((byte[]) tag);
//		}
		System.out.println("timeout elapsed!");
	}
}
