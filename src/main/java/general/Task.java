package general;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Observable;

import client.progressview.ProgressGUI;

public abstract class Task extends Observable implements Runnable {
	protected int id;
	protected ProgressGUI progressBar;
	protected DatagramSocket sock;
	protected InetAddress addr;
	protected int port;
	protected int totalFileSize;
	protected int beginTimeSeconds = -1;
	protected int endTimeSeconds = -1;
	protected String name;

	public Task(File file, DatagramSocket sock, InetAddress addr, int port, int fileSize) {
		this.sock = sock;
		this.addr = addr;
		this.port = port;
		this.name = file.getName();
	}
	
//	abstract void updateProgressBar();

	protected void sendPacket(byte[] packet) {
		try {
			sock.send(new DatagramPacket(packet, packet.length, addr, port));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getTaskId() {
		return this.id;
	}
	
	public String getName() {
		return this.name;
	}

	public int getTotalFileSize() {
		return this.totalFileSize;
	}

	public boolean finished() {
		return this.beginTimeSeconds != -1 && this.endTimeSeconds != -1;
	}

}
