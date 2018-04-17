package general;

public class Config {

	private Config() {}
	
	//debug
	public static final boolean systemOuts = true;
	
	//parameters
	public static final int FTP_HEADERSIZE = 12; // number of header bytes in each packet
	public static final int FILESIZE_HEADERSIZE = 4; // filesize header is used for requesting upload or downloads
	public static final int DATASIZE = 10000; // max. number of user data bytes in each packet
	public static final int K = 500;			// space of sequence number
	public static final int SWS = 10;		// sliding window size
	public static final int RWS = 10;		// receiving window size
	public static final int FIRST_PACKET = 2;
	public static final int TIMEOUT = 1500; //in ms

	//flags
	public static final int REQ_UP = 	0b10000000;
	public static final int REQ_DOWN = 	0b01000000;
	public static final int TRANSFER = 	0b00100000;
	public static final int ACK =		0b00010000;
	public static final int STATS = 		0b00001000;
	public static final int LIST = 		0b00000100;
	public static final int PAUSE = 		0b00000010;
	
}
