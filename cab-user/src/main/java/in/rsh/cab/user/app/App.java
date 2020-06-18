// $Id$
/**
 *
 */
package in.rsh.cab.user.app;

import in.rsh.cab.user.DB.DBStore;
import in.rsh.cab.user.constants.AppConstants;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** @author rahil */
//TODO: Use Spring controller instead of this
//TODO: Move to tests
public class App {

  String method;
  HttpServletRequest request;
  HttpServletResponse response;

  public App(HttpServletRequest request, HttpServletResponse response)
      throws IOException, JSONException {
    this.request = request;
    this.response = response;
    // Called from user servlet
    String path = request.getPathInfo();
    if (path != null) {
      String[] paths = path.split("/");
      if (paths.length > 1) {
        method = paths[1];
      }
    }
    if (method == null) {
      return;
    }
    response.setCharacterEncoding(AppConstants.CHAR_ENCODING_UTF8);
    response.setContentType(AppConstants.JSON_MIMETYPE);
    if (method.equalsIgnoreCase("cabs")) {
      cabs();
    } else if (method.equalsIgnoreCase("book")) {
      book();
    } else if (method.equalsIgnoreCase("stop")) {
      stop();
    }
  }

  private void stop() throws NumberFormatException, JSONException, IOException {
    String number = request.getParameter("number");
    String duration = request.getParameter("duration"); // in minutes
    String type = request.getParameter("pink");
    String lat = request.getParameter("lat");
    String lon = request.getParameter("lat");
    float distance =
        Float.parseFloat(
            DBStore.cabs(Float.parseFloat(lat), Float.parseFloat(lon), false, number)
                .getJSONObject(0)
                .getString("distance"));

    double fare = fare(distance, type, Integer.parseInt(duration));
    DBStore.update(lat, lon, fare);
    response.getWriter().write(new JSONObject("{\"result\":\"Fare is " + fare + "\"}").toString());
  }

  private double fare(float distance, String type, int duration) {
    return (double) ((1 * duration) + (distance * 1.60934) + (type == "pink" ? 5 : 0));
  }

  private void cabs() throws IOException {
    JSONArray cabs = DBStore.cabs(0F, 0F, false, null);
    response.getWriter().write(cabs.toString());
  }

  private void book() throws JSONException, IOException {
    String sPink = request.getParameter("pink");
    boolean pink = false;
    if ("on".equals(sPink)) {
      pink = true;
    }
    JSONObject user = new JSONObject(request.getParameter("user"));
    boolean status = false;
    String result = null;
    String number =
        getNearestCab(
            Float.parseFloat(user.get("lat").toString()),
            Float.parseFloat(user.get("lon").toString()),
            pink);
    if (number != null) {
      status = DBStore.book(number, user);
    }
    if (status == true) {
      result = "Cab booked !! Number: " + number;
    } else {
      result = "No cabs available";
    }
    response.getWriter().write(new JSONObject("{\"result\":\"" + result + "\"}").toString());
  }

  private String getNearestCab(float lat, float lon, boolean pink) throws JSONException {
    JSONArray cabs = DBStore.cabs(lat, lon, pink, null);
    if (cabs.length() > 0) {
      return cabs.getJSONObject(0).getString("number");
    } else {
      return null;
    }
  }
}
