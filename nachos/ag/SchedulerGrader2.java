package nachos.ag;

import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.threads.Lock;
import nachos.threads.Communicator;
import nachos.threads.KThread;
import nachos.threads.PriorityScheduler;
import nachos.threads.ThreadedKernel;

public class SchedulerGrader2 extends BasicTestGrader
{
	Lock lock;
	int received = -1;

	public void run () {
		assertTrue(ThreadedKernel.scheduler instanceof PriorityScheduler,
	  		"this test requires priority scheduler");
	
		lock = new Lock();

		forkNewThread(new sender(), 2);
		forkNewThread(new receiver(), 3);

		while (received == -1) {		
			assertTrue(Machine.timer().getTime() < 2000, 
				"waited for too long.");
			KThread.yield();
		}
		done();
	}
  
	private class sender implements Runnable {
		public void run () {
			lock.acquire();
			received = 123;
			lock.release();
		}
	}

	private class receiver implements Runnable {
		public void run() {
			while (received == -1) {
				KThread.yield();
			}
		}
	}
}