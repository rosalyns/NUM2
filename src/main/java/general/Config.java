package general;

public class Config {

	private Config() {}
	
	//parameters
	public static final int HEADERSIZE = 12; // number of header bytes in each packet
	public static final int UP_HEADERSIZE = 4;
	public static final int DATASIZE = 1450; // max. number of user data bytes in each packet
//	static final int K = 0xffffffff;
	public static final int K = 500;
	public static final int SWS = 20;
	public static final int RWS = 20;
	public static final int FIRST_PACKET = 2;

	//flags
	public static final int REQ_UP = 	0b10000000;
	public static final int REQ_DOWN = 	0b00100000;
	public static final int TRANSFER = 	0b00010000;
	public static final int STATS = 		0b00001000;
	public static final int ACK = 		0b00000100;
	public static final int PAUSE = 		0b00000010;
	
}
