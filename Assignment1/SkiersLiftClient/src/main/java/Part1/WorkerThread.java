package Part1;

import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkerThread implements Runnable {
    private final BlockingQueue<String[]> reqQueue;
    private final BlockingQueue<String[]> metricsQueue;
    private final int numOfRequests;
    private final int MAX_RETRIES = 5;
    private final AtomicInteger successfulRequestCount;

    public WorkerThread(BlockingQueue<String[]> reqQueue, BlockingQueue<String[]> metricsQueue, int numOfRequests, AtomicInteger successfulRequestCount) {
        this.reqQueue = reqQueue;
        this.metricsQueue = metricsQueue;
        this.numOfRequests = numOfRequests;
        this.successfulRequestCount = successfulRequestCount;
    }

    @Override
    public void run() {
        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setRetryStrategy(new DefaultHttpRequestRetryStrategy(MAX_RETRIES, TimeValue.ofSeconds(1)) {
                    @Override
                    public boolean retryRequest(HttpResponse response, int executionCount, HttpContext context) {
                        return executionCount <= MAX_RETRIES && response != null && response.getCode() >= 400;
                    }
                })
                .build()) {

            for (int i = 0; i < numOfRequests; i++) {
                String[] requestData = reqQueue.poll();

                long startTime = System.currentTimeMillis();
                assert requestData != null;
                int responseCode = sendPostRequest(httpClient, requestData[0], requestData[1]);
                long endTime = System.currentTimeMillis();
                long latency = endTime - startTime;
                metricsQueue.put(new String[]{String.valueOf(startTime), "POST", String.valueOf(latency), String.valueOf(responseCode)});
            }
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private int sendPostRequest(CloseableHttpClient httpClient, String url, String jsonPayload) throws IOException {
        return httpClient.execute(
                ClassicRequestBuilder.post(url)
                        .setHeader("Content-Type", "application/json")
                        .setEntity(new StringEntity(jsonPayload, StandardCharsets.UTF_8))
                        .build(),
                response -> {
                    int responseCode = response.getCode();
                    if (responseCode == 201) {
                        successfulRequestCount.incrementAndGet();
                    } else {
                        System.err.println("Request failed with status: " + responseCode);
                    }
                    return responseCode;
                });
    }
}
