import com.google.gson.JsonObject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.IOException;

@WebServlet("/resorts/*")
public class ResortServlet extends HttpServlet {
    public static final String REDIS_HOST = "54.214.10.191";
    private static final int REDIS_PORT = 6379;
    private static JedisPool jedisPool;

    @Override
    public void init() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(300);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        jedisPool = new JedisPool(poolConfig, REDIS_HOST, REDIS_PORT);
    }

    @Override
    public void destroy() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String urlPath = request.getPathInfo();

        if (urlPath == null || urlPath.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("Missing parameters");
            return;
        }

        String[] urlParts = urlPath.split("/");
        if (urlParts.length == 7 && urlParts[2].equals("seasons") && urlParts[4].equals("day") && urlParts[6].equals("skiers")) {
            try {
                int resortId = Integer.parseInt(urlParts[1]);
                String seasonId = urlParts[3];
                String dayId = urlParts[5];

                try (Jedis jedis = jedisPool.getResource()) {
                    String setKey = "resort:" + resortId + ":season:" + seasonId + ":day:" + dayId;
                    long numSkiers = jedis.scard(setKey);

                    if (numSkiers == 0) {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        response.getWriter().write("Data not found");
                        return;
                    }
                    JsonObject json = new JsonObject();
                    json.addProperty("resortId", resortId);
                    json.addProperty("uniqueSkiers", numSkiers);
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.getWriter().write(json.toString());
                }
            } catch (NumberFormatException nfe) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Invalid resort ID format");
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("Error at GET: " + e.getMessage());
            }
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Invalid URL");
        }
    }
}