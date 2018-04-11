package general;

public class Config {

	private Config() {}
	
	//parameters
	public static final int HEADERSIZE = 24; // number of header bytes in each packet
	public static final int DATASIZE = 128; // max. number of user data bytes in each packet
//	static final int K = 0xffffffff;
	public static final int K = 0x0fffffff;
	public static final int SWS = 25;
	public static final int RWS = 25;

	//flags
	public static final int REQ_UP = 0b10000000;
	public static final int REQ_DOWN = 0b00100000;
	public static final int UP = 0b01000000;
	public static final int DOWN = 0b00010000;
	public static final int STATS = 0b00001000;
	
}
