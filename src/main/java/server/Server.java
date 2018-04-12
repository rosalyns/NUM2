package server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

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
		int taskId = Header.twoBytes2dec(pkt[0],pkt[1]);
		int checksum = Header.twoBytes2dec(pkt[2],pkt[3]);
		int seqNo = Header.twoBytes2dec(pkt[4],pkt[5]);
		int ackNo = Header.twoBytes2dec(pkt[6],pkt[7]);
		byte flags = pkt[8];
		int windowSize = Header.twoBytes2dec(pkt[10],pkt[11]);
		byte[] data; 
		
		//only REQ_UP has extra header that contains the size of the file the client wants to upload
		if ((flags & Config.REQ_UP)==Config.REQ_UP) {
			data = new byte[pkt.length - Config.HEADERSIZE - Config.UP_HEADERSIZE];
			System.arraycopy(pkt, Config.HEADERSIZE+Config.UP_HEADERSIZE, data, 0, pkt.length - Config.HEADERSIZE - Config.UP_HEADERSIZE);
		} else {
			data = new byte[pkt.length - Config.HEADERSIZE];
			System.arraycopy(pkt, Config.HEADERSIZE, data, 0, pkt.length - Config.HEADERSIZE);
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
			Task t = new Task(Task.Type.DOWNLOAD, new String(data), sock, packet.getAddress(), packet.getPort(), fileSize);
			tasks.add(t);
		} else if ((flags & Config.REQ_UP) == Config.REQ_UP) {
			int fileSize = Header.fourBytes2dec(pkt[12], pkt[13], pkt[14], pkt[15]);
			System.out.println("File has size " + fileSize + " bytes!");
			//TODO check if enough space
			
			String filename = new String(data);
			//TODO something with filename
			
			Task t = new Task(Task.Type.UPLOAD, "output"+filename, sock, packet.getAddress(), packet.getPort(), fileSize);
			tasks.add(t);
			byte[] header = Header.ftp(t.getTaskId(), 3, seqNo + 1, Config.ACK | Config.REQ_UP, 0xffffffff);
			this.sendPacket(header, packet.getAddress(), packet.getPort());
		} else if ((flags & Config.UP) == Config.UP) {
			//TODO store file contents
			
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
