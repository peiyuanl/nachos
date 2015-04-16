package nachos.ag;

import java.util.Vector;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.threads.Communicator;
import nachos.threads.KThread;
import nachos.threads.RoundRobinScheduler;
import nachos.threads.ThreadedKernel;

public class CommunicatorGrader2 extends BasicTestGrader
{
	static int count = 0;
	static Vector<Integer> list = new Vector<Integer>();

	private Communicator com;	
	public void run () {
		assertTrue(ThreadedKernel.scheduler instanceof RoundRobinScheduler,
			"this test requires roundrobin scheduler");
		com = new Communicator();

		ListenerThenSpeaker(1, 2000);
		ListenerThenSpeaker(3, 2000);
		ListenerThenSpeaker(10, 3000);
		ListenerThenSpeaker(20, 8000);
		ListenerThenSpeaker(50, 30000);
		done();
	}

	/* Forks all the speakers at once then forks listeners */
	private void ListenerThenSpeaker(int speakers, int time) {
		System.out.println("Forking " + speakers + " Listeners then Speakers");
		count = 0; 
		list.clear();
		for (int i = 0; i < speakers; i++) {
			forkNewThread(new Listener());
		}
		for (int i = 0; i < speakers; i++) {
			forkNewThread(new Speaker(i));
		}
		while (count != speakers) {
			assertTrue(Machine.timer().getTime() < time,
				"Too many ticks wasted on running " + speakers +
				" Speakers then same listeners");
			KThread.yield();
		}	
	}	
	private class Speaker implements Runnable
	{
    	int word;
    	public Speaker (int word) {
    		this.word = word;
    	}
    	public void run () {
    		list.add(word);
    		//System.out.println(KThread.currentThread() + " say " + word);
    		com.speak(word);
    	}
    }
    private class Listener implements Runnable
    {
    	public void run () {
    		int w = com.listen();
    		assertTrue(list.contains(new Integer(w)), "unknown message received");
    		list.remove(new Integer(w));
    		//System.out.println(KThread.currentThread() + " listened " + w);
    		++count;
    	}
    }
}