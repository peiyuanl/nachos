package nachos.threads;

import nachos.machine.*;
import java.util.ArrayList;
/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	private static ArrayList<waitingThreads> waitingList = new ArrayList<waitingThreads>();
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt()
	{
		/*
		KThread.currentThread().yield();
		*/
		int i = 0;
		while(i < waitingList.size())
		{
			if(waitingList.get(i).wakeTime <= Machine.timer().getTime())
			{
				boolean currStatus = Machine.interrupt().disable();
				waitingList.get(i).thread.ready();
				waitingList.remove(i);
				Machine.interrupt().restore(currStatus);
				continue;
			}
			i++;
		}
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		/*
		long wakeTime = Machine.timer().getTime() + x;
		while (wakeTime > Machine.timer().getTime())
			KThread.yield();
		*/
		long wakeTime = Machine.timer().getTime() + x;
		boolean currStatus = Machine.interrupt().disable();
		waitingList.add(new waitingThreads(wakeTime, KThread.currentThread()));
		KThread.sleep();
		Machine.interrupt().restore(currStatus);

	}
	/*
	public static void selfTest() {
		AlarmTest.runTest();
	}
	*/
	public class waitingThreads{
		public long wakeTime;
		public KThread thread;

		waitingThreads(long wakeTime, KThread thread)
		{
			this.wakeTime = wakeTime;
			this.thread = thread;
		}

	}
}

