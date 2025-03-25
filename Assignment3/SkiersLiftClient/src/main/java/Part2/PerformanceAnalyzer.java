package Part2;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class PerformanceAnalyzer {
    public static void processMetrics(String csvPath, BlockingQueue<String[]> metricsQueue, String imgPath, String plotTitle) {
        long earliestStartTime = Long.MAX_VALUE;
        long latestEndTime = Long.MIN_VALUE;
        List<Long> latencies = new ArrayList<>();
        List<Long> timestamps = new ArrayList<>();

        try (FileWriter fileWriter = new FileWriter(csvPath)) {
            fileWriter.write("Start Time, Request Type, Latency, Response Code\n");

            while (true) {
                String[] metric = metricsQueue.poll(5, TimeUnit.SECONDS);
                if (metric == null) {
                    break;
                }
                long startTime = Long.parseLong(metric[0]);
                long latency = Long.parseLong(metric[2]);
                long endTime = startTime + latency;

                if (startTime < earliestStartTime) earliestStartTime = startTime;
                if (endTime > latestEndTime) latestEndTime = endTime;

                latencies.add(latency);
                timestamps.add(startTime);
                fileWriter.write(String.join(",", metric) + "\n");
                fileWriter.flush();
            }
            calculateMetrics(latencies, earliestStartTime, latestEndTime, latencies.size());
            ThroughputPlotter.plotThroughput(plotTitle, timestamps, imgPath);
        } catch (IOException | InterruptedException e) {
            System.err.println("Error processing metrics.");
        }
    }

    private static void calculateMetrics(List<Long> latencies, long earliestStartTime, long latestEndTime, int successfulRequest) {
        double meanResponseTime = latencies.stream()
                .mapToLong(l -> l)
                .average()
                .orElse(0.0);

        long medianResponseTime;
        if (latencies.size() % 2 == 0) {
            medianResponseTime = (latencies.get(latencies.size() / 2) + latencies.get(latencies.size() / 2 - 1)) / 2;
        } else {
            medianResponseTime = latencies.get(latencies.size() / 2);
        }

        double totalDurationInSeconds = (latestEndTime - earliestStartTime) / 1000.0;
        double throughput = successfulRequest / totalDurationInSeconds;

        int p99Index = (int) Math.ceil(latencies.size() * 0.99) - 1;
        long p99ResponseTime = latencies.get(p99Index);

        System.out.println("Performance Metrics:");
        System.out.printf("Mean Response Time: %.2f ms%n", meanResponseTime);
        System.out.printf("Median Response Time: %d ms%n", medianResponseTime);
        System.out.printf("Throughput: %.2f requests/sec%n", throughput);
        System.out.printf("99th Percentile Response Time: %d ms%n", p99ResponseTime);
        System.out.printf("Min Response Time: %d ms%n", Collections.min(latencies));
        System.out.printf("Max Response Time: %d ms%n", Collections.max(latencies));
    }
}
