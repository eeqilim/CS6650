package Consumer;

import com.rabbitmq.client.*;
import DynamoDB.DynamoDBWriter;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class SkierConsumer {
  private static final String QUEUE_NAME = "SkierQueue";
  private static final int BATCH_SIZE = 25;
  private static final int THREAD_COUNT = 100;

  public static void main(String[] args) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("13.216.71.149");
    factory.setAutomaticRecoveryEnabled(true);
    factory.setNetworkRecoveryInterval(5000);
    factory.setRequestedHeartbeat(30);

    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();
    channel.queueDeclare(QUEUE_NAME, true, false, false, null);
    channel.basicQos(BATCH_SIZE);

    ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
    List<Delivery> batchMessages = new ArrayList<>();

    DeliverCallback deliverCallback = (consumerTag, delivery) -> {
      synchronized (batchMessages) {
        batchMessages.add(delivery);

        if (batchMessages.size() >= BATCH_SIZE) {
          List<Delivery> batchToProcess = new ArrayList<>(batchMessages);
          batchMessages.clear();

          executor.execute(() -> processBatch(batchToProcess, channel));
        }
      }
    };

    channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> {});
    System.out.println(" [*] Waiting for messages...");
  }

  private static void processBatch(List<Delivery> batch, Channel channel) {
    try {
      System.out.println("Processing batch of size: " + batch.size());

      List<String> messages = new ArrayList<>();
      for (Delivery delivery : batch) {
        String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
        messages.add(message);
      }

      DynamoDBWriter.saveBatchToDynamoDB(messages);
      for (Delivery delivery : batch) {
        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
      }

    } catch (Exception e) {
      System.err.println("Error processing batch: " + e.getMessage());
      batch.forEach(delivery -> {
        try {
          channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
        } catch (IOException ioException) {
          ioException.printStackTrace();
        }
      });
    }
  }
}

