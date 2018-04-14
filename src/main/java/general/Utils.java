package general;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import general.Config;

/**
 * Helper utilities. Supplied for convenience.
 * 
 * @author Jaco ter Braak & Frans van Dijk, Twente University
 * @version 10-02-2016
 */
public class Utils {
	private Utils() {
	}

	/**
	 * Helper method to get the current process ID
	 *
	 * @return process id
	 */
	public static int getProcessId() {
		final String jvmName = ManagementFactory.getRuntimeMXBean().getName();
		final int index = jvmName.indexOf('@');

		if (index < 1) {
			return 0;
		}

		try {
			return Integer.parseInt(jvmName.substring(0, index));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/**
	 * Gets the contents of the specified file.
	 * 
	 * @param id
	 *            the file ID
	 * @return the array of integers, representing the contents of the file to
	 *         transmit
	 */
	public static Integer[] getFileContents(String fileName) {
		File fileToTransmit = new File(String.format(fileName));
		try (FileInputStream fileStream = new FileInputStream(fileToTransmit)) {
			Integer[] fileContents = new Integer[(int) fileToTransmit.length()];

			for (int i = 0; i < fileContents.length; i++) {
				int nextByte = fileStream.read();
				if (nextByte == -1) {
					throw new Exception("File size is smaller than reported");
				}

				fileContents[i] = nextByte;
			}
			return fileContents;
		} catch (Exception e) {
			System.err.println(e.getMessage());
			System.err.println(e.getStackTrace());
			return null;
		}
	}

	public static byte[] getFileContents(String fileName, int offset) {
		File fileToTransmit = new File(String.format(fileName));
		RandomAccessFile file = null;
		try {
			file = new RandomAccessFile(fileToTransmit, "r");
			
			long datalen = Math.min((long) Config.DATASIZE, file.length() - offset);
			byte[] buf = new byte[(int)datalen];
			
			file.seek(offset);
			file.read(buf);
			
			file.close();
			return buf;
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	public static void setContents(FileOutputStream fileStream, byte[] data) {
		try {
			for (byte fileContent : data) {
				fileStream.write(fileContent);
			}
		} catch (Exception e) {
			System.err.println(e.getMessage());
			System.err.println(e.getStackTrace());
		}
	}
	
	

	/**
	 * Gets the size of the specified file.
	 * 
	 */
	public static int getFileSize(String fileName) {
		return (int) new File(String.format(fileName)).length();
	}

	/**
	 * Merge arrays
	 */
	public static byte[] mergeArrays(byte[]... arrays) {
		byte[] first = arrays[0];

		for (int i = 1; i < arrays.length; i++) {
			byte[] toAppend = arrays[i];

			int oldlength = first.length;
			int extralen = toAppend.length;
			first = Arrays.copyOf(first, oldlength + extralen);
			System.arraycopy(toAppend, 0, first, oldlength, extralen);
		}

		return first;
	}

	/**
	 * Writes the contents of the fileContents array to the specified file.
	 * 
	 * @param fileContents
	 *            the contents to write
	 * @param id
	 *            the file ID
	 */
	public static void setFileContents(byte[] fileContents, String fileName) {
		File fileToWrite = new File(String.format(fileName, Utils.getProcessId()));
		try (FileOutputStream fileStream = new FileOutputStream(fileToWrite)) {
			for (byte fileContent : fileContents) {
				fileStream.write(fileContent);
			}
		} catch (Exception e) {
			System.err.println(e.getMessage());
			System.err.println(e.getStackTrace());
		}
	}

	/**
	 * Helper class for setting timeouts. Supplied for convenience.
	 * 
	 * @author Jaco ter Braak & Frans van Dijk, Twente University
	 * @version 09-02-2016
	 */
	public static class Timeout implements Runnable {
		private static Map<Date, Map<ITimeoutEventHandler, List<Object>>> eventHandlers = new HashMap<>();
		private static Thread eventTriggerThread;
		private static boolean started = false;
		private static ReentrantLock lock = new ReentrantLock();

		/**
		 * Starts the helper thread
		 */
		public static void Start() {
			if (started)
				throw new IllegalStateException("Already started");
			started = true;
			eventTriggerThread = new Thread(new Timeout());
			eventTriggerThread.start();
		}

		/**
		 * Stops the helper thread
		 */
		public static void Stop() {
			if (!started)
				throw new IllegalStateException("Not started or already stopped");
			eventTriggerThread.interrupt();
			try {
				eventTriggerThread.join();
			} catch (InterruptedException e) {
			}
		}

		/**
		 * Set a timeout
		 * 
		 * @param millisecondsTimeout
		 *            the timeout interval, starting now
		 * @param handler
		 *            the event handler that is called once the timeout elapses
		 */
		public static void SetTimeout(long millisecondsTimeout, ITimeoutEventHandler handler, Object tag) {
			Date elapsedMoment = new Date();
			elapsedMoment.setTime(elapsedMoment.getTime() + millisecondsTimeout);

			lock.lock();
			if (!eventHandlers.containsKey(elapsedMoment)) {
				eventHandlers.put(elapsedMoment, new HashMap<>());
			}
			if (!eventHandlers.get(elapsedMoment).containsKey(handler)) {
				eventHandlers.get(elapsedMoment).put(handler, new ArrayList<>());
			}
			eventHandlers.get(elapsedMoment).get(handler).add(tag);
			lock.unlock();
		}

		/**
		 * Do not call this
		 */
		@Override
		public void run() {
			boolean runThread = true;
			ArrayList<Date> datesToRemove = new ArrayList<>();
			HashMap<ITimeoutEventHandler, List<Object>> handlersToInvoke = new HashMap<>();
			Date now;

			while (runThread) {
				try {
					now = new Date();

					// If any timeouts have elapsed, trigger their handlers
					lock.lock();

					for (Date date : eventHandlers.keySet()) {
						if (date.before(now)) {
							datesToRemove.add(date);
							for (ITimeoutEventHandler handler : eventHandlers.get(date).keySet()) {
								if (!handlersToInvoke.containsKey(handler)) {
									handlersToInvoke.put(handler, new ArrayList<>());
								}
								for (Object tag : eventHandlers.get(date).get(handler)) {
									handlersToInvoke.get(handler).add(tag);
								}
							}
						}
					}

					// Remove elapsed events
					for (Date date : datesToRemove) {
						eventHandlers.remove(date);
					}
					datesToRemove.clear();

					lock.unlock();

					// Invoke the event handlers outside of the lock, to prevent
					// deadlocks
					for (ITimeoutEventHandler handler : handlersToInvoke.keySet()) {
						handlersToInvoke.get(handler).forEach(handler::TimeoutElapsed);
					}
					handlersToInvoke.clear();

					Thread.sleep(1);
				} catch (InterruptedException e) {
					runThread = false;
				}
			}

		}
	}
}