import com.google.gson.Gson;
import com.rabbitmq.client.Channel;

public class LiftRideService {
    private final RabbitMQChannelPool rabbitMQChannelPool;
    private static final String QUEUE_NAME = "liftRides";
    private final Gson gson;

    public LiftRideService(RabbitMQChannelPool rabbitMQChannelPool) {
        this.rabbitMQChannelPool = rabbitMQChannelPool;
        this.gson = new Gson();
    }

    public boolean processLiftRide(LiftRide liftRide) {
        if (!isLiftRideValid(liftRide)) {
            return false;
        }

        try {
            Channel channel = rabbitMQChannelPool.borrowChannel();
            try {
                String message = gson.toJson(liftRide);
                channel.basicPublish("", QUEUE_NAME, null, message.getBytes());
                return true;
            } catch (Exception e) {
                System.err.println("Failed to send message to RabbitMQ: " + e.getMessage());
                return false;
            } finally {
                rabbitMQChannelPool.returnChannel(channel);
            }
        } catch (InterruptedException e) {
            System.err.println("Interrupted while borrowing channel: " + e.getMessage());
            return false;
        }
    }

    private boolean isLiftRideValid(LiftRide liftRide) {
        return liftRide != null &&
                liftRide.getSkierID() >= 1 && liftRide.getSkierID() <= 100000 &&
                liftRide.getResortID() >= 1 && liftRide.getResortID() <= 10 &&
                liftRide.getLiftID() >= 1 && liftRide.getLiftID() <= 40 &&
                liftRide.getSeasonID() == 2025 && liftRide.getDayID() == 1 &&
                liftRide.getTime() >= 1 && liftRide.getTime() <= 360;
    }
}