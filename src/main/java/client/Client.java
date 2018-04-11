package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Client {

	// server address
	private InetAddress host;

	// server port
	private int port;
	private DatagramSocket socket;

	// whether the simulation is finished
	private boolean simulationFinished = false;

	public Client(InetAddress serverAddress, int serverPort) throws IOException {
		this.host = serverAddress;
		this.port = serverPort;
		socket = new DatagramSocket();
		
		byte[] buf = "Hallo!!!".getBytes();
		
		this.sendMessage(buf);
	}

	/**
	 * Sends a message to the server
	 * 
	 * @param message
	 *            the message to send
	 */
	private void sendMessage(byte[] data) {
		DatagramPacket pkt = new DatagramPacket(data, data.length, host, port);
		while (!simulationFinished) {
			try {
				socket.send(pkt);
				System.out.println("Sending message..");
				Thread.sleep(2000);
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
				simulationFinished=true;
			}
		}
	}

	/**
	 * @return whether the simulation has finished
	 */
	public boolean isFinished() {
		return simulationFinished;
	}

}
