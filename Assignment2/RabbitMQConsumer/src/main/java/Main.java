import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    private static final String QUEUE_NAME = "liftRides";
    private static final String BROKER_IP = "52.13.92.67";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin";
    private static final int THREAD_COUNT = 35;
    private static final int BATCH_SIZE = 30;

    private static final ConcurrentHashMap<Integer, List<LiftRide>> skierData = new ConcurrentHashMap<>();
    private static final AtomicInteger messagesProcessed = new AtomicInteger(0);

    public static void main(String[] args) throws IOException, TimeoutException, InterruptedException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(BROKER_IP);
        factory.setUsername(USERNAME);
        factory.setPassword(PASSWORD);

        Connection connection = factory.newConnection();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    Channel channel = connection.createChannel();
                    channel.queueDeclare(QUEUE_NAME, true, false, false, null);
                    channel.basicQos(BATCH_SIZE);
                    DeliverCallback deliverCallback = getDeliverCallback(channel);
                    channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> {
                    });
                } catch (IOException e) {
                    System.out.println("Failed to created RabbitMQ channel: " + e.getMessage());
                }
            });
        }
        ScheduledExecutorService monitor = Executors.newScheduledThreadPool(1);
        monitor.scheduleAtFixedRate(() -> {
            System.out.println("Messages processed: " + messagesProcessed.get());
        }, 1, 1, TimeUnit.SECONDS);

        executor.shutdown();

        if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
            System.err.println("Executor did not terminate. Force shutdown.");
            executor.shutdownNow();
        }

        connection.close();

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        System.out.println("Total time (ms): " + totalTime);
        System.out.println("RabbitMQ Consumer Throughput (requests/sec): " + (messagesProcessed.get() / (totalTime / 1000.0)));
        System.out.println();
    }

    private static DeliverCallback getDeliverCallback(Channel channel) {
        return (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            processMessage(message);
            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            messagesProcessed.incrementAndGet();
        };
    }

    private static void processMessage(String message) {
        Gson gson = new Gson();
        LiftRide liftRide = gson.fromJson(message, LiftRide.class);
        skierData.computeIfAbsent(liftRide.getSkierID(), k -> new ArrayList<>()).add(liftRide);
    }
}
