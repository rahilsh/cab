package in.rsh.cab.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @Test
  void preservesClientCorrelationIdAndClearsMdc() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.HEADER_NAME, "request-123");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain =
        (ignoredRequest, ignoredResponse) -> assertEquals("request-123", MDC.get("correlationId"));

    filter.doFilter(request, response, chain);

    assertEquals("request-123", response.getHeader(CorrelationIdFilter.HEADER_NAME));
    assertNull(MDC.get("correlationId"));
  }

  @Test
  void generatesCorrelationIdWhenMissingOrBlank() throws Exception {
    for (String value : new String[] {null, "  "}) {
      MockHttpServletRequest request = new MockHttpServletRequest();
      if (value != null) {
        request.addHeader(CorrelationIdFilter.HEADER_NAME, value);
      }
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {});

      String generated = response.getHeader(CorrelationIdFilter.HEADER_NAME);
      assertNotNull(generated);
      assertFalse(generated.isBlank());
    }
  }
}
