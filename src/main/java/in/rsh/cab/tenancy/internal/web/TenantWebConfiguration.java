package in.rsh.cab.tenancy.internal.web;

import in.rsh.cab.ratelimit.RateLimitInterceptor;
import in.rsh.cab.web.RequestContextMdcInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class TenantWebConfiguration implements WebMvcConfigurer {

  private final TenantSelectionInterceptor tenantSelectionInterceptor;
  private final RequestContextMdcInterceptor requestContextMdcInterceptor;
  private final RateLimitInterceptor rateLimitInterceptor;

  public TenantWebConfiguration(
      TenantSelectionInterceptor tenantSelectionInterceptor,
      RequestContextMdcInterceptor requestContextMdcInterceptor,
      RateLimitInterceptor rateLimitInterceptor) {
    this.tenantSelectionInterceptor = tenantSelectionInterceptor;
    this.requestContextMdcInterceptor = requestContextMdcInterceptor;
    this.rateLimitInterceptor = rateLimitInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(tenantSelectionInterceptor)
        .addPathPatterns("/api/v1/**")
        .excludePathPatterns("/api/v1/tenants", "/api/v1/payment-providers/**");
    registry
        .addInterceptor(requestContextMdcInterceptor)
        .addPathPatterns("/api/v1/**")
        .excludePathPatterns("/api/v1/payment-providers/**");
    registry
        .addInterceptor(rateLimitInterceptor)
        .addPathPatterns("/api/v1/**")
        .excludePathPatterns("/api/v1/tenants", "/api/v1/payment-providers/**");
  }
}
