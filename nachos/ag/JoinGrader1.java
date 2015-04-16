package nachos.ag;

import nachos.machine.Machine;
import nachos.threads.KThread;
import nachos.threads.RoundRobinScheduler;
import nachos.threads.ThreadedKernel;

public class JoinGrader1 extends BasicTestGrader
{
  static StringBuffer buf = null;
  
  void run ()
  {
    assertTrue(ThreadedKernel.scheduler instanceof RoundRobinScheduler,
      "this test requires roundrobin scheduler");
    
    /* JoinGrader1.a */
    buf = new StringBuffer();
    ThreadHandler t1 = forkNewThread(new PingTest(1, 3));
    forkNewThread(new PingTest(0, 5));
    t1.thread.join();
    while (buf.length() < 8)
    {
      assertTrue(Machine.timer().getTime() < 1500,
        "Too many ticks wasted on \nTest JoinGrader1.a");
      System.out.println(buf.toString());
      KThread.yield();
    }
    assertTrue(buf.toString().equals("10101000"),
      "sequence error in execution");
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