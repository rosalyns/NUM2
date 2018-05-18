package general;

public class Config {

	private Config() {}
	
	//debug
	public static final boolean systemOuts = false;
	
	//parameters
	public static final int FTP_HEADERSIZE = 10; // number of header bytes in each packet
	public static final int FILESIZE_HEADERSIZE = 4; // filesize header is used for requesting upload or downloads
	public static final int DATASIZE = 6000; // max. number of user data bytes in each packet
	public static final int K = 0xffff;			// space of sequence number
	public static final int SWS = 150;		// sliding window size
	public static final int RWS = 150;		// receiving window size
	public static final int FIRST_PACKET = 2;
	public static final int TIMEOUT = 200; //in ms
	public static final int TIMEOUT_REQUEST = 5000;

	
}
