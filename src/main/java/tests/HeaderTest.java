package tests;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import general.Header;

public class HeaderTest {

	@Before
	public void setUp() throws Exception {
	}

	@Test
	public final void testFtp() {
		fail("Not yet implemented"); // TODO
	}

	@Test
	public final void testDissectFTPBytes() {
		fail("Not yet implemented"); // TODO
	}

	@Test
	public final void testFileSize() {
		fail("Not yet implemented"); // TODO
	}
	
	@Test
	public final void testInt2XBytesOne() {
		fail("Not yet implemented"); // TODO
	}
	
	@Test
	public final void testInt2XBytesTwo() {
		fail("Not yet implemented"); // TODO
	}
	
	@Test
	public final void testInt2XBytesThree() {
		fail("Not yet implemented"); // TODO
	}
	
	@Test
	public final void testInt2XBytesFour() {
		fail("Not yet implemented"); // TODO
	}

	@Test
	public final void testBytes2intByte() {
		byte a = 15;
		int act = Header.bytes2int(a);
		assertEquals(15, act);
		assertEquals(15, act & 0xFF);
	}

	@Test
	public final void testBytes2intByteByte() {
		byte a = 15;
		byte b = 42;
		int act = Header.bytes2int(a,b);
		assertEquals(3882, act);
		assertEquals(15, ((act & 0xFF00) >> 8));
		assertEquals(42, (act & 0x00FF));
	}

	@Test
	public final void testBytes2intByteByteByte() {
		byte a = 15;
		byte b = 42;
		byte c = 33;
		int act = Header.bytes2int(a,b,c);
		assertEquals(993825, act);
		assertEquals(15, ((act & 0xFF0000) >> 16));
		assertEquals(42, ((act & 0x00FF00) >> 8));
		assertEquals(33, (act & 0x0000FF));
	}

	@Test
	public final void testBytes2intByteByteByteByte() {
		byte a = 15;
		byte b = 42;
		byte c = 33;
		byte d = 64;
		int act = Header.bytes2int(a,b,c,d);
		assertEquals(254419264, act);
		assertEquals(15, ((act & 0xFF000000) >> 24));
		assertEquals(42, ((act & 0x00FF0000) >> 16));
		assertEquals(33, ((act & 0x0000FF00) >> 8));
		assertEquals(64, (act& 0x000000FF));
	}

	@Test
	public final void testMergeArrays() {
		fail("Not yet implemented"); // TODO
	}

}
