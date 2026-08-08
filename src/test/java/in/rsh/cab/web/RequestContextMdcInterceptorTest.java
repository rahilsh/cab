package in.rsh.cab.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import in.rsh.cab.tenancy.TenantContext;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

class RequestContextMdcInterceptorTest {

  private final RequestContextMdcInterceptor interceptor = new RequestContextMdcInterceptor();

  @AfterEach
  void clear() {
    TenantContext.clear();
    SecurityContextHolder.clearContext();
    MDC.clear();
  }

  @Test
  void addsAuthorizedTenantAndActorAndClearsThem() {
    TenantContext context =
        new TenantContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Set.of());
    TenantContext.set(context);

    interceptor.preHandle(
        new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

    assertEquals(context.tenantId().toString(), MDC.get("tenantId"));
    assertEquals(context.accountId().toString(), MDC.get("actorId"));
    interceptor.afterCompletion(
        new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);
    assertNull(MDC.get("tenantId"));
    assertNull(MDC.get("actorId"));
  }

  @Test
  void hashesRawOidcIdentityWhenNoTenantIsSelected() {
    Jwt jwt =
        Jwt.withTokenValue("secret-token")
            .header("alg", "none")
            .issuer("https://issuer.example")
            .subject("sensitive-subject")
            .build();
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt, null));

    interceptor.preHandle(
        new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

    assertNotEquals("sensitive-subject", MDC.get("actorId"));
    assertEquals(21, MDC.get("actorId").length());
  }
}
