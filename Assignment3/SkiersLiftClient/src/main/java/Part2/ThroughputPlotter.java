package Part2;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class ThroughputPlotter {
    public static void plotThroughput(String plotTitle, List<Long> timestamps, String imgPath) {
        Map<Long, Double> throughputData = calculateThroughputOverTime(timestamps);

        JFreeChart chart = ChartFactory.createLineChart(
                plotTitle, "Time (sec)", "Throughput (requests/sec)",
                createDataset(throughputData), PlotOrientation.VERTICAL,true, true, false
        );
        ChartPanel chartPanel = new ChartPanel(chart);
        JFrame frame = new JFrame(plotTitle);
        frame.setContentPane(chartPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        try {
            ChartUtils.saveChartAsPNG(new File(imgPath), chart, 1000, 600);
        } catch (IOException e) {
            System.err.println("Error saving image to file");
        }
    }

    private static Map<Long, Double> calculateThroughputOverTime(List<Long> timestamps) {
        Map<Long, Double> throughputMap = new TreeMap<>();
        if (timestamps.isEmpty()) return throughputMap;

        long startTime = Collections.min(timestamps);

        for (long timestamp : timestamps) {
            long relativeSecond = (timestamp - startTime) / 1000;
            throughputMap.put(relativeSecond, throughputMap.getOrDefault(relativeSecond, 0.0) + 1);
        }

        long endSecond = Collections.max(throughputMap.keySet());
        for (long second = 0; second <= endSecond; second++) {
            throughputMap.putIfAbsent(second, 0.0);
        }

        Map<Long, Double> smoothedMap = new TreeMap<>();
        final int windowSize = 5;
        for (long second = 0; second <= endSecond; second++) {
            double sum = 0;
            int count = 0;
            for (int i = 0; i < windowSize; i++) {
                long windowSecond = second - i;
                if (throughputMap.containsKey(windowSecond)) {
                    sum += throughputMap.get(windowSecond);
                    count++;
                }
            }
            smoothedMap.put(second, count > 0 ? sum / count : 0.0);
        }
        return smoothedMap;
    }

    private static DefaultCategoryDataset createDataset(Map<Long, Double> throughputData) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (Map.Entry<Long, Double> entry : throughputData.entrySet()) {
            dataset.addValue(entry.getValue(), "Throughput", entry.getKey().toString());
        }
        return dataset;
    }
}
