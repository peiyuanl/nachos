package nachos.ag;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.threads.KThread;
import nachos.threads.RoundRobinScheduler;
import nachos.threads.ThreadedKernel;

public class AlarmGrader1 extends BasicTestGrader {
	static StringBuffer buf = null;

	public void run() {
		assertTrue(ThreadedKernel.scheduler instanceof RoundRobinScheduler,
				"this test requires roundrobin scheduler");
		buf = new StringBuffer();
		long time = Machine.timer().getTime();
		forkNewThread(new PingTest(0, time + 10100));
		forkNewThread(new PingTest(1, time + 9100));
		forkNewThread(new PingTest(2, time + 8900));
		forkNewThread(new PingTest(3, time + 8800));
		forkNewThread(new PingTest(4, time + 4900));

		while (buf.length() < 5)
			KThread.yield();

		assertTrue(buf.toString().equals("43210"),
			"sequence error in execution");
		done();
	}

	private static class PingTest implements Runnable {
		PingTest (int which, long wakeTime) {
			this.which = which;
			this.wakeTime = wakeTime;
		}
    
		public void run () {
			ThreadedKernel.alarm.waitUntil(wakeTime);
			buf.append(which);
		}
	    private int which;
    	private long wakeTime;
    }
}