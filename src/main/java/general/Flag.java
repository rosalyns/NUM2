package general;

public enum Flag {
	REQ_UP(0b10000000), 
	REQ_DOWN(0b01000000), 
	TRANSFER(0b00100000), 
	ACK(0b00010000), 
	STATS(0b00001000),
	LIST(0b00000100),
	PAUSE(0b00000010);
	
	private final int flag;
	Flag(int flag) { this.flag = flag; }
    public int getValue() { return flag; }
}
