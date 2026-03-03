package tradingbot.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Primary;

import tradingbot.agent.domain.execution.OrderExecutionGateway;
import tradingbot.agent.impl.execution.LiveOrderGateway;
import tradingbot.agent.impl.execution.PaperTradingOrderGateway;
import tradingbot.bot.service.FuturesExchangeService;

@Configuration
public class AgentExecutionConfig {

    @Bean
    @Primary
    @Profile("!live")
    public OrderExecutionGateway paperTradingOrderGateway(FuturesExchangeService exchange) {
        return new PaperTradingOrderGateway(exchange, null);
    }
}
