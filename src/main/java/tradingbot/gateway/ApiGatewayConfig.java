package tradingbot.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * API Gateway configuration for routing, load balancing, and request transformation
 * 
 * Provides centralized gateway functionality with:
 * - Request/Response transformation
 * - Rate limiting via Resilience4j
 * - Circuit breaker pattern
 * - Load balancing
 * - Security filtering
 */
@Configuration
public class ApiGatewayConfig {
    
    @Bean
    public RestTemplate gatewayRestTemplate() {
        return new RestTemplate();
    }
    
    @Bean
    public GatewayService gatewayService(RestTemplate gatewayRestTemplate) {
        return new GatewayService(gatewayRestTemplate);
    }
}
