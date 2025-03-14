import com.google.gson.Gson;
import com.rabbitmq.client.*;

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
    private static final int THREAD_COUNT = 300;
    private static final int BATCH_SIZE = 100;
    private static final int TOTAL_MESSAGES = 200000;

    private static final ConcurrentHashMap<Integer, List<LiftRide>> skierData = new ConcurrentHashMap<>();
    private static final AtomicInteger messagesProcessed = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(BROKER_IP);
        factory.setUsername(USERNAME);
        factory.setPassword(PASSWORD);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(5000);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    Connection connection = factory.newConnection();
                    Channel channel = connection.createChannel();

                    channel.queueDeclare(QUEUE_NAME, true, false, false, null);
                    channel.basicQos(BATCH_SIZE);

                    String consumerTag = "consumer-" + threadId;

                    channel.basicConsume(QUEUE_NAME, false, consumerTag,
                            new DefaultConsumer(channel) {
                                @Override
                                public void handleDelivery(String consumerTag, Envelope envelope,
                                                           AMQP.BasicProperties properties, byte[] body) throws IOException {
                                    try {
                                        String message = new String(body, StandardCharsets.UTF_8);
                                        processMessage(message);
                                        channel.basicAck(envelope.getDeliveryTag(), false);
                                        messagesProcessed.incrementAndGet();
                                    } catch (Exception e) {
                                        channel.basicNack(envelope.getDeliveryTag(), false, true);
                                    }
                                }
                            });

                    latch.countDown();

                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        try {
                            if (channel.isOpen()) {
                                channel.close();
                            }
                            if (connection.isOpen()) {
                                connection.close();
                            }

                        } catch (Exception e) {
                            System.err.println("Error closing consumer: " + e.getMessage());
                        }
                    }));

                } catch (Exception e) {
                    latch.countDown();
                }
            });
        }

        if (!latch.await(30, TimeUnit.SECONDS)) {
            System.err.println("Partial consumer threads initialized");
        }

        long startTime = System.currentTimeMillis();

        while (!executor.isTerminated() && messagesProcessed.get() < TOTAL_MESSAGES) {
            Thread.sleep(1000);
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        System.out.println("Messages processed: " + messagesProcessed.get());
        System.out.println("Total time (ms): " + totalTime);
        System.out.println("Consumption Rate (msg/sec): " + (messagesProcessed.get() / (totalTime / 1000.0)));
    }

    private static void processMessage(String message) {
        try {
            Gson gson = new Gson();
            LiftRide liftRide = gson.fromJson(message, LiftRide.class);
            skierData.computeIfAbsent(liftRide.getSkierID(), k -> new ArrayList<>()).add(liftRide);
        } catch (Exception e) {
            System.err.println("Error processing message JSON: " + e.getMessage());
        }
    }
}