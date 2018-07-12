package general;

import java.net.InetAddress;

public class Packet {
	private FTPHeader ftpHeader;
	private byte[] data;
	private InetAddress addr;
	private int port;
	
	public Packet(byte[] ftpHeader, byte[] data) {
		this.ftpHeader = Header.bytesToFTP(ftpHeader);
		this.data = data;
	}
	
	public Packet(FTPHeader ftpHeader, byte[] data) {
		this.ftpHeader = ftpHeader;
		this.data = data;
	}

	public FTPHeader getFtpHeader() {
		return ftpHeader;
	}

	public byte[] getData() {
		return data;
	}
	
	public void setAddress(InetAddress addr) {
		this.addr = addr;
	}
	
	public void setPort(int port) {
		this.port = port;
	}
	
	public InetAddress getAddress() {
		return this.addr;
	}
	
	public int getPort() {
		return this.port;
	}
	

}
