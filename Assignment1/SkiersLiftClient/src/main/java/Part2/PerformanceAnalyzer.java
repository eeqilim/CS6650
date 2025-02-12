package Part2;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;

public class PerformanceAnalyzer {
    public static void processMetrics(String csvPath, BlockingQueue<String[]> metricsQueue, String imgPath, String plotTitle) {
        List<Long> latencies = new ArrayList<>();
        List<Long> timestamps = new ArrayList<>();

        long[] timeRange = writeToCsv(csvPath, metricsQueue, latencies, timestamps);
        long earliestStartTime = timeRange[0];
        long latestEndTime = timeRange[1];

        Collections.sort(latencies);
        calculateMetrics(latencies, earliestStartTime, latestEndTime);

        Map<Long, Long> throughputData = calculateThroughput(timestamps);
        ThroughputPlotter.plotThroughput(plotTitle, throughputData, imgPath);
    }

    private static long[] writeToCsv(String csvPath, BlockingQueue<String[]> metricsQueue, List<Long> latencies, List<Long> timestamps) {
        long earliestStartTime = Long.MAX_VALUE;
        long latestEndTime = Long.MIN_VALUE;

        try (FileWriter fileWriter = new FileWriter(csvPath)) {
            fileWriter.write("Start Time, Request Type, Latency, Response Code\n");

            for (String[] metric : metricsQueue) {
                long startTime = Long.parseLong(metric[0]);
                long latency = Long.parseLong((metric[2]));
                long endTime = startTime + latency;

                if (startTime < earliestStartTime) {
                    earliestStartTime = startTime;
                }
                if (endTime > latestEndTime) {
                    latestEndTime = endTime;
                }

                timestamps.add(startTime);
                latencies.add(latency);
                fileWriter.write(String.join(",", metric) + "\n");
            }
        } catch (IOException e) {
            System.err.println("Error writing to CSV: " + e.getMessage());
        }
        return new long[]{earliestStartTime, latestEndTime};
    }

    private static void calculateMetrics(List<Long> latencies, long earliestStartTime, long latestEndTime) {
        long meanResponseTime = latencies.stream().mapToLong(l -> l).sum() / latencies.size();

        long medianResponseTime;
        if (latencies.size() % 2 == 0) {
            medianResponseTime = (latencies.get(latencies.size() / 2) + latencies.get(latencies.size() / 2 - 1)) / 2;
        } else {
            medianResponseTime = latencies.get(latencies.size() / 2);
        }

        double totalDurationInSeconds = (latestEndTime - earliestStartTime) / 1000.0;
        double throughput = latencies.size() / totalDurationInSeconds;

        long p99ResponseTime = latencies.get((int) (latencies.size() * 0.99));
        long minResponseTime = Collections.min(latencies);
        long maxResponseTime = Collections.max(latencies);

        System.out.println("Mean Response Time (ms): " + meanResponseTime);
        System.out.println("Median Response Time (ms): " + medianResponseTime);
        System.out.println("Throughput (requests/sec): " + throughput);
        System.out.println("99th Percentile Response Time (ms): " + p99ResponseTime);
        System.out.println("Min Response Time (ms): " + minResponseTime);
        System.out.println("Max Response Time (ms): " + maxResponseTime);
    }

    private static Map<Long, Long> calculateThroughput(List<Long> timestamps) {
        Map<Long, Long> throughputMap = new HashMap<>();

        for (long startTime : timestamps) {
            long intervalStart = startTime / 1000;
            throughputMap.put(intervalStart, throughputMap.getOrDefault(intervalStart, 0L) + 1);
        }
        return throughputMap;
    }
}
