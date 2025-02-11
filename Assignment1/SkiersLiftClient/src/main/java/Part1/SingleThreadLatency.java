package Part1;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SingleThreadLatency {
    private static final String SERVER_URL = "http://localhost:8080/skiers_api_server_war_exploded/";
    private static final int REQUESTS_PER_THREAD = 10000;
    private static final int SINGLE_THREAD = 1;
    private static final BlockingQueue<String[]> reqQueue = new LinkedBlockingQueue<>();
    private static final BlockingQueue<String[]> metricsQueue = new LinkedBlockingQueue<>();
    private static final AtomicInteger successfulRequestCount = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        Thread generatorThread = new Thread(new RideEventGenerator(reqQueue, REQUESTS_PER_THREAD, SERVER_URL));
        generatorThread.start();
        generatorThread.join();

        ExecutorService executor = Executors.newFixedThreadPool(SINGLE_THREAD);
        long startTime = System.currentTimeMillis();

        executor.execute(new WorkerThread(reqQueue, metricsQueue, REQUESTS_PER_THREAD, successfulRequestCount));
        executor.shutdown();

        if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
            System.err.println("Executor did not terminate. Force shutdown.");
            executor.shutdownNow();
        }
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        System.out.println("Total requests: " + REQUESTS_PER_THREAD);
        System.out.println("Successful requests: " + successfulRequestCount.get());
        System.out.println("Unsuccessful requests: " + (REQUESTS_PER_THREAD - successfulRequestCount.get()));
        System.out.println("Total time (ms): " + totalTime);
        System.out.println("Throughput (requests/sec): " + (successfulRequestCount.get() / (totalTime / 1000.0)));
    }
}
