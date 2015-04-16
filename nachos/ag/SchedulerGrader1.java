package nachos.ag;

import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.threads.KThread;
import nachos.threads.PriorityScheduler;
import nachos.threads.ThreadedKernel;

public class SchedulerGrader1 extends BasicTestGrader
{
	static int total = 0;
	static int count = 0;
	Set<ThreadHandler> set = new HashSet<ThreadHandler>();
  	StringBuffer buf, refBuf;

	public void run () {
		assertTrue(ThreadedKernel.scheduler instanceof PriorityScheduler,
	  		"this test requires priority scheduler");
	
		total = 50;
		count = 0;
		set.clear();
		buf = new StringBuffer();
		refBuf = new StringBuffer();
		for (int i = 0; i < total; ++i) {
			int p = Lib.random(PriorityScheduler.priorityMaximum + 1);
	  		set.add(forkNewThread(new a(p), p));
	  		refBuf.append(p);
		}
		
		for (ThreadHandler t : set)
			t.thread.join();

		char[] chars = refBuf.toString().toCharArray();
		Arrays.sort(chars);
		refBuf = new StringBuffer(new String(chars)).reverse();
		assertTrue(refBuf.toString().equals(buf.toString()),
			"incorrect sequence" + " Ref: " + new String(chars) +
			" Output: " + buf.toString());
	
		done();
	}
  
	private class a implements Runnable {
		int which;
		public a(int which) {
			this.which = which;
		}
		public void run () {
			buf.append(which);
			KThread.yield();
		}
	}
}