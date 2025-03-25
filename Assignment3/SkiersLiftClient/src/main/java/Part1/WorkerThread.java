package Part1;

import org.apache.commons.lang3.concurrent.EventCountCircuitBreaker;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkerThread implements Runnable {
    private final BlockingQueue<String[]> reqQueue;
    private final BlockingQueue<String[]> metricsQueue;
    private final int numOfRequests;
    private final AtomicInteger successfulRequestCount;
    private final EventCountCircuitBreaker circuitBreaker;

    public WorkerThread(BlockingQueue<String[]> reqQueue, BlockingQueue<String[]> metricsQueue, int numOfRequests, AtomicInteger successfulRequestCount, EventCountCircuitBreaker circuitBreaker) {
        this.reqQueue = reqQueue;
        this.metricsQueue = metricsQueue;
        this.numOfRequests = numOfRequests;
        this.successfulRequestCount = successfulRequestCount;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public void run() {
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(200)
                .setMaxConnPerRoute(100)
                .build();
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                .setResponseTimeout(Timeout.ofSeconds(5))
                .build();
        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            for (int i = 0; i < numOfRequests; i++) {
                String[] requestData = reqQueue.take();
                long startTime = System.currentTimeMillis();
                int responseCode = sendPostRequest(httpClient, requestData[0], requestData[1]);
                long endTime = System.currentTimeMillis();
                long latency = endTime - startTime;
                metricsQueue.put(new String[]{String.valueOf(startTime), "POST", String.valueOf(latency), String.valueOf(responseCode)});
                circuitBreaker.incrementAndCheckState();
            }
        } catch (InterruptedException | IOException e) {
            Thread.currentThread().interrupt();
            System.err.println("Worker thread interrupted: " + e.getMessage());
        }
    }

    private int sendPostRequest(CloseableHttpClient httpClient, String url, String jsonPayload) throws IOException {
        int retries = 0;
        while (true) {
            try {
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
            } catch (IOException e) {
                retries++;
                int MAX_RETRIES = 5;
                System.err.println("IOException occurred, retrying " + retries + " out of " + MAX_RETRIES + ": " + e.getMessage());
                if (retries >= MAX_RETRIES) throw e;
            }
        }
    }
}
