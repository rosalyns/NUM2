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
		
		//seq_number
		byte[] seq = int2twoBytes(ftp.getSeqNo());
		header[2] = seq[0];
		header[3] = seq[1];
		
		//ack_number
		byte[] ack = int2twoBytes(ftp.getAckNo());
		header[4] = ack[0];
		header[5] = ack[1];
		
		//flags: req_upload/upload/req_download/download/stats/ACK/pause
		header[6] = (byte) ftp.getFlags();
		header[7] = seq[1];
		
		//window_size
		byte[] window = int2twoBytes(ftp.getWindowSize());
		header[8] = window[0];
		header[9] = window[1];
		
		return header;
	}
	
	public static FTPHeader dissectFTPBytes(byte[] header) {
		
		int taskId = Header.bytes2int(header[0],header[1]);
		int seqNo = Header.bytes2int(header[2],header[3]);
		int ackNo = Header.bytes2int(header[4],header[5]);
		byte flags = header[6];
		int windowSize = Header.bytes2int(header[8],header[9]);
		
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
	
	public static byte[] int2XBytes(int no, int noBytes) {
		//throw exception
		
		byte[] bytes = new byte[noBytes];
		
		for (int i = 0; i < bytes.length; i++) {
			bytes[bytes.length - 1 - i] = (byte) ((no >> (8 * i)) & 0xFF);
		}
		
		return bytes;
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
	
	private static int xbytes2int(byte...array) {
		int result = 0;
		
		for (int i = 0; i < array.length; i++) {
			result = result | ((array[array.length - 1 - i] & 0xFF) << (8 * i));
		}
		
		return result;
	}
	
	public static int bytes2int(byte a) {
		return Header.xbytes2int(a);
	}

	public static int bytes2int(byte a, byte b) {
		return Header.xbytes2int(a, b);
	}

	public static int bytes2int(byte a, byte b, byte c) {
		return Header.xbytes2int(a, b, c);
	}

	public static int bytes2int(byte a, byte b, byte c, byte d) {
		return Header.xbytes2int(a, b, c, d);
	}
	
	public static byte[] mergeArrays(byte[]... arrays) {
		byte[] first = arrays[0];

		for (int i = 1; i < arrays.length; i++) {
			byte[] toAppend = arrays[i];

			int oldlength = first.length;
			int extralen = toAppend.length;
			first = Arrays.copyOf(first, oldlength + extralen);
			System.arraycopy(toAppend, 0, first, oldlength, extralen);
		}

		return first;
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
