package tradingbot.bot.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;

import tradingbot.agent.api.dto.AgentMapper;
import tradingbot.agent.application.AgentService;
import tradingbot.bot.FuturesTradingBot;
import tradingbot.bot.controller.config.TradingBotControllerValidationTestConfig;
import tradingbot.bot.messaging.EventPublisher;
import tradingbot.bot.service.BotCacheService;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.strategy.analyzer.SentimentAnalyzer;
import tradingbot.config.InstanceConfig;

/**
 * Abstract base class for controller validation tests.
 * Provides common Spring Boot test setup and validation test utilities.
 */
@SpringBootTest(
    classes = TradingBotControllerValidationTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc(addFilters = false)
public abstract class AbstractControllerValidationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    protected BotCacheService botCacheService;

    @MockitoBean
    protected FuturesTradingBot tradingBot;

    @MockitoBean
    protected EventPublisher eventPublisher;

    @MockitoBean
    protected InstanceConfig instanceConfig;

    @MockitoBean
    protected FuturesExchangeService exchangeService;

    @MockitoBean
    protected SentimentAnalyzer sentimentAnalyzer;

    @MockitoBean
    protected AgentService agentService;

    @MockitoBean
    protected AgentMapper agentMapper;

    /**
     * Performs a POST request and expects validation failure with field errors.
     */
    protected ResultActions performValidationTest(String url, Object requestBody, String expectedField, String expectedMessage) throws Exception {
        return mockMvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.title").value("Validation Failed"))
               .andExpect(jsonPath("$.fieldErrors." + expectedField + "[0]").value(expectedMessage));
    }

    /**
     * Performs a request and expects it to pass validation (not return 400 with validation errors).
     */
    protected ResultActions performValidRequestTest(String url, Object requestBody) throws Exception {
        return mockMvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
               .andExpect(result -> {
                   int status = result.getResponse().getStatus();
                   if (status == 400) {
                       String body = result.getResponse().getContentAsString();
                       if (body.contains("Validation Failed") || body.contains("Constraint Violation")) {
                           throw new AssertionError("Valid request should not fail validation");
                       }
                   }
               });
    }
}