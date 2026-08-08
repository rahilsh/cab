package in.rsh.cab.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class SecurityProblemWriter implements AuthenticationEntryPoint, AccessDeniedHandler {

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    write(response, 401, "Unauthorized", "Authentication is required", "unauthorized");
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      org.springframework.security.access.AccessDeniedException exception)
      throws IOException {
    write(response, 403, "Forbidden", "Access is denied", "forbidden");
  }

  private void write(
      HttpServletResponse response, int status, String title, String detail, String code)
      throws IOException {
    response.setStatus(status);
    response.setCharacterEncoding("UTF-8");
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response
        .getWriter()
        .write(
            "{\"type\":\"https://github.com/rahilsh/cab/problems/"
                + code
                + "\",\"title\":\""
                + title
                + "\",\"status\":"
                + status
                + ",\"detail\":\""
                + detail
                + "\",\"code\":\""
                + code
                + "\"}");
  }
}
