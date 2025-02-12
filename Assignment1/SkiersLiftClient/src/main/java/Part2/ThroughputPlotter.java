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
import java.util.Map;

public class ThroughputPlotter {
    public static void plotThroughput(String plotTitle, Map<Long, Long> throughputData, String imgPath) {
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

        File imageFile = new File(imgPath);
        try {
            ChartUtils.saveChartAsPNG(imageFile, chart, 800, 600);
        } catch (IOException e) {
            System.err.println("Error saving image to file");
        }
    }

    private static DefaultCategoryDataset createDataset(Map<Long, Long> throughputData) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        for (Map.Entry<Long, Long> entry : throughputData.entrySet()) {
            long intervalStart = entry.getKey();
            long requestCount = entry.getValue();
            dataset.addValue(requestCount, "Throughput", String.valueOf(intervalStart));
        }
        return dataset;
    }
}
