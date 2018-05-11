package general;

public class Config {

	private Config() {}
	
	//debug
	public static final boolean systemOuts = false;
	
	//parameters
	public static final int FTP_HEADERSIZE = 10; // number of header bytes in each packet
	public static final int FILESIZE_HEADERSIZE = 4; // filesize header is used for requesting upload or downloads
	public static final int DATASIZE = 60000; // max. number of user data bytes in each packet
	public static final int K = 0xffff;			// space of sequence number
	public static final int SWS = 300;		// sliding window size
	public static final int RWS = 300;		// receiving window size
	public static final int FIRST_PACKET = 2;
	public static final int TIMEOUT = 500; //in ms
	public static final int TIMEOUT_REQUEST = 5000;

	//flags
	public static final int REQ_UP = 	0b10000000;
	public static final int REQ_DOWN = 	0b01000000;
	public static final int TRANSFER = 	0b00100000;
	public static final int ACK =		0b00010000;
	public static final int STATS = 		0b00001000;
	public static final int LIST = 		0b00000100;
	public static final int PAUSE = 		0b00000010;
	
}
