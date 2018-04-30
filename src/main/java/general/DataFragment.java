package general;

public class DataFragment {
	private int seqNo;
	private byte[] data;
	
	public DataFragment(int seqNo, byte[] data) {
		this.seqNo = seqNo;
		this.data = data;
	}
	
	
	public int getSeqNo() {
		return this.seqNo;
	}
	
	public byte[] getData() {
		return this.data;
	}

}
