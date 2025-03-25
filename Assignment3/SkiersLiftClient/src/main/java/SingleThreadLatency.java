import Part1.RideEventGenerator;
import Part1.WorkerThread;
import Part2.PerformanceAnalyzer;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SingleThreadLatency {
    private static final String SERVER_URL = "http://lb-639830833.us-west-2.elb.amazonaws.com:8080/skiers-api-server_war/";
//    private static final String SERVER_URL = "http://52.39.54.249:8080/skiers-api-server_war/";
//    private static final String SERVER_URL = "http://localhost:8080/skiers_api_server_war_exploded/";
    private static final int REQUESTS_PER_THREAD = 10000;
    private static final int SINGLE_THREAD = 1;
    private static final BlockingQueue<String[]> reqQueue = new LinkedBlockingQueue<>();
    private static final BlockingQueue<String[]> metricsQueue = new LinkedBlockingQueue<>();
    private static final AtomicInteger successfulRequestCount = new AtomicInteger(0);
    private static final String CSV_PATH = "src/main/java/Part2/test_result.csv";
    private static final String IMG_PATH = "src/main/java/Part2/test_plot.png";
    private static final String PLOT_TITLE = "Throughput Over Time";

    public static void main(String[] args) throws InterruptedException {
        Thread generatorThread = new Thread(new RideEventGenerator(reqQueue, REQUESTS_PER_THREAD, SERVER_URL));
        generatorThread.start();
        generatorThread.join();

        ExecutorService executor = Executors.newFixedThreadPool(SINGLE_THREAD);
        long startTime = System.currentTimeMillis();

        executor.submit(new WorkerThread(reqQueue, metricsQueue, REQUESTS_PER_THREAD, successfulRequestCount));
        executor.shutdown();

        if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
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
        System.out.println();

        ExecutorService metricsExecutor = Executors.newSingleThreadExecutor();
        metricsExecutor.submit(() -> PerformanceAnalyzer.processMetrics(CSV_PATH, metricsQueue, IMG_PATH, PLOT_TITLE));
        metricsExecutor.shutdown();
        if (!metricsExecutor.awaitTermination(5, TimeUnit.MINUTES)) {
            System.err.println("Executor did not terminate. Force shutdown.");
            metricsExecutor.shutdownNow();
        }
    }
}
