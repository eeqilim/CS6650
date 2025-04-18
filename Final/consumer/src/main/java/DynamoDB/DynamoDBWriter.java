package DynamoDB;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.*;

public class DynamoDBWriter {

  private static final String TABLE_NAME = "SkiersData";
  private static final String COUNTER_TABLE_NAME = "SkiersCounters";
  private static final Logger logger = LoggerFactory.getLogger(DynamoDBWriter.class);
  private static final DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
      .region(Region.US_EAST_1)
      .build();

  public static void saveBatchToDynamoDB(List<String> messages) {
    try {
      List<WriteRequest> writeRequests = new ArrayList<>();
      List<WriteRequest> counterRequests = new ArrayList<>();

      Set<String> processedSkiers = new HashSet<>();

      for (String message : messages) {
        try {
          JSONObject json = new JSONObject(message);
          String resortID = json.getString("resortID");
          String seasonID = json.getString("seasonID");
          String dayID = json.getString("dayID");
          String skierID = json.getString("skierID");

          // The body is actually a JSON object, not a string
          JSONObject bodyJson = json.getJSONObject("body");
          int time = bodyJson.getInt("time");
          int liftID = bodyJson.getInt("liftID");

          // Calculate vertical - assuming 10 * liftId
          int vertical = liftID * 10;

          // Create the composite key for GSI
          String resort_season_day = resortID + "_" + seasonID + "_" + dayID;

          // 1. Primary table write request (original)
          Map<String, AttributeValue> item = new HashMap<>();
          item.put("skierId", AttributeValue.builder().s(skierID).build());
          item.put("dayId", AttributeValue.builder().s(dayID).build());
          item.put("resort_season_day", AttributeValue.builder().s(resort_season_day).build());
          item.put("resortId", AttributeValue.builder().s(resortID).build());
          item.put("seasonId", AttributeValue.builder().s(seasonID).build());
          item.put("time", AttributeValue.builder().n(String.valueOf(time)).build());
          item.put("liftId", AttributeValue.builder().n(String.valueOf(liftID)).build());
          item.put("vertical", AttributeValue.builder().n(String.valueOf(vertical)).build());

          writeRequests.add(WriteRequest.builder().putRequest(PutRequest.builder().item(item).build()).build());

          // 2. Unique skier counter
          String uniqueKey = resortID + "_" + seasonID + "_" + dayID + "_" + skierID;
          if (!processedSkiers.contains(uniqueKey)) {
            processedSkiers.add(uniqueKey);

            // Add skier check item
            Map<String, AttributeValue> skierCheckItem = new HashMap<>();
            skierCheckItem.put("CounterId", AttributeValue.builder().s("UniqueSkierCheck").build());
            skierCheckItem.put("Key", AttributeValue.builder().s(uniqueKey).build());
            skierCheckItem.put("Value", AttributeValue.builder().s("counted").build());

            counterRequests.add(WriteRequest.builder().putRequest(PutRequest.builder().item(skierCheckItem).build()).build());
          }

          // 3. Day vertical item
          Map<String, AttributeValue> dayVerticalItem = new HashMap<>();
          dayVerticalItem.put("CounterId", AttributeValue.builder().s("DayVertical").build());
          dayVerticalItem.put("Key", AttributeValue.builder().s(resortID + "_" + seasonID + "_" + dayID + "_" + skierID).build());
          dayVerticalItem.put("Vertical", AttributeValue.builder().n(String.valueOf(vertical)).build());
          dayVerticalItem.put("ResortId", AttributeValue.builder().s(resortID).build());
          dayVerticalItem.put("SeasonId", AttributeValue.builder().s(seasonID).build());
          dayVerticalItem.put("DayId", AttributeValue.builder().s(dayID).build());
          dayVerticalItem.put("SkierId", AttributeValue.builder().s(skierID).build());

          counterRequests.add(WriteRequest.builder().putRequest(PutRequest.builder().item(dayVerticalItem).build()).build());

          // 4. Total vertical item
          Map<String, AttributeValue> totalVerticalItem = new HashMap<>();
          totalVerticalItem.put("CounterId", AttributeValue.builder().s("TotalVertical").build());
          totalVerticalItem.put("Key", AttributeValue.builder().s(resortID + "_" + seasonID + "_" + skierID).build());
          totalVerticalItem.put("Vertical", AttributeValue.builder().n(String.valueOf(vertical)).build());
          totalVerticalItem.put("ResortId", AttributeValue.builder().s(resortID).build());
          totalVerticalItem.put("SeasonId", AttributeValue.builder().s(seasonID).build());
          totalVerticalItem.put("SkierId", AttributeValue.builder().s(skierID).build());

          counterRequests.add(WriteRequest.builder().putRequest(PutRequest.builder().item(totalVerticalItem).build()).build());

        } catch (Exception e) {
          // Log the specific message that caused the error
          logger.error("Error processing message: " + message, e);
          // Continue processing other messages
        }
      }

      // Process original table writes
      if (!writeRequests.isEmpty()) {
        // Split into batches of 25 if needed (DynamoDB batch limit)
        processBatchWrites(writeRequests, TABLE_NAME);
      }

      // Process counter table writes
      if (!counterRequests.isEmpty()) {
        processBatchWrites(counterRequests, "SkiersCounters");
      }

      // After processing all items, update the unique skiers counter
      updateUniqueSkiersCounters(processedSkiers);

    } catch (Exception e) {
      logger.error("Batch write failed: {}", e.getMessage(), e);
    }
  }

