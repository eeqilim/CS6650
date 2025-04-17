import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;

public class RabbitMQChannelPool {
    private final Connection connection;
    private final BlockingQueue<Channel> channelPool;
    private static RabbitMQChannelPool instance;

    public RabbitMQChannelPool(String queueName, String host, String username, String password, int poolSize) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setUsername(username);
        factory.setPassword(password);

        this.connection = factory.newConnection();
        this.channelPool = new LinkedBlockingQueue<>(poolSize);

        for (int i = 0; i < poolSize; i++) {
            try {
                Channel channel = createChannel();
                channel.queueDeclare(queueName, true, false, false, null);
                if (!channelPool.offer(channel)) {
                    System.err.println("Channel pool is full. Failed to return channel to pool.");
                    channel.close();
                }
            } catch (IOException e) {
                System.err.println("Failed to create RabbitMQ channel: " + e.getMessage());
            }
        }
    }

    public synchronized Channel createChannel() throws IOException {
        return connection.createChannel();
    }

    public static synchronized RabbitMQChannelPool getInstance(String queueName, String host, String username, String password, int poolSize) throws IOException, TimeoutException {
        if (instance == null) {
            instance = new RabbitMQChannelPool(queueName, host, username, password, poolSize);
        }
        return instance;
    }

    public Channel borrowChannel() throws InterruptedException {
        return channelPool.take();
    }

    public void returnChannel(Channel channel) {
        if (channel != null && channel.isOpen()) {
            if (!channelPool.offer(channel)) {
                System.err.println("Channel pool is full. Failed to return channel to pool.");
                try {
                    channel.close();
                } catch (IOException | TimeoutException e) {
                    System.err.println("Failed to close channel: " + e.getMessage());
                }
            }
        }
    }

    public void close() throws IOException, TimeoutException {
        for (Channel channel : channelPool) {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        }
        if (connection != null && connection.isOpen()) {
            connection.close();
        }
    }
}
