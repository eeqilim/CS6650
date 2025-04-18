package Client;

import io.swagger.client.model.LiftRide;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MultiThreadLiftRideClient {
  private static final int TOTAL_REQUESTS = 200000;
  private static final int INITIAL_THREADS = 200;
  private static final int REQUESTS_PER_THREAD = 1000;

  private static final BlockingQueue<LiftRide> GeneratorQueue = new LinkedBlockingQueue<>(TOTAL_REQUESTS);
  private static final AtomicInteger successfulRequests = new AtomicInteger(0);
  private static final AtomicInteger failedRequests = new AtomicInteger(0);

  private static final List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());

  public static void main(String[] args) throws InterruptedException {
    // Start LiftRide event generation
    Thread eventGeneratorThread = new Thread(new SkierEventGenerator(GeneratorQueue, TOTAL_REQUESTS));
    eventGeneratorThread.start();

    long startTime = System.currentTimeMillis();

    // Create ThreadPoolExecutor
    ThreadPoolExecutor executor = new ThreadPoolExecutor(
        INITIAL_THREADS,
        INITIAL_THREADS,
        5000L,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(REQUESTS_PER_THREAD * INITIAL_THREADS),
        new ThreadPoolExecutor.CallerRunsPolicy()
    );

    CountDownLatch completed = new CountDownLatch(TOTAL_REQUESTS);

    for (int i = 0; i < TOTAL_REQUESTS / REQUESTS_PER_THREAD; i++) {
      executor.execute(new PostingSkiInfo(GeneratorQueue, REQUESTS_PER_THREAD, successfulRequests, failedRequests, completed, responseTimes));
    }

    // Wait for all threads to finish
    try {
      completed.await();
    } catch (InterruptedException e) {
      System.out.println("Main thread was interrupted");
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }

    // Print results
    long totalTime = System.currentTimeMillis() - startTime;
    System.out.println("Successful requests: " + successfulRequests.get());
    System.out.println("Failed requests: " + failedRequests.get());
    System.out.println("Total time: " + totalTime + " ms");
    System.out.println("Throughput: " + (TOTAL_REQUESTS / (totalTime / 1000.0)) + " requests/second");

    // Print latency metrics
    printLatencyMetrics(responseTimes);

    executor.shutdown();
  }

  private static void printLatencyMetrics(List<Long> responseTimes) {
    if (responseTimes.isEmpty()) {
      System.out.println("No response time data collected.");
      return;
    }

    Collections.sort(responseTimes);
    int size = responseTimes.size();

    long min = responseTimes.get(0);
    long max = responseTimes.get(size - 1);
    double mean = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
    long median = responseTimes.get(size / 2);
    long p95 = responseTimes.get((int) (size * 0.95));
    long p99 = responseTimes.get((int) (size * 0.99));

    System.out.println("Response Time Metrics (ms):");
    System.out.println("Min: " + min + " ms");
    System.out.println("Max: " + max + " ms");
    System.out.println("Mean: " + mean + " ms");
    System.out.println("Median: " + median + " ms");
    System.out.println("P95: " + p95 + " ms");
    System.out.println("P99: " + p99 + " ms");
  }
}