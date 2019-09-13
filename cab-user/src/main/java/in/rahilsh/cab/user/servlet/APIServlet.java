package in.rahilsh.cab.user.servlet;

import in.rahilsh.cab.user.app.FuberApp;
import in.rahilsh.cab.user.constants.FuberConstants;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONException;

/**
 * Servlet implementation class APIServlet
 */
public class APIServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;

  /**
   * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
   */
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    doPost(request, response);
  }

  /**
   * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
   */
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    response.setContentType(FuberConstants.JSON_MIMETYPE);
    try {
      new FuberApp(request, response);
    } catch (JSONException e) {
      // TODO: Add log statement
    }
  }
}
