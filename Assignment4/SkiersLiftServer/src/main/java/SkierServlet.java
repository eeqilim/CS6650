import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;

@WebServlet("/skiers/*")
public class SkierServlet extends HttpServlet {
    private LiftRideService liftRideService;
    private RabbitMQChannelPool rabbitMQChannelPool;
    private static final String HOST = "35.85.78.59";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin";
    private static final int POOL_SIZE = 65;
    private static final String QUEUE_NAME = "liftRides";
    private final Gson gson = new Gson();

    public static final String REDIS_HOST = "54.214.10.191";
    private static final int REDIS_PORT = 6379;
    private static JedisPool jedisPool;

    @Override
    public void init() {
        try {
            rabbitMQChannelPool = RabbitMQChannelPool.getInstance(QUEUE_NAME, HOST, USERNAME, PASSWORD, POOL_SIZE);
            liftRideService = new LiftRideService(rabbitMQChannelPool);
        } catch (IOException | TimeoutException e) {
            System.err.println("Failed to initialize RabbitMQ channel pool: " + e.getMessage());
        }
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(300);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        jedisPool = new JedisPool(poolConfig, REDIS_HOST, REDIS_PORT);
    }

    @Override
    public void destroy() {
        if (rabbitMQChannelPool != null) {
            try {
                rabbitMQChannelPool.close();
            } catch (IOException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] urlParts = isInvalidRequest(request, response);
        if (urlParts == null) {
            return;
        }

        if (urlParts.length == 8) {
            handleSkierDayVertical(response, urlParts);
        } else if (urlParts.length == 3 && urlParts[2].equals("vertical")) {
            handleSkierVertical(response, urlParts);
        } else {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid URL pattern");
        }
    }

    private void handleSkierDayVertical(HttpServletResponse response, String[] urlParts) throws IOException {
        try (Jedis jedis = jedisPool.getResource()) {
            String skierSeasonKey = String.format("skier:%s:resort:%s:season:%s:day:%s", urlParts[7], urlParts[1], urlParts[3], urlParts[5]);
            List<String> liftIds = jedis.lrange(skierSeasonKey, 0, -1);

            if (liftIds.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("Data not found");
                return;
            }

            int sum = 0;
            for (String liftId : liftIds) {
                try {
                    sum += Integer.parseInt(liftId);
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write("Invalid liftId: " + liftId);
                    return;
                }
            }
            int result = sum * 10;
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(String.valueOf(result));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Error at GET: " + e.getMessage());
        }
    }

    private void handleSkierVertical(HttpServletResponse response, String[] urlParts) throws IOException {
        String skierID = urlParts[1];
        try (Jedis jedis = jedisPool.getResource()) {
            JsonObject responseJson = new JsonObject();
            JsonArray resortsArray = new JsonArray();

            // Get all resort keys for this skier
            Set<String> resortKeys = jedis.keys("skier:" + skierID + ":resort:*:season:*:day:*");
            int totalVert = 0;

            if (resortKeys == null || resortKeys.isEmpty()) {
                sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND,
                        "No vertical data found for skier " + skierID);
                return;
            }

            // Aggregate data across all resorts
            for (String resortKey : resortKeys) {
                String[] keyParts = resortKey.split(":");
                String resortId = keyParts[3];
                String seasonId = keyParts[5];
                String dayId = keyParts[7];

                // Get all lift IDs for this resort/season/day
                List<String> liftIds = jedis.lrange(resortKey, 0, -1);
                int seasonVert = liftIds.stream()
                        .mapToInt(liftId -> {
                            try {
                                return Integer.parseInt(liftId);
                            } catch (NumberFormatException e) {
                                return 0;
                            }
                        })
                        .sum() * 10;

                if (seasonVert > 0) {
                    totalVert += seasonVert;

                    JsonObject seasonObj = new JsonObject();
                    seasonObj.addProperty("seasonID", seasonId);
                    seasonObj.addProperty("resortID", resortId);
                    seasonObj.addProperty("dayID", dayId);
                    seasonObj.addProperty("totalVert", seasonVert);
                    resortsArray.add(seasonObj);
                }
            }

            responseJson.addProperty("skierID", skierID);
            responseJson.addProperty("totalVert", totalVert);
            responseJson.add("resorts", resortsArray);
            sendSuccessResponse(response, responseJson.toString());
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Error retrieving vertical data: " + e.getMessage());
        }
    }

    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        response.getWriter().write(message);
    }

    private void sendSuccessResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(message);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] urlParts= isInvalidRequest(request, response);
        if (urlParts == null) {
            return;
        }

        StringBuilder requestBody = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
        }

        try {
            LiftRide liftRide = gson.fromJson(requestBody.toString(), LiftRide.class);

            if (liftRideService.processLiftRide(liftRide)) {
                response.setStatus(HttpServletResponse.SC_CREATED);
                response.getWriter().write("Lift ride data sent to queue");
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Invalid lift ride data");
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Invalid JSON format");
        }
    }

    private String[] isInvalidRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String urlPath = request.getPathInfo();

        if (urlPath == null || urlPath.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("Missing parameters");
            return null;
        }

        String[] urlParts = urlPath.split("/");

        if (isUrlInvalid(urlParts)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("Invalid URL");
            return null;
        }
        return urlParts;
    }

    private boolean isUrlInvalid(String[] urlParts) {
        if (urlParts.length == 8) {
            try {
                Integer.parseInt(urlParts[1]);
                Integer.parseInt(urlParts[3]);
                Integer.parseInt(urlParts[5]);
                Integer.parseInt(urlParts[7]);
            } catch (NumberFormatException e) {
                return true;
            }
            return !(urlParts[2].equals("seasons") && urlParts[4].equals("days") && urlParts[6].equals("skiers"));
        } else if (urlParts.length == 3 && urlParts[2].equals("vertical")) {
            try {
                Integer.parseInt(urlParts[1]);
                return false;
            } catch (NumberFormatException e) {
                return true;
            }
        }
        return true;
    }
}