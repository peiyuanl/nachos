package nachos.threads;
import nachos.ag.*;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
	/**
	 * Allocate a new communicator.
	 */
	private Lock conditionLock;
	private Condition speaker;
	private Condition listener;
	private int message;
	private boolean empty = true;
	private int numlisteners = 0;
	public Communicator() {
		this.conditionLock = new Lock();
		this.speaker = new Condition(this.conditionLock);
		this.listener = new Condition(this.conditionLock);
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 * 
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 * 
	 * @param word the integer to transfer.
	 */
	public void speak(int word) {
		this.conditionLock.acquire();
		//wait until there is listener and buffer is empty
		while(!empty || numlisteners == 0)
		{
			this.speaker.sleep();
		}
		//this.listener.wake();
		this.message = word;
		this.empty = false;
		this.conditionLock.release();
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 * 
	 * @return the integer transferred.
	 */
	public int listen() {
		this.conditionLock.acquire();

		numlisteners++;
		speaker.wake();
		while(empty)
			listener.sleep();
		int word = message;
		empty = true;
		numlisteners--;
		speaker.wake();
		this.conditionLock.release();
		return word;
	}
	public static void selfTest() {
		/*

		//new KThread(new PingTest(0)).setName("forked thread").fork();
		new KThread(new PingTest(2)).setName("forked thread").fork();
		new KThread(new PingTest(1)).setName("forked thread").fork();
		new PingTest(0).run();
		//new PingTest(2).run();
		*/
	}
}
