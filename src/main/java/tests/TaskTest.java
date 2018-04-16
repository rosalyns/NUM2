package tests;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;


import java.util.Arrays;


import general.Task;

class TaskTest {
	private Task storeTask;
	private Task sendTask;

	@Before
	void setUp() throws Exception {
		storeTask = new Task(Task.Type.STORE_ON_CLIENT, "testoutputfile1.png", null, null, 0, 248);
		sendTask = new Task(Task.Type.SEND_FROM_CLIENT, "file1.png", null, null, 0, 248);
		storeTask.acked(4);
		storeTask.acked(5);
		storeTask.acked(6);
	}

	@Test
	final void testInReceivingWindow() {
		byte[] data1 = new byte[4];
		Arrays.fill(data1, (byte) 3);
		storeTask.addContent(2, data1);
		storeTask.addContent(3, data1);
		storeTask.addContent(4, data1);
		storeTask.addContent(5, data1);
		storeTask.addContent(6, data1);
		
		assertFalse(storeTask.inReceivingWindow(6));
		assertTrue(storeTask.inReceivingWindow(7));
		
		assertTrue(storeTask.inReceivingWindow(26));
		assertFalse(storeTask.inReceivingWindow(27));
		
		
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
		storeTask.addContent(2, data1);
		storeTask.addContent(3, data2);
		storeTask.addContent(4, data3);
		assertEquals(5, storeTask.nextExpectedPacket());
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
		storeTask.addContent(2, data1);
		assertEquals(4, storeTask.getCurrentFileSize());
		storeTask.addContent(3, data1);
		assertEquals(8, storeTask.getCurrentFileSize());
		storeTask.addContent(4, data1);
		assertEquals(12, storeTask.getCurrentFileSize());
	}

	@Test
	final void testTimeoutElapsed() {
		fail("Not yet implemented"); // TODO write test
	}

	@Test
	final void testSetAndGetTaskId() {
		sendTask.setId(5);
		assertEquals(5, sendTask.getTaskId());
	}

	@Test
	final void testGetType() {
		assertEquals(Task.Type.SEND_FROM_CLIENT, sendTask.getType());
		assertEquals(Task.Type.STORE_ON_CLIENT, storeTask.getType());
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
		storeTask.addContent(2, data1);
		assertEquals(4, storeTask.getCurrentFileSize());
		storeTask.addContent(3, data1);
		assertEquals(8, storeTask.getCurrentFileSize());
		storeTask.addContent(4, data1);
		assertEquals(12, storeTask.getCurrentFileSize());
	}
	
	@Test
	final void testFinished() {
		Task task = new Task(Task.Type.STORE_ON_CLIENT, "testoutputfile2.png", null, null, 0, 248);
		byte[] data = new byte[200];
		byte[] data2 = new byte[48];
		Arrays.fill(data, (byte)5);
		Arrays.fill(data2, (byte)6);
		task.addContent(2, data);
		task.addContent(3, data2);
		assertTrue(task.finished());
	}

}
