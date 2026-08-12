package in.rsh.cab.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

  private static final String BEARER = "bearerAuth";

  @Bean
  OpenAPI marketplaceOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Cab Marketplace API")
                .version("v1")
                .description(
                    "Tenant-scoped marketplace operations. Authenticate with a bearer token and "
                        + "select an authorized tenant with X-Tenant-ID."))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }

  @Bean
  OpenApiCustomizer marketplaceHeaders() {
    return openApi ->
        openApi
            .getPaths()
            .forEach(
                (path, item) ->
                    item.readOperations()
                        .forEach(
                            operation -> {
                              boolean providerCallback =
                                  path.startsWith("/api/v1/payment-providers/");
                              if (!providerCallback) {
                                operation.addSecurityItem(
                                    new SecurityRequirement().addList(BEARER));
                              }
                              if (!path.equals("/api/v1/tenants") && !providerCallback) {
                                addHeader(operation, "X-Tenant-ID", "Authorized tenant UUID", true);
                              }
                              if (path.equals("/api/v1/quotes") || path.equals("/api/v1/rides")) {
                                addHeader(
                                    operation,
                                    "Idempotency-Key",
                                    "Unique key scoped to tenant, actor, and operation",
                                    true);
                              }
                            }));
  }

  private void addHeader(
      io.swagger.v3.oas.models.Operation operation,
      String name,
      String description,
      boolean required) {
    boolean exists =
        operation.getParameters() != null
            && operation.getParameters().stream()
                .anyMatch(parameter -> name.equalsIgnoreCase(parameter.getName()));
    if (!exists) {
      operation.addParametersItem(header(name, description, required));
    }
  }

  private Parameter header(String name, String description, boolean required) {
    return new Parameter()
        .in("header")
        .name(name)
        .description(description)
        .required(required)
        .schema(new io.swagger.v3.oas.models.media.StringSchema());
  }
}
