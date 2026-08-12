package in.rsh.cab.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain apiSecurity(HttpSecurity http, SecurityProblemWriter problems)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(
                        csp ->
                            csp.policyDirectives(
                                "default-src 'self'; script-src 'self' 'unsafe-inline'; "
                                    + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
                                    + "frame-ancestors 'none'"))
                    .referrerPolicy(
                        policy ->
                            policy.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                    .addHeaderWriter(
                        new StaticHeadersWriter(
                            "Permissions-Policy", "camera=(), microphone=(), geolocation=()")))
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(problems).accessDeniedHandler(problems))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/actuator/prometheus", "/actuator/info")
                    .hasAuthority("SCOPE_observability.read")
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/tenants")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/tenants")
                    .hasAuthority("SCOPE_platform.admin")
                    .requestMatchers("/api/v1/payment-providers/*/accounts/*/events")
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/current-tenant",
                        "/api/v1/service-areas/**",
                        "/api/v1/routes/**",
                        "/api/v1/rider/**",
                        "/api/v1/drivers/**",
                        "/api/v1/vehicles/**",
                        "/api/v1/driver/shifts/**",
                        "/api/v1/products/**",
                        "/api/v1/pricing-rules/**",
                        "/api/v1/admin/audit-events/**",
                        "/api/v1/quotes/**",
                        "/api/v1/rides/**",
                        "/api/v1/notification-preferences/**",
                        "/api/v1/support/**",
                        "/api/v1/safety/**",
                        "/api/v1/admin/webhook-subscriptions/**",
                        "/api/v1/dispatch/**",
                        "/api/v1/driver/location",
                        "/api/v1/driver/offers/**",
                        "/api/v1/driver/rides/**")
                    .authenticated()
                    .requestMatchers("/api/v1/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> {}))
        .build();
  }
}
