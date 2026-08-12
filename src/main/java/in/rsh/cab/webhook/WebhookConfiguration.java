package in.rsh.cab.webhook;

import java.net.InetAddress;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebhookConfiguration {

  @Bean
  WebhookSecurity webhookSecurity() {
    return new WebhookSecurity(host -> List.of(InetAddress.getAllByName(host)));
  }
}
