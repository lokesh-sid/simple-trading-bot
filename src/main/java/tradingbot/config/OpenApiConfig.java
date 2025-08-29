package tradingbot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for the Simple Trading Bot API
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Simple Trading Bot API")
                        .version("1.0.0")
                        .description("REST API for managing and monitoring a cryptocurrency futures trading bot with rate limiting and resilience features")
                        .contact(new Contact()
                                .name("Trading Bot Team")
                                .email("support@tradingbot.com")
                                .url("https://github.com/your-org/simple-trading-bot"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local development server"),
                        new Server()
                                .url("https://api.tradingbot.com")
                                .description("Production server")));
    }
}
