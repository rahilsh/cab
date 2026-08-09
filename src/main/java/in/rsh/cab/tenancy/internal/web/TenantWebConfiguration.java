package in.rsh.cab.tenancy.internal.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class TenantWebConfiguration implements WebMvcConfigurer {

  private final TenantSelectionInterceptor tenantSelectionInterceptor;

  public TenantWebConfiguration(TenantSelectionInterceptor tenantSelectionInterceptor) {
    this.tenantSelectionInterceptor = tenantSelectionInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(tenantSelectionInterceptor)
        .addPathPatterns("/api/v1/**")
        .excludePathPatterns("/api/v1/tenants");
  }
}
