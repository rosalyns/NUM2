package tests;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import general.Task;

class TaskTest {
	private Task storeTask;
	private Task sendTask;

	@BeforeEach
	void setUp() throws Exception {
		//public Task(Task.Type type, String fileName, DatagramSocket sock, InetAddress addr, int port, int fileSize) {
		storeTask = new Task(Task.Type.STORE_FILE, "testoutputfile1.png", null, null, 0, 248);
		sendTask = new Task(Task.Type.SEND_FILE, "file1.png", null, null, 0, 248);
		storeTask.acked(0);
		storeTask.acked(1);
		storeTask.acked(2);
	}

	@Test
	final void testInReceivingWindow() {
		assertTrue(storeTask.inReceivingWindow(21));
	}

	@Test
	final void testNextExpectedPacket() {
		//first addContent
		byte[] data1 = new byte[4];
		byte[] data2 = new byte[4]; 
		byte[] data3 = new byte[4]; 
		Arrays.fill(data1, (byte) 3);
		Arrays.fill(data2, (byte) 3);
		Arrays.fill(data3, (byte) 3);
		storeTask.addContent(0, data1);
		storeTask.addContent(1, data2);
		storeTask.addContent(2, data3);
		assertEquals(3, storeTask.nextExpectedPacket());
	}
	
	@Test
	final void testNextExpectedAck() {
		assertEquals(3, storeTask.nextExpectedAck());
	}

	@Test
	final void testAddContent() {
		byte[] data1 = new byte[4];
		Arrays.fill(data1, (byte) 3);
		assertEquals(0, storeTask.getCurrentFileSize());
		storeTask.addContent(0, data1);
		assertEquals(4, storeTask.getCurrentFileSize());
		storeTask.addContent(1, data1);
		assertEquals(8, storeTask.getCurrentFileSize());
		storeTask.addContent(2, data1);
		assertEquals(12, storeTask.getCurrentFileSize());
	}

	@Test
	final void testTimeoutElapsed() {
		fail("Not yet implemented"); // TODO
	}

	@Test
	final void testSetAndGetTaskId() {
		sendTask.setId(5);
		assertEquals(5, sendTask.getTaskId());
	}

	@Test
	final void testGetType() {
		assertEquals(Task.Type.SEND_FILE, sendTask.getType());
		assertEquals(Task.Type.STORE_FILE, storeTask.getType());
	}

	@Test
	final void testGetTotalFileSize() {
		assertEquals(248, sendTask.getTotalFileSize());
	}
	
	@Test
	final void testGetCurrentFileSize() {
		byte[] data1 = new byte[4];
		Arrays.fill(data1, (byte) 3);
		assertEquals(0, storeTask.getCurrentFileSize());
		storeTask.addContent(0, data1);
		assertEquals(4, storeTask.getCurrentFileSize());
		storeTask.addContent(1, data1);
		assertEquals(8, storeTask.getCurrentFileSize());
		storeTask.addContent(2, data1);
		assertEquals(12, storeTask.getCurrentFileSize());
	}
	
	@Test
	final void testFinished() {
		Task task = new Task(Task.Type.STORE_FILE, "testoutputfile2.png", null, null, 0, 248);
		byte[] data = new byte[200];
		byte[] data2 = new byte[48];
		Arrays.fill(data, (byte)5);
		Arrays.fill(data2, (byte)6);
		task.addContent(0, data);
		task.addContent(1, data2);
		assertTrue(task.finished());
	}

}
