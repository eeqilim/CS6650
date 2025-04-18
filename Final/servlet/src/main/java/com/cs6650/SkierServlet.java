package com.cs6650;
import com.amazonaws.auth.BasicAWSCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javax.servlet.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import com.rabbitmq.client.*;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

// AWS SDK imports
import java.io.File;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

@WebServlet("/skiers/*")
public class SkierServlet extends HttpServlet {

  private final static String QUEUE_NAME = "SkierQueue";
  private static final String RABBITMQ_HOST = "13.216.71.149"; // Elastic IP of RabbitMQ EC2
  private static final int CHANNEL_POOL_SIZE = 120;
  private static ConnectionFactory factory = new ConnectionFactory();
  private static Connection connection;
  private static final BlockingQueue<Channel> channelPool = new LinkedBlockingQueue<>(CHANNEL_POOL_SIZE);

  // DynamoDB client
  private static final DynamoDbClient dynamoDbClient;
  private static final String TABLE_NAME = "SkiersCounters";

  static {
    // Try to initialize DynamoDB client with explicit profile credentials if available
    try {
      // For AWS lab environment with temporary credentials
      AwsSessionCredentials sessionCredentials = AwsSessionCredentials.create(
          "",
          "",
          ""
      );

      dynamoDbClient = DynamoDbClient.builder()
          .region(Region.US_EAST_1)
          .credentialsProvider(StaticCredentialsProvider.create(sessionCredentials))
          .build();
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize DynamoDB client: " + e.getMessage(), e);
    }
  }

  static { initializeChannelPool(); }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    res.setContentType("application/json");
    res.setCharacterEncoding("UTF-8");
    String urlPath = req.getPathInfo();
    System.out.println("GET request: " + urlPath);

    if (urlPath == null || urlPath.isEmpty()) {
      res.setStatus(HttpServletResponse.SC_NOT_FOUND);
      res.getWriter().write("{\"message\": \"missing parameters\"}");
      return;
    }

    // Health check handler
    if (urlPath.equals("/health")) {
      res.setStatus(HttpServletResponse.SC_OK);
      res.getWriter().write("{\"status\": \"healthy\"}");
      return;
    }

    String[] urlParts = urlPath.split("/");

