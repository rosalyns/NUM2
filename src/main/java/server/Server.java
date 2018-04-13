package server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import client.ITimeoutEventHandler;
import client.Utils;
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
	private Map<Integer, Task> tasks;
	private int currentTaskId = 1;

	private static boolean keepAlive = true;
	private static int RANDOM_SEQ = 25;

	public Server(int portArg) {
		this.port = portArg;
		this.tasks = new HashMap<Integer, Task>();
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
		byte[] shorter = Arrays.copyOfRange(pkt, 0, packet.getLength());
		int taskId = Header.twoBytes2int(pkt[0],pkt[1]);
		int checksum = Header.twoBytes2int(pkt[2],pkt[3]);
		int seqNo = Header.twoBytes2int(pkt[4],pkt[5]);
		int ackNo = Header.twoBytes2int(pkt[6],pkt[7]);
		byte flags = pkt[8];
		int windowSize = Header.twoBytes2int(pkt[10],pkt[11]);
		byte[] data; 
		
		//only REQ_UP has extra header that contains the size of the file the client wants to upload
		if ((flags & Config.REQ_UP)==Config.REQ_UP) {
			data = new byte[shorter.length - Config.HEADERSIZE - Config.UP_HEADERSIZE];
			System.arraycopy(shorter, Config.HEADERSIZE+Config.UP_HEADERSIZE, data, 0, shorter.length - Config.HEADERSIZE - Config.UP_HEADERSIZE);
		} else {
			data = new byte[shorter.length - Config.HEADERSIZE];
			System.arraycopy(shorter, Config.HEADERSIZE, data, 0, shorter.length - Config.HEADERSIZE);
		}
		
		System.out.println("[Server] Packet received from " + packet.getSocketAddress() 
		+ " :\ntaskID: "  + taskId 
		+ "\nchecksum: " + checksum 
		+ "\nseqNo: " + seqNo 
		+ "\nackNo: " + ackNo 
		+ "\nflags: " + Integer.toBinaryString(flags) 
		+ "\nwindowSize: " + windowSize);
		
		if ((flags & Config.REQ_DOWN) == Config.REQ_DOWN) {
			int fileSize = Utils.getFileSize(new String(data));
			
			Task t = new Task(Task.Type.STORE_FILE, new String(data), sock, packet.getAddress(), packet.getPort(), fileSize);
			t.setId(currentTaskId);
			tasks.put(t.getTaskId(), t);
			currentTaskId++;
			
		} else if ((flags & Config.REQ_UP) == Config.REQ_UP) {
			int fileSize = Header.fourBytes2int(pkt[12], pkt[13], pkt[14], pkt[15]);
			System.out.println("File has size " + fileSize + " bytes!");
			//TODO check if enough space
			
			String fileName = new String(data);
			//TODO something with filename
			
			Task t = new Task(Task.Type.STORE_FILE, "output"+fileName, sock, packet.getAddress(), packet.getPort(), fileSize);
			t.setId(currentTaskId);
			tasks.put(t.getTaskId(), t);
			currentTaskId++;
			
			byte[] header = Header.ftp(t.getTaskId(), RANDOM_SEQ, seqNo + 1, Config.ACK | Config.REQ_UP, 0xffffffff);
			this.sendPacket(header, packet.getAddress(), packet.getPort());
		} else if ((flags & Config.UP) == Config.UP) {
			Task t = tasks.get(taskId);
			t.addContent(seqNo, data);
//			getNetworkLayer().sendPacket(ack);
			
			byte[] header = Header.ftp(t.getTaskId(), RANDOM_SEQ, seqNo + 1, Config.ACK | Config.UP, 0xffffffff);
			this.sendPacket(header, packet.getAddress(), packet.getPort());
//			this.sendPacket(packet, addr, windowSize);
			
		} else if ((flags & Config.DOWN) == Config.DOWN) {
		} else if ((flags & Config.STATS) == Config.STATS) {
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
//		int numberPacketSent = ((byte[]) tag)[0];
//		if (inSendingWindow(numberPacketSent) && !receivedAck(numberPacketSent)) { TODO check if request is still open.
//			sendPacket((byte[]) tag);
//		}
		System.out.println("timeout elapsed!");
	}
}
