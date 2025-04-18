package Client;

import io.swagger.client.model.LiftRide;
import java.util.Random;
import java.util.concurrent.*;


public class SkierEventGenerator implements Runnable {

  private BlockingQueue<LiftRide> queue;
  private int numEvent;
  static private final int LIFT_ID_RANGE = 40;
  static private final int TIME_RANGE = 360;

  public SkierEventGenerator(BlockingQueue<LiftRide> queue, int numEvent) {
    this.queue = queue;
    this.numEvent = numEvent;
  }

  public BlockingQueue<LiftRide> getQueue() {
    return queue;
  }

  public int getNumEvent() {
    return numEvent;
  }

  @Override
  public void run() {
    for (int i = 0; i < numEvent; i++) {
      LiftRide newLiftRide = createRandomLiftRide();
      try {
        queue.put(newLiftRide);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private LiftRide createRandomLiftRide() {
    Random random = new Random();
    LiftRide liftRide = new LiftRide();
    liftRide.setLiftID(random.nextInt(LIFT_ID_RANGE) + 1);
    liftRide.setTime(random.nextInt(TIME_RANGE) + 1);
    return liftRide;
  }
}