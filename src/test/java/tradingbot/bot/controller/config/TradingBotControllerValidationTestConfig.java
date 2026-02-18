package tradingbot.bot.controller.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import net.devh.boot.grpc.server.autoconfigure.GrpcServerAutoConfiguration;
import net.devh.boot.grpc.server.autoconfigure.GrpcServerFactoryAutoConfiguration;
import tradingbot.agent.api.AgentController;
import tradingbot.bot.controller.TradingBotController;
import tradingbot.bot.controller.exception.GlobalExceptionHandler;

@TestConfiguration
@EnableAutoConfiguration(exclude = {
    KafkaAutoConfiguration.class,
    RedisAutoConfiguration.class,
    RedisRepositoriesAutoConfiguration.class,
    // HibernateJpaAutoConfiguration.class,  // Allow JPA to configure so mocks can work if they trigger it
    DataSourceAutoConfiguration.class,
    GrpcServerAutoConfiguration.class,
    GrpcServerFactoryAutoConfiguration.class
})
@EntityScan(basePackages = "tradingbot")
@EnableJpaRepositories(basePackages = "tradingbot")
@ComponentScan(
    basePackages = {"tradingbot.bot.controller", "tradingbot.agent.api"},
    useDefaultFilters = false,
    includeFilters = {
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = {
                TradingBotController.class,
                GlobalExceptionHandler.class,
                AgentController.class
            }
        )
    }
)
public class TradingBotControllerValidationTestConfig {
}
