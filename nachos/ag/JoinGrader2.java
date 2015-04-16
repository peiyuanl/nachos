package nachos.ag;

import nachos.machine.Machine;
import nachos.threads.KThread;
import nachos.threads.RoundRobinScheduler;
import nachos.threads.ThreadedKernel;

public class JoinGrader2 extends BasicTestGrader
{
	static StringBuffer buf = null;
	
	void run ()
	{
		assertTrue(ThreadedKernel.scheduler instanceof RoundRobinScheduler,
			"this test requires roundrobin scheduler");
		
		/* JoinGrader2.a */
		buf = new StringBuffer();
		ThreadHandler t1 = forkNewThread(new PingTest(1, 3));
		ThreadHandler t2 = forkNewThread(new PingTest(2, 5));
		forkNewThread(new PingTest(0, 1));
		t1.thread.join(); t2.thread.join();
		assertTrue(buf.toString().equals("120121222"),
			"sequence error in execution" + buf.toString());

		/* JoinGrader2.a */
		buf = new StringBuffer();
		t1 = forkNewThread(new PingTest(1, 3));
		t2 = forkNewThread(new PingTest(2, 5));
		forkNewThread(new PingTest(0, 3));
		t1.thread.join(); t2.thread.join();
		assertTrue(buf.toString().equals("12012012022"),
			"sequence error in execution" + buf.toString());
		done();
	}
	
	private static class PingTest implements Runnable
	{
		PingTest (int which, int iter)
		{
			this.which = which;
			this.iter = iter;
		}
		
		public void run ()
		{
			for (int i = 0; i < iter; i++)
			{
				buf.append(which);
				KThread.yield();
			}
		}
		
		private int which;
		private int iter;
	}
}