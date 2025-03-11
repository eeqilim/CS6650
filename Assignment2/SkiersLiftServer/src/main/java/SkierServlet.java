import com.google.gson.Gson;

import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import com.rabbitmq.client.Channel;

@WebServlet("/skiers/*")
public class SkierServlet extends HttpServlet {
    private RabbitMQChannelPool rabbitMQChannelPool;
    private static final String QUEUE_NAME = "liftRides";
    private static final String HOST = "52.13.92.67";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin";
    private static final int POOL_SIZE = 65;

    @Override
    public void init() {
        try {
            rabbitMQChannelPool = RabbitMQChannelPool.getInstance(QUEUE_NAME, HOST, USERNAME, PASSWORD, POOL_SIZE);
        } catch (IOException | TimeoutException e) {
            System.err.println("Failed to initialize RabbitMQ channel pool: " + e.getMessage());
        }
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
        if (isInvalidRequest(request, response)) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write("It works!");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (isInvalidRequest(request, response)) {
            return;
        }
        StringBuilder requestBody = new StringBuilder();
        BufferedReader reader = request.getReader();
        String line;

        while ((line = reader.readLine()) != null) {
            requestBody.append(line);
        }
        try {
            LiftRide liftRide = new Gson().fromJson(requestBody.toString(), LiftRide.class);

            if (liftRide == null || !isLiftRideValid (liftRide)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            Channel channel = rabbitMQChannelPool.borrowChannel();
            try {
                String message = new Gson().toJson(liftRide);
                channel.basicPublish("", QUEUE_NAME, null, message.getBytes());
                response.setStatus(HttpServletResponse.SC_CREATED);
                response.getWriter().write("Lift ride data sent to queue: " + message);
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("Failed to send message to RabbitMQ: " + e.getMessage());
            } finally {
                rabbitMQChannelPool.returnChannel(channel);
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Invalid JSON format");
        }
    }

    private boolean isInvalidRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String urlPath = request.getPathInfo();

        if (urlPath == null || urlPath.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("Missing parameters");
            return true;
        }

        String[] urlParts = urlPath.split("/");

        if (isUrlInvalid(urlParts)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("Invalid URL");
            return true;
        }
        return false;
    }

    private boolean isUrlInvalid(String[] urlPath) {
        if (urlPath.length != 8) {
            return true;
        }
        try {
            Integer.parseInt(urlPath[1]);
            Integer.parseInt(urlPath[3]);
            Integer.parseInt(urlPath[5]);
            Integer.parseInt(urlPath[7]);
        } catch (NumberFormatException e) {
            return true;
        }
        return !(urlPath[2].equals("seasons") && urlPath[4].equals("days") && urlPath[6].equals("skiers"));
    }

    private boolean isLiftRideValid(LiftRide liftRide) {
        return liftRide.getSkierID() >= 1 && liftRide.getSkierID() <= 100000 &&
                liftRide.getResortID() >= 1 && liftRide.getResortID() <= 10 &&
                liftRide.getLiftID() >= 1 && liftRide.getLiftID() <= 40 &&
                liftRide.getSeasonID() == 2025 && liftRide.getDayID() == 1 &&
                liftRide.getTime() >= 1 && liftRide.getTime() <= 360;
    }
}
