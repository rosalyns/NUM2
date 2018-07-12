package general;

import java.net.InetAddress;

public class Packet {
	private FTPHeader ftpHeader;
	private byte[] fileSizeHeader;
	private byte[] data;
	private InetAddress addr;
	private int port;
	
	public Packet(FTPHeader ftpHeader, byte[] fileSizeHeader, byte[] data) {
		this.ftpHeader = ftpHeader;
		this.fileSizeHeader = fileSizeHeader;
		this.data = data;
	}
	
	public Packet(FTPHeader ftpHeader, byte[] data) {
		this(ftpHeader, null, data);
	}
	
	public Packet(FTPHeader ftpHeader) {
		this(ftpHeader, null, null);
	}
	
	public Packet(byte[] data) {
		this(null, null, data);
	}

	public FTPHeader getFtpHeader() {
		return ftpHeader;
	}
	
	public byte[] getFileSizeHeader() {
		return fileSizeHeader;
	}
	
	public boolean hasFileSizeHeader() {
		return this.fileSizeHeader == null;
	}

	public byte[] getData() {
		return data;
	}
	
	public int getDataLength() {
		return data.length;
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