    try {
      // Determine which API endpoint is being called
      if (urlParts.length >= 8 && urlParts[2].equals("seasons") &&
          urlParts[4].equals("days") && urlParts[6].equals("skiers")) {
        // GET /skiers/{resortID}/seasons/{seasonID}/days/{dayID}/skiers/{skierID}
        handleSkierDayVertical(urlParts, res);
      } else if (urlParts.length == 3 && urlParts[2].equals("vertical")) {
        // GET /skiers/{skierID}/vertical
        handleSkierTotalVertical(urlParts, res);
      } else {
        res.setStatus(HttpServletResponse.SC_NOT_FOUND);
        res.getWriter().write("{\"message\": \"invalid url pattern for skier\"}");
      }
    } catch (Exception e) {
      res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      res.getWriter().write("{\"message\": \"" + e.getMessage() + "\"}");
      e.printStackTrace();
    }
  }

  /**
   * Handle GET /skiers/{resortID}/seasons/{seasonID}/days/{dayID}/skiers/{skierID}
   * Returns the total vertical for the specified skier for the specified day
   */
  private void handleSkierDayVertical(String[] urlParts, HttpServletResponse res) throws IOException {
    String resortID = urlParts[1];
    String seasonID = urlParts[3];
    String dayID = urlParts[5];
    String skierID = urlParts[7];

    try {
      // Construct the key for the day vertical counter
      String dayKey = resortID + "_" + seasonID + "_" + dayID + "_" + skierID;

      // Query DynamoDB for the counter
      GetItemRequest getRequest = GetItemRequest.builder()
          .tableName("SkiersCounters")
          .key(Map.of(
              "CounterId", AttributeValue.builder().s("DayVertical").build(),
              "Key", AttributeValue.builder().s(dayKey).build()
          ))
          .build();

      GetItemResponse getResponse = dynamoDbClient.getItem(getRequest);

      int totalVertical = 0;
      if (getResponse.hasItem() && getResponse.item().containsKey("Vertical")) {
        totalVertical = Integer.parseInt(getResponse.item().get("Vertical").n());
      }

      JSONObject responseJson = new JSONObject();
      responseJson.put("resortID", resortID);
      responseJson.put("seasonID", seasonID);
      responseJson.put("dayID", dayID);
      responseJson.put("skierID", skierID);
      responseJson.put("totalVertical", totalVertical);

      res.setStatus(HttpServletResponse.SC_OK);
      res.getWriter().write(responseJson.toJSONString());
    } catch (Exception e) {
      res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      res.getWriter().write("{\"message\": \"Error querying DynamoDB: " + e.getMessage() + "\"}");
      e.printStackTrace();
    }
  }

  /**
   * Handle GET /skiers/{skierID}/vertical
   * Returns the total vertical for the specified skier across all seasons
   */
  private void handleSkierTotalVertical(String[] urlParts, HttpServletResponse res) throws IOException {
    String skierID = urlParts[1];

    try {
      Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
      expressionAttributeValues.put(":counterId", AttributeValue.builder().s("TotalVertical").build());
      expressionAttributeValues.put(":skierId", AttributeValue.builder().s(skierID).build());

      QueryRequest queryRequest = QueryRequest.builder()
          .tableName("SkiersCounters")
          .indexName("CounterId-SkierId-index")  // Name of your GSI
          .keyConditionExpression("CounterId = :counterId AND SkierId = :skierId")
          .expressionAttributeValues(expressionAttributeValues)
          .build();

      QueryResponse queryResponse = dynamoDbClient.query(queryRequest);

      // Calculate total vertical by resort
      Map<String, Integer> resortVerticals = new HashMap<>();

      for (Map<String, AttributeValue> item : queryResponse.items()) {
        if (item.containsKey("ResortId") && item.containsKey("Vertical")) {
          String resortId = item.get("ResortId").s();
          int vertical = Integer.parseInt(item.get("Vertical").n());

          resortVerticals.put(resortId, resortVerticals.getOrDefault(resortId, 0) + vertical);
        }
      }

      // Build response JSON
      JSONObject responseJson = new JSONObject();
      JSONArray resortTotals = new JSONArray();

      for (Map.Entry<String, Integer> entry : resortVerticals.entrySet()) {
        JSONObject resortTotal = new JSONObject();
        resortTotal.put("resortID", entry.getKey());
        resortTotal.put("totalVertical", entry.getValue());
        resortTotals.add(resortTotal);
      }

      responseJson.put("skierID", skierID);
      responseJson.put("resorts", resortTotals);

      res.setStatus(HttpServletResponse.SC_OK);
      res.getWriter().write(responseJson.toJSONString());
    } catch (Exception e) {
      res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      res.getWriter().write("{\"message\": \"Error querying DynamoDB: " + e.getMessage() + "\"}");
      e.printStackTrace();
    }
  }



  private boolean isUrlValid(String[] urlPath, boolean isGet) {
    if (isGet) {
      // For GET requests, we've implemented specific validation in doGet
      return true;
    }

    if (urlPath.length < 6) return false;

    try {
      Integer.parseInt(urlPath[1]);
      Integer.parseInt(urlPath[3]);
      Integer.parseInt(urlPath[5]);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    res.setContentType("application/json");
    res.setCharacterEncoding("UTF-8");
    String urlPath = req.getPathInfo();

    if (urlPath == null || urlPath.isEmpty()) {
      res.setStatus(HttpServletResponse.SC_NOT_FOUND);
      res.getWriter().write("{\"message\": \"missing parameters\"}");
      return;
    }

    String[] urlParts = urlPath.split("/");

    if (!isUrlValid(urlParts, false)) {
      res.setStatus(HttpServletResponse.SC_NOT_FOUND);
      res.getWriter().write("{\"message\": \"invalid url\"}");
    } else {
      String body = req.getReader().lines().collect(Collectors.joining());
      if (body.isEmpty()) {
        res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        res.getWriter().write("{\"message\": \"missing request body\"}");
      } else {
        String resortID = urlParts[1];
        String seasonID = urlParts[3];
        String dayID = urlParts[5];
        String skierID = urlParts[urlParts.length - 1];
        String message = packageMessage(body, resortID, seasonID, dayID, skierID);

        // Send data to RabbitMQ message queue
        boolean success = sendToMessageQueue(message);
        if (success) {
          res.setStatus(HttpServletResponse.SC_CREATED);
          res.getWriter().write("{\"message\": \"POST successful\"}");
        } else {
          res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
          res.getWriter().write("{\"message\": \"Failed to send message to queue\"}");
        }
      }
    }
  }

  private static void initializeChannelPool() {
    factory.setHost(RABBITMQ_HOST);
    try {
      connection = factory.newConnection();
      Channel setupChannel = connection.createChannel();
      setupChannel.queueDeclare(QUEUE_NAME, true, false, false, null); // Durable queue
      setupChannel.close();

      for (int i = 0; i < CHANNEL_POOL_SIZE; i++) {
        Channel channel = connection.createChannel();
        channelPool.add(channel);
      }
    } catch (IOException | TimeoutException e) {
      throw new RuntimeException("Failed to initialize RabbitMQ channel pool", e);
    }
  }

  private String packageMessage(String body, String resortID, String seasonID, String dayID, String skierID) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"body\":").append(body)
        .append(", \"resortID\":\"").append(resortID).append("\"")
        .append(", \"seasonID\":\"").append(seasonID).append("\"")
        .append(", \"dayID\":\"").append(dayID).append("\"")
        .append(", \"skierID\":\"").append(skierID).append("\"}");
    return sb.toString();
  }

  private boolean sendToMessageQueue(String message) {
    Channel channel = null;
    try {
      channel = channelPool.poll();
      if (channel == null) {
        System.err.println("No available channel in pool!");
        return false;
      }

      channel.basicPublish("", QUEUE_NAME, null, message.getBytes());
      System.out.println(" [x] Sent '" + message + "'");
      return true;

    } catch (IOException e) {
      System.err.println("Failed to send message to queue: " + e.getMessage());
      return false;
    } finally {
      if (channel != null) {
        channelPool.offer(channel); // Return channel to pool
      }
    }
  }
}