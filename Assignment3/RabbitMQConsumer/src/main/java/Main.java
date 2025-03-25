import com.google.gson.Gson;
import com.rabbitmq.client.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    private static final String QUEUE_NAME = "liftRides";
    private static final String BROKER_IP = "35.85.78.59";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin";
    private static final int THREAD_COUNT = 150;
    private static final int BATCH_SIZE = 50;
    private static final int TOTAL_MESSAGES = 200000;

    public static final String REDIS_HOST = "54.214.10.191";
    private static final int REDIS_PORT = 6379;
    private static JedisPool jedisPool;

    private static final AtomicInteger messagesProcessed = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(300);

        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);

        jedisPool = new JedisPool(poolConfig, REDIS_HOST, REDIS_PORT);

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

        jedisPool.close();

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        System.out.println("Messages processed: " + messagesProcessed.get());
        System.out.println("Total time (ms): " + totalTime);
        System.out.println("Consumption Rate (msg/sec): " + (messagesProcessed.get() / (totalTime / 1000.0)));
    }

    private static void processMessage(String message) {
        try (Jedis jedis = jedisPool.getResource()) {
            if (jedis == null) {
                throw new RuntimeException("Redis connection failed");
            }

            Gson gson = new Gson();
            LiftRide liftRide = gson.fromJson(message, LiftRide.class);
            String skierID = String.valueOf(liftRide.getSkierID());
            int resortID = liftRide.getResortID();
            int seasonID = liftRide.getSeasonID();
            int dayID = liftRide.getDayID();
            String liftID = String.valueOf(liftRide.getLiftID());
            int vertical = liftRide.getLiftID() * 10;

            Pipeline pipeline = jedis.pipelined();
            pipeline.sadd("resort:" + resortID + ":" + "season:" + seasonID + ":day:" + dayID, skierID);
            pipeline.rpush("skier:" + skierID + ":season:" + seasonID + ":day:" + dayID, liftID);
            pipeline.hincrBy("skier:" + skierID + ":resort:" + resortID, "season:" + seasonID, vertical);
            pipeline.sync();

        } catch (Exception e) {
            System.err.println("Unrecoverable error processing message: " + e.getMessage());
            System.err.println("Failed message: " + message);
        }
    }
}