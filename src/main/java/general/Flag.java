package general;

public class Flag {
	
	private Flag() {}
	
	// flags
	public static final int REQ_UP = 0b10000000;
	public static final int REQ_DOWN = 0b01000000;
	public static final int TRANSFER = 0b00100000;
	public static final int ACK = 0b00010000;
	public static final int STATS = 0b00001000;
	public static final int LIST = 0b00000100;
	public static final int PAUSE = 0b00000010;
}
