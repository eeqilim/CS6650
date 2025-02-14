import com.google.gson.Gson;

import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.BufferedReader;
import java.io.IOException;

@WebServlet("/skiers/*")
public class SkierServlet extends HttpServlet {
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
            response.setStatus(HttpServletResponse.SC_CREATED);
            response.getWriter().write(new Gson().toJson(liftRide));
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
