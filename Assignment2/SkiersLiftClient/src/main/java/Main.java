import Part1.RideEventGenerator;
import Part1.WorkerThread;
import Part2.PerformanceAnalyzer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    private static final String SERVER_URL = "http://lb-318240099.us-west-2.elb.amazonaws.com:8080/skiers-api-server_war/";
//    private static final String SERVER_URL = "http://35.91.235.118:8080/skiers-api-server_war/";
//    private static final String SERVER_URL = "http://localhost:8080/skiers_api_server_war_exploded/";
    private static final String CSV_PATH = "src/main/java/Part2/result.csv";
    private static final String IMG_PATH = "src/main/java/Part2/plot.png";
    private static final String PLOT_TITLE = "Throughput Over Time";

    private static final int TOTAL_REQUESTS = 200000;
    private static final int INITIAL_THREAD_COUNT = 32;
    private static final int REQUESTS_PER_INITIAL_THREAD = 1000;
    private static final int SECOND_PHASE_THREAD_COUNT = 168;
    private static final int REQUESTS_PER_SECOND_PHASE_THREAD = (TOTAL_REQUESTS - (INITIAL_THREAD_COUNT * REQUESTS_PER_INITIAL_THREAD)) / SECOND_PHASE_THREAD_COUNT;

    private static final BlockingQueue<String[]> reqQueue = new LinkedBlockingQueue<>();
    private static final BlockingQueue<String[]> metricsQueue = new LinkedBlockingQueue<>();
    private static final AtomicInteger successfulRequestCount = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        Thread generatorThread = new Thread(new RideEventGenerator(reqQueue, TOTAL_REQUESTS, SERVER_URL));
        generatorThread.start();
        generatorThread.join();

        ExecutorService executor = Executors.newFixedThreadPool(INITIAL_THREAD_COUNT + SECOND_PHASE_THREAD_COUNT);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < INITIAL_THREAD_COUNT; i++) {
            executor.submit(() -> new WorkerThread(reqQueue, metricsQueue, REQUESTS_PER_INITIAL_THREAD, successfulRequestCount).run());
        }

        for (int i = 0; i < SECOND_PHASE_THREAD_COUNT; i++) {
            executor.submit(() -> new WorkerThread(reqQueue, metricsQueue, REQUESTS_PER_SECOND_PHASE_THREAD, successfulRequestCount).run());
        }

        executor.shutdown();

        if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
            System.err.println("Executor did not terminate. Force shutdown.");
            executor.shutdownNow();
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        System.out.println("Client Configuration:");
        System.out.println("Initial phase thread count: " + INITIAL_THREAD_COUNT);
        System.out.println("Second phase thread count: " + SECOND_PHASE_THREAD_COUNT);
        System.out.println("Requests per initial thread: " + REQUESTS_PER_INITIAL_THREAD);
        System.out.println("Requests per second-phase thread: " + REQUESTS_PER_SECOND_PHASE_THREAD);
        System.out.println();

        System.out.println("Total requests: " + TOTAL_REQUESTS);
        System.out.println("Successful requests: " + successfulRequestCount.get());
        System.out.println("Unsuccessful requests: " + (TOTAL_REQUESTS - successfulRequestCount.get()));
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