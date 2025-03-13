import Part1.RideEventGenerator;
import Part1.WorkerThread;
import Part2.PerformanceAnalyzer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    private static final String SERVER_URL = "http://54.200.110.175:8080/skiers-api-server_war/";
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

        ExecutorService initialExecutor = Executors.newFixedThreadPool(INITIAL_THREAD_COUNT);
        CountDownLatch initialLatch = new CountDownLatch(INITIAL_THREAD_COUNT);
        long startTime = System.currentTimeMillis();
        executeWorkerThreads(initialExecutor, initialLatch, INITIAL_THREAD_COUNT, REQUESTS_PER_INITIAL_THREAD);

        initialLatch.await();
        initialExecutor.shutdown();

        ExecutorService secondaryExecutor = Executors.newFixedThreadPool(SECOND_PHASE_THREAD_COUNT);
        CountDownLatch secondaryLatch = new CountDownLatch(SECOND_PHASE_THREAD_COUNT);
        executeWorkerThreads(secondaryExecutor, secondaryLatch, SECOND_PHASE_THREAD_COUNT, REQUESTS_PER_SECOND_PHASE_THREAD);

        secondaryLatch.await();
        secondaryExecutor.shutdown();

        if (!initialExecutor.awaitTermination(5, TimeUnit.MINUTES)) {
            System.err.println("Executor did not terminate. Force shutdown.");
            initialExecutor.shutdownNow();
        }

        if (!secondaryExecutor.awaitTermination(5, TimeUnit.MINUTES)) {
            System.err.println("Executor did not terminate. Force shutdown.");
            secondaryExecutor.shutdownNow();
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

    private static void executeWorkerThreads(ExecutorService executor, CountDownLatch latch, int threadCount, int requestsPerThread) {
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    new WorkerThread(reqQueue, metricsQueue, requestsPerThread, successfulRequestCount).run();
                } finally {
                    latch.countDown();
                }
            });
        }
    }
}
