package Client;

import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.ApiResponse;
import io.swagger.client.api.SkiersApi;
import io.swagger.client.model.LiftRide;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class PostingSkiInfo implements Runnable {
  private final BlockingQueue<LiftRide> queue;
  private final int numRequests;
  private final AtomicInteger successfulRequests;
  private final AtomicInteger failedRequests;
  private final CountDownLatch count;
  private final List<Long> responseTimes;

  private final Random random = new Random();
  private static final int MAX_RETRIES = 5;
  private static final int RESORT_ID = 1;
  private static final int SKIER_ID_RANGE = 100000;
  private static final String SEASON_ID = "2025";
  private static final String DAY_ID = "1";

  public PostingSkiInfo(BlockingQueue<LiftRide> queue, int numRequests,
      AtomicInteger successfulRequests, AtomicInteger failedRequests,
      CountDownLatch count, List<Long> responseTimes) {
    this.queue = queue;
    this.numRequests = numRequests;
    this.successfulRequests = successfulRequests;
    this.failedRequests = failedRequests;
    this.count = count;
    this.responseTimes = responseTimes;
  }

  @Override
  public void run() {
    SkiersApi skiersApi = createApiClient();

    for (int i = 0; i < numRequests; i++) {
      LiftRide liftRide = queue.poll();
      if (liftRide == null) break;

      boolean success = false;
      int retries = 0;
      long startTime, endTime;

      while (!success && retries < MAX_RETRIES) {
        startTime = System.currentTimeMillis();
        try {
          ApiResponse<Void> apiResponse = skiersApi.writeNewLiftRideWithHttpInfo(
              liftRide, RESORT_ID, SEASON_ID, DAY_ID,
              random.nextInt(SKIER_ID_RANGE) + 1
          );
          endTime = System.currentTimeMillis();
          responseTimes.add(endTime - startTime);

          if (apiResponse.getStatusCode() == 201) {
            successfulRequests.incrementAndGet();
            success = true;
          } else {
            retries++;
            System.err.println("Retry " + retries + " for request: " + liftRide);
          }
        } catch (ApiException e) {
          System.err.println("API Exception on attempt " + (retries + 1) + ": " + e.getMessage());
          retries++;
        } finally {
          this.count.countDown();
        }
      }

      if (!success) {
        failedRequests.incrementAndGet();
      }
    }
  }

  private SkiersApi createApiClient() {
    ApiClient apiClient = new ApiClient();
    apiClient.setBasePath("http://34.225.55.133:8080/Assignment4/");
    return new SkiersApi(apiClient);
  }
}