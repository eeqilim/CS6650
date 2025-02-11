package Part1;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import com.google.gson.Gson;

public class RideEventGenerator implements Runnable {
    private final BlockingQueue<String[]> queue;
    private final Random random = new Random();
    private final int totalRequests;
    private final String serverUrl;
    private static final Gson gson = new Gson();

    public RideEventGenerator(BlockingQueue<String[]> queue, int totalRequests, String baseUrl) {
        this.queue = queue;
        this.totalRequests = totalRequests;
        this.serverUrl = baseUrl;
    }

    @Override
    public void run() {
        try {
            for (int i = 0; i < totalRequests; i++) {
                int skierID = random.nextInt(100000) + 1;
                int resortID = random.nextInt(10) + 1;
                int liftID = random.nextInt(40) + 1;
                int seasonID = 2025;
                int dayID = 1;
                int time = random.nextInt(360) + 1;

                String url = serverUrl + "skiers/" + resortID + "/seasons/" + seasonID + "/days/" + dayID + "/skiers/" + skierID;

                Map<String, Integer> event = new HashMap<>();
                event.put("liftID", liftID);
                event.put("time", time);
                String jsonEvent = gson.toJson(event);

                queue.put(new String[]{url, jsonEvent});
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
