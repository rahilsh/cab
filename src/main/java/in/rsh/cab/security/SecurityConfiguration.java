package in.rsh.cab.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/actuator/health/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/tenants")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/tenants")
                    .hasAuthority("SCOPE_platform.admin")
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
