import com.google.gson.Gson;

import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.BufferedReader;
import java.io.IOException;

@WebServlet("/skiers/*")
public class SkierServlet extends HttpServlet {
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

        if (!isUrlValid(urlParts)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("Invalid URL");
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("It works!");
        }
    }

    private boolean isUrlValid(String[] urlPath) {
        if (urlPath.length != 8) {
            return false;
        }
        try {
            Integer.parseInt(urlPath[1]);
            Integer.parseInt(urlPath[3]);
            Integer.parseInt(urlPath[5]);
            Integer.parseInt(urlPath[7]);
        } catch (NumberFormatException e) {
            return false;
        }
        return urlPath[2].equals("seasons") && urlPath[4].equals("days") && urlPath[6].equals("skier");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String postReqUrl = request.getRequestURI();
        System.out.println("Received POST Request URL: " + postReqUrl);

        StringBuilder requestBody = new StringBuilder();
        BufferedReader reader = request.getReader();
        String line;

        while ((line = reader.readLine()) != null) {
            requestBody.append(line);
        }
        System.out.println("Received JSON: " + requestBody);

        try {
            LiftRide liftRide = new Gson().fromJson(requestBody.toString(), LiftRide.class);

            if (liftRide == null || !isLiftRideValid (liftRide)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            response.setStatus(HttpServletResponse.SC_CREATED);
            response.getWriter().write(new Gson().toJson(liftRide));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Invalid JSON format");
        }
    }

    private boolean isLiftRideValid(LiftRide liftRide) {
        return liftRide.getSkierID() >= 1 && liftRide.getSkierID() <= 100000 &&
                liftRide.getResortID() >= 1 && liftRide.getResortID() <= 10 &&
                liftRide.getLiftID() >= 1 && liftRide.getLiftID() <= 40 &&
                liftRide.getSeasonID() == 2025 && liftRide.getDayID() == 1 &&
                liftRide.getTime() >= 1 && liftRide.getTime() <= 360;
    }
}
