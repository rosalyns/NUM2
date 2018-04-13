package tests;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import general.Config;
import general.Utils;

class UtilsTest {

	@BeforeEach
	void setUp() throws Exception {
		
	}

	@Test
	final void testGetFileContentsInteger() {
		fail("Not yet implemented"); // TODO
	}

	@Test
	final void testGetFileContentsByte() {
		byte[] file1 = Utils.getFileContents("file5.png", 0);
		assertEquals(Config.DATASIZE, file1.length);
		byte[] file2 = Utils.getFileContents("file5.png", Config.DATASIZE);
		assertEquals(file1.length, file2.length);
		
		Integer[] wholeFile = Utils.getFileContents("file5.png");
		byte[] file = new byte[wholeFile.length];
		for (int i = 0; i < wholeFile.length; i++) {
			file[i] = (byte) (int)wholeFile[i];
		}
		
		byte[] file1and2 = Utils.mergeArrays(file1, file2);
		assertEquals(2 * Config.DATASIZE, file1and2.length);
		byte[] copyOfFile = new byte[2 * Config.DATASIZE];

		assertEquals(file1and2.length, copyOfFile.length);
		
		System.arraycopy(file, 0, copyOfFile, 0, 2*Config.DATASIZE);
		
		for (int i = 0; i < file1and2.length; i++) {
			assertEquals(file1and2[i], copyOfFile[i]);
		}
	}
	
	@Test
	final void testGetFileContentsByteEndOfFile() {
		File file = new File("file5.png");
		byte[] file1 = Utils.getFileContents("file5.png", (int) file.length() - 100);
		assertEquals(100, file1.length);
	}

	@Test
	final void testGetFileSize() {
		fail("Not yet implemented"); // TODO
	}

	@Test
	final void testMergeArrays() {
		fail("Not yet implemented"); // TODO
	}

	@Test
	final void testSetFileContents() {
		fail("Not yet implemented"); // TODO
	}

}
