package general;

import java.util.Arrays;

public class Header {

	private Header() {}
	
	public static byte[] ftp(FTPHeader ftp) {
		byte[] header = new byte[Config.FTP_HEADERSIZE];

		//task_id
		byte[] task = int2twoBytes(ftp.getTaskId());
		header[0] = task[0];
		header[1] = task[1];
		
		//checksum
		header[2] = 0x00;
		header[3] = 0x00;
		
		//seq_number
		byte[] seq = int2twoBytes(ftp.getSeqNo());
		header[4] = seq[0];
		header[5] = seq[1];
		
		//ack_number
		byte[] ack = int2twoBytes(ftp.getAckNo());
		header[6] = ack[0];
		header[7] = ack[1];
		
		//flags: req_upload/upload/req_download/download/stats/ACK/pause
		header[8] = (byte) ftp.getFlags();
		header[9] = seq[1];
		
		//window_size
		byte[] window = int2twoBytes(ftp.getWindowSize());
		header[10] = window[0];
		header[11] = window[1];
		
		return header;
	}
	
	public static FTPHeader dissectFTPBytes(byte[] header) {
		
		int taskId = Header.twoBytes2int(header[0],header[1]);
//		int checksum = Header.twoBytes2int(header[2],header[3]);
		int seqNo = Header.twoBytes2int(header[4],header[5]);
		int ackNo = Header.twoBytes2int(header[6],header[7]);
		byte flags = header[8];
		int windowSize = Header.twoBytes2int(header[10],header[11]);
		
		return new FTPHeader(taskId, seqNo, ackNo, flags, windowSize);
	}
	
	public static byte[] fileSize(int fileSize) {
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
	
	public static boolean checksumCorrect(byte[] pkt, int checksum) {
		return Header.crc16(pkt) == checksum;
	}
	
	/**
	 * Calculcate CRC of header + data.
	 */
	public static int crc16(final byte[] buffer) {
	    int crc = 0xFFFF;

	    for (int j = 0; j < buffer.length ; j++) {
	        crc = ((crc  >>> 8) | (crc  << 8) )& 0xffff;
	        crc ^= (buffer[j] & 0xff);//byte to int, trunc sign
	        crc ^= ((crc & 0xff) >> 4);
	        crc ^= (crc << 12) & 0xffff;
	        crc ^= ((crc & 0xFF) << 5) & 0xffff;
	    }
	    crc &= 0xffff;
	    return crc;
	}
	
	public static byte[] addChecksum(byte[] pkt, int checksum) {
		byte[] sum = int2twoBytes(checksum);
		pkt[2] = sum[0];
		pkt[3] = sum[1];
		return pkt;
	}
	
	
}
