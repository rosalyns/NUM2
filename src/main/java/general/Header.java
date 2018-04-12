package general;

public class Header {

	private Header() {}
	
	public static byte[] ftp(int taskId, int seqNo, int ackNo, int flags, int windowSize) {
		byte[] header = new byte[Config.HEADERSIZE];

		//task_id
		byte[] task = dec2fourBytes(taskId);
		header[0] = task[0];
		header[1] = task[1];
		header[2] = task[2];
		header[3] = task[3];
		
		//checksum
		header[4] = 0x00;
		header[5] = 0x00;
		header[6] = 0x00;
		header[7] = 0x00;
		
		//seq_number
		byte[] seq = dec2fourBytes(seqNo);
		header[8] = seq[0];
		header[9] = seq[1];
		header[10] = seq[2];
		header[11] = seq[3];
		
		//ack_number
		byte[] ack = dec2fourBytes(ackNo);
		header[12] = ack[0];
		header[13] = ack[1];
		header[14] = ack[2];
		header[15] = ack[3];
		
		//flags: req_upload/upload/req_download/download/stats/ACK/pause
		header[16] = (byte) flags;
		header[17] = 0x00;
		header[18] = 0x00;
		header[19] = 0x00;
		
		//window_size
		byte[] window = dec2fourBytes(windowSize);
		header[20] = window[0];
		header[21] = window[1];
		header[22] = window[2];
		header[23] = window[3];
		
		return header;
	}
	
	public static byte[] upload() {
		return new byte[8];
	}
	
	public static byte[] dec2fourBytes(int no) {
		byte[] seqBytes = new byte[4];
		seqBytes[0] = (byte) (no >> 24);
		seqBytes[1] = (byte) (no >> 16);
		seqBytes[2] = (byte) (no >> 8);
		seqBytes[3] = (byte) no;
		return seqBytes;
	}
	
	public static int fourBytes2dec(int a, int b, int c, int d) {
		int seq = 0;
		seq = a << 24;
		seq += b << 16;
		seq += c << 8;
		seq += d;
		return seq;
	}
	
}
