package tests;

import static org.junit.jupiter.api.Assertions.*;

import java.net.DatagramSocket;
import java.net.InetAddress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import general.Task;

class TaskTest {
	private Task downTask;
	private Task upTask;

	@BeforeEach
	void setUp() throws Exception {
		//public Task(Task.Type type, String fileName, DatagramSocket sock, InetAddress addr, int port, int fileSize) {
		downTask = new Task(Task.Type.DOWNLOAD, "file1.png", null, null, 0, 248);
		downTask.acked(0);
		downTask.acked(1);
		downTask.acked(2);
	}

	@Test
	final void testInReceivingWindow() {
		fail("Not yet implemented"); // TODO
	}

	@Test
	final void testNextReceivingPacket() {
		assertEquals(3, downTask.nextReceivingPacket());
	}

	@Test
	final void testAddContent() {
		fail("Not yet implemented"); // TODO
	}

	@Test
	final void testTimeoutElapsed() {
		fail("Not yet implemented"); // TODO
	}

	@Test
	final void testGetTaskId() {
		fail("Not yet implemented"); // TODO
	}

	@Test
	final void testGetType() {
		fail("Not yet implemented"); // TODO
	}

	@Test
	final void testGetTotalFileSize() {
		fail("Not yet implemented"); // TODO
	}

}