  private static void processBatchWrites(List<WriteRequest> requests, String tableName) {
    for (int i = 0; i < requests.size(); i += 25) {
      int endIndex = Math.min(i + 25, requests.size());
      List<WriteRequest> batch = requests.subList(i, endIndex);

      BatchWriteItemRequest batchRequest = BatchWriteItemRequest.builder()
          .requestItems(Collections.singletonMap(tableName, batch))
          .build();

      dynamoDbClient.batchWriteItem(batchRequest);
      logger.info("Batch write successful for {} items {} to {}", tableName, i, endIndex-1);
    }
  }

  private static void updateUniqueSkiersCounters(Set<String> processedSkiers) {
    // Group skiers by resort/season/day
    Map<String, Integer> counterUpdates = new HashMap<>();

    for (String uniqueKey : processedSkiers) {
      String[] parts = uniqueKey.split("_");
      if (parts.length >= 3) {
        String counterKey = parts[0] + "_" + parts[1] + "_" + parts[2]; // resort_season_day
        counterUpdates.put(counterKey, counterUpdates.getOrDefault(counterKey, 0) + 1);
      }
    }

    // Update counters
    for (Map.Entry<String, Integer> entry : counterUpdates.entrySet()) {
      String counterKey = entry.getKey();
      int count = entry.getValue();

      try {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("CounterId", AttributeValue.builder().s("UniqueSkiers").build());
        key.put("Key", AttributeValue.builder().s(counterKey).build());

        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":inc", AttributeValue.builder().n(String.valueOf(count)).build());

        try {
          // Try to update existing counter
          dynamoDbClient.updateItem(software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest.builder()
              .tableName(COUNTER_TABLE_NAME)
              .key(key)
              .updateExpression("ADD #count :inc")
              .expressionAttributeNames(Collections.singletonMap("#count", "Count"))
              .expressionAttributeValues(values)
              .build());
        } catch (Exception e) {
          // If update fails, create new counter
          Map<String, AttributeValue> item = new HashMap<>(key);
          item.put("Count", AttributeValue.builder().n(String.valueOf(count)).build());

          dynamoDbClient.putItem(software.amazon.awssdk.services.dynamodb.model.PutItemRequest.builder()
              .tableName("SkiersCounters")
              .item(item)
              .build());
        }
      } catch (Exception e) {
        logger.error("Error updating counter for key {}: {}", counterKey, e.getMessage());
      }
    }
  }
}