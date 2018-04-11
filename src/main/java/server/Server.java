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
		running = true;
		initShutdownHook();
		
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
    private static boolean running = false;
    private static int packetLength = 8;
	
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
				System.out.println("[Server] Packet received: " + new String(p.getData()));
				Thread.sleep(3);
				
			} catch (IOException e) {
				Thread.currentThread().interrupt();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		
		 System.out.println("Stopped");
	       running = false;
	}
	
	private DatagramPacket getEmptyPacket() {
		byte[] data = new byte[packetLength];
		return new DatagramPacket(data, packetLength);
	}
	
	private String packetToString(byte[] pkt) {
		String result = "";
		for (int i = 0; i < pkt.length; i++) {
			result += pkt[i];
		}
		return result;
	}
	
	private static void initShutdownHook() {
        final Thread shutdownThread = new Thread() {
            @Override
            public void run() {
                keepAlive = false;
                while (running) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        };
        Runtime.getRuntime().addShutdownHook(shutdownThread);
    }
	static final int HEADERSIZE = 2; // number of header bytes in each packet
	static final int DATASIZE = 128; // max. number of user data bytes in each packet
	static final int K = 255;
	static final int SWS = 25;
	static final int RWS = 25;

	private int fileID;
	private int LAR = -1;
	private int LFR = -1;
	private int filePointer = 0;
	private int datalen = -1;
	private int sequenceNumber = 0;
	private boolean[] ackedPackets = new boolean[K];
	private boolean lastPacket = false;
	
	public void sender() {
		Integer[] fileContents = Utils.getFileContents(getFileID());

		while (!lastPacket) {
			while (filePointer < fileContents.length && inSendingWindow(sequenceNumber)) { 
				datalen = Math.min(DATASIZE, fileContents.length - filePointer);
				lastPacket = datalen < DATASIZE;
	
				byte[] pkt = new byte[HEADERSIZE + datalen];
				pkt[0] = (byte) sequenceNumber;
				pkt[1] = lastPacket ? (byte) 1 : 0;
				
				System.arraycopy(fileContents, filePointer, pkt, HEADERSIZE, datalen);
				sequenceNumber =  (sequenceNumber + 1) % K;
				filePointer += datalen;
				sendPacket(pkt);
//				try {
//					Thread.sleep(1);
//				} catch (InterruptedException e) {
//				}
			}
			boolean canSendAgain = false;
			while (!canSendAgain) {
				DatagramPacket p = getEmptyPacket();
				try {
					sock.receive(p);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				byte[] packet = p.getData();
//				p.getAddress()
//				Integer[] packet = getNetworkLayer().receivePacket();
				if (packet != null) {
					System.out.println("ACK" + packet[0] + " received.");
					if (packet[0] == nextAckPacket()) {
						LAR = packet[0];
						canSendAgain = true;
						System.out.println("LAR is now "+ LAR + " .");
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

	private void sendPacket(byte[] packet) {
		try {
			sock.send(new DatagramPacket(packet, packet.length, InetAddress.getByName("localhost"), port));
		} catch (IOException e) {
			e.printStackTrace();
		}
//		getNetworkLayer().sendPacket(packet);
		System.out.println("Sent one packet with header=" + packet[0]);
		client.Utils.Timeout.SetTimeout(1000, this, packet);
	}
	
	private boolean inSendingWindow(int packetNumber) {
		return (LAR < packetNumber && packetNumber <= (LAR + SWS)) || (LAR+SWS >= K && packetNumber <= (LAR+SWS) % K);
	}
	
	private boolean inReceivingWindow(int packetNumber) {
		return (LFR < packetNumber && packetNumber <= (LFR + RWS)) || (LFR+RWS >= K && packetNumber <= (LFR+RWS) % K);
	}
	
	private int nextAckPacket() {
		return (LAR + 1) % K;
	}
	
	private int nextReceivingPacket() {
		return (LFR + 1) % K;
	}
	
	private boolean receivedAck(int packetNumber) {
		return ackedPackets[packetNumber];
	}
	
	/**
     * Sets the ID of the file to send/receive.
     * @param fileID the ID of the file to send/receive.
     */
    public void setFileID(int fileID) {
        this.fileID = fileID;
    }

    /**
     * @return the ID of the file to send/receive.
     */
    public int getFileID() {
        return fileID;
    }

	@Override
	public void TimeoutElapsed(Object tag) {
		int numberPacketSent = ((byte[]) tag)[0];
		if (inSendingWindow(numberPacketSent) && !receivedAck(numberPacketSent)) {
			sendPacket((byte[]) tag);
		}
	}

}
