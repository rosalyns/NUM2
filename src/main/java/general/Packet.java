package general;

public class Packet {
	private FTPHeader ftpHeader;
	private byte[] data;
	
	public Packet(byte[] ftpHeader, byte[] data) {
		this.ftpHeader = Header.dissectFTPBytes(ftpHeader);
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
	

}
