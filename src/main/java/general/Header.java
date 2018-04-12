package general;

public class Header {

	private Header() {}
	
	public static byte[] ftp(int taskId, int seqNo, int ackNo, int flags, int windowSize) {
		byte[] header = new byte[Config.HEADERSIZE];

		//task_id
		byte[] task = int2twoBytes(taskId);
		header[0] = task[0];
		header[1] = task[1];
		
		//checksum
		header[2] = 0x00;
		header[3] = 0x00;
		
		//seq_number
		byte[] seq = int2twoBytes(seqNo);
		header[4] = seq[0];
		header[5] = seq[1];
		
		//ack_number
		byte[] ack = int2twoBytes(ackNo);
		header[6] = ack[0];
		header[7] = ack[1];
		
		//flags: req_upload/upload/req_download/download/stats/ACK/pause
		header[8] = (byte) flags;
		header[9] = seq[1];
		
		//window_size
		byte[] window = int2twoBytes(windowSize);
		header[10] = window[0];
		header[11] = window[1];
		
		return header;
	}
	
	public static byte[] upload(int fileSize) {
		byte[] header = new byte[4];
		
		byte[] size = int2fourBytes(fileSize);
		header[0] = size[0];
		header[1] = size[1];
		header[2] = size[2];
		header[3] = size[3];
		
		return header;
	}
	
	public static byte[] int2twoBytes(int no) {
		byte[] bytes = new byte[2];
		bytes[0] = (byte) ((no >> 8) & 0xFF);
		bytes[1] = (byte) (no & 0xFF);
		return bytes;
	}
	
	public static byte[] int2fourBytes(int no) {
		byte[] bytes = new byte[4];
		bytes[0] = (byte) ((no >> 24) & 0xFF);
		bytes[1] = (byte) ((no >> 16) & 0xFF);
		bytes[2] = (byte) ((no >> 8) & 0xFF);
		bytes[3] = (byte) (no & 0xFF);
		return bytes;
	}
	
	public static int fourBytes2int(byte a, byte b, byte c, byte d) {
		return ((a & 0xFF) << 24) | ((b & 0xFF) << 16) | ((c & 0xFF) << 8) | (d & 0xFF);
	}
	
	public static int twoBytes2int(int a, int b) {
		return ((a & 0xFF) << 8) | (b & 0xFF);
	}
	
}
