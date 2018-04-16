package tests;
import static org.junit.Assert.*;

import java.io.File;

import org.junit.Before;
import org.junit.Test;

import general.Config;
import general.Utils;

public class UtilsTest {

	@Before
	public void setUp() throws Exception {
		
	}

	@Test
	public void testGetFileContentsByte() {
		byte[] file1 = Utils.getFileContents("files/file5.png", 0);
		assertEquals(Config.DATASIZE, file1.length);
		byte[] file2 = Utils.getFileContents("files/file5.png", Config.DATASIZE);
		assertEquals(file1.length, file2.length);
		byte[] file3 = Utils.getFileContents("files/file5.png", 2*Config.DATASIZE);
		
		byte[] file1and2 = Utils.mergeArrays(file1, file2);
		byte[] file2and3 = Utils.mergeArrays(file2, file3);
		assertEquals(2 * Config.DATASIZE, file1and2.length);
		assertEquals(2 * Config.DATASIZE, file2and3.length);
	}
	
	@Test
	public void testGetFileContentsByteEndOfFile() {
		File file = new File("files/file5.png");
		byte[] file1 = Utils.getFileContents("files/file5.png", (int) file.length() - 100);
		assertEquals(100, file1.length);
	}

	@Test
	public void testGetFileSize() {
		assertEquals(248, Utils.getFileSize("files/file1.png"));
		assertEquals(2085, Utils.getFileSize("files/file2.png"));
		assertEquals(6267, Utils.getFileSize("files/file3.png"));
		assertEquals(21067, Utils.getFileSize("files/file4.png"));
		assertEquals(53228, Utils.getFileSize("files/file5.png"));
		assertEquals(216583872, Utils.getFileSize("files/grand_tour.tif"));
	}

	@Test
	public void testMergeArrays() {
		byte[] file1 = Utils.getFileContents("files/file5.png", 0);
		byte[] file2 = Utils.getFileContents("files/file5.png", Config.DATASIZE);
		
		assertNotEquals(Utils.mergeArrays(file1,file2), Utils.mergeArrays(file2, file1));
		
		byte[] data1 = new byte[] { 1,2 };
		byte[] data2 = new byte[] { 3,4 } ;
		byte[] result = Utils.mergeArrays(data1, data2);
		
		assertEquals(1, result[0]);
		assertEquals(2, result[1]);
		assertEquals(3, result[2]);
		assertEquals(4, result[3]);
		assertEquals(4, result.length);
		
	}

	@Test
	public void testSetFileContents() {
		fail("Not yet implemented"); // TODO write test
	}

}

