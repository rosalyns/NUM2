package tests;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;


import java.util.Arrays;

import general.Config;
import general.Task;

public class TaskTest {
	private Task storeTask;
	private Task sendTask;

	@Before
	public void setUp() throws Exception {
		storeTask = new Task(Task.Type.STORE_ON_CLIENT, "testoutputfile1.png", null, null, 0, 248);
		sendTask = new Task(Task.Type.SEND_FROM_CLIENT, "file1.png", null, null, 0, 248);
		sendTask.acked(2);
		sendTask.acked(3);
		sendTask.acked(4);
	}

	@Test
	public void testInReceivingWindow() {
		byte[] data1 = new byte[4];
		Arrays.fill(data1, (byte) 3);
		storeTask.addContent(2, data1);
		storeTask.addContent(3, data1);
		storeTask.addContent(4, data1);
		storeTask.addContent(5, data1);
		storeTask.addContent(6, data1);
		
		assertFalse(storeTask.inReceivingWindow(6));
		assertTrue(storeTask.inReceivingWindow(7));
		
		assertTrue(storeTask.inReceivingWindow(6+Config.RWS));
		assertFalse(storeTask.inReceivingWindow(7+Config.RWS));
		
	}

	@Test
	public void testNextExpectedPacket() {
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
	public void testNextExpectedAck() {
		assertEquals(5, sendTask.nextExpectedAck());
	}

	@Test
	public void testAddContent() {
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
	public void testTimeoutElapsed() {
		fail("Not yet implemented"); // TODO write test
	}

	@Test
	public void testSetAndGetTaskId() {
		sendTask.setId(5);
		assertEquals(5, sendTask.getTaskId());
	}

	@Test
	public void testGetType() {
		assertEquals(Task.Type.SEND_FROM_CLIENT, sendTask.getType());
		assertEquals(Task.Type.STORE_ON_CLIENT, storeTask.getType());
	}

	@Test
	public void testGetTotalFileSize() {
		assertEquals(248, sendTask.getTotalFileSize());
	}
	
	@Test
	public void testGetCurrentFileSize() {
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
	public void testFinished() {
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
