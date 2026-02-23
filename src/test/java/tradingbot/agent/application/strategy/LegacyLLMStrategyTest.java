package tradingbot.agent.application.strategy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentGoal;
import tradingbot.agent.domain.model.Order;
import tradingbot.agent.domain.model.Perception;
import tradingbot.agent.domain.model.Reasoning;
import tradingbot.agent.domain.model.ReasoningContext;
import tradingbot.agent.domain.model.TradeDirection;
import tradingbot.agent.infrastructure.llm.LLMProvider;
import tradingbot.agent.service.OrderPlacementService;

/**
 * Unit tests for LegacyLLMStrategy
 * 
 * Tests the legacy strategy that uses direct LLM calls:
 * - Market perception
 * - Direct LLM reasoning
 * - Order placement
 * - No RAG or tool use
 */
@ExtendWith(MockitoExtension.class)
class LegacyLLMStrategyTest {
    
    @Mock
    private LLMProvider llmProvider;
    
    @Mock
    private OrderPlacementService orderPlacementService;
    
    @InjectMocks
    private LegacyLLMStrategy strategy;
    
    private Agent testAgent;
    private Reasoning mockReasoning;
    
    @BeforeEach
    void setUp() {
        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Maximize profits");
        testAgent = Agent.create("Legacy Agent", goal, "BTCUSDT", 10000.0);
        
        mockReasoning = new Reasoning(
            "Market trending upward",
            "Strong momentum indicators",
            "Low risk environment",
            "BUY",
            80,
            Instant.now()
        );
    }
    
    @Test
    void testGetStrategyName() {
        assertEquals("Legacy LLM", strategy.getStrategyName());
    }
    
    @Test
    void testExecuteIteration_CallsLLMProvider() {
        // Given
        when(llmProvider.generateReasoning(any(ReasoningContext.class)))
            .thenReturn(mockReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(null);
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        verify(llmProvider).generateReasoning(any(ReasoningContext.class));
    }
    
    @Test
    void testExecuteIteration_UpdatesAgentPerception() {
        // Given
        when(llmProvider.generateReasoning(any(ReasoningContext.class)))
            .thenReturn(mockReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(null);
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        assertNotNull(testAgent.getLastPerception());
        assertEquals("BTCUSDT", testAgent.getLastPerception().getSymbol());
        assertEquals(45000.0, testAgent.getLastPerception().getCurrentPrice());
    }
    
    @Test
    void testExecuteIteration_UpdatesAgentReasoning() {
        // Given
        when(llmProvider.generateReasoning(any(ReasoningContext.class)))
            .thenReturn(mockReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(null);
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        assertEquals(mockReasoning, testAgent.getLastReasoning());
        assertEquals("BUY", testAgent.getLastReasoning().getRecommendation());
        assertEquals(80, testAgent.getLastReasoning().getConfidence());
    }
    
    @Test
    void testExecuteIteration_PlacesBuyOrder() {
        // Given
        Order expectedOrder = Order.builder()
            .id(java.util.UUID.randomUUID().toString())
            .agentId(testAgent.getId().toString())
            .symbol("BTCUSDT")
            .direction(TradeDirection.LONG)
            .price(45000.0)
            .quantity(0.2)
            .stopLoss(44000.0)
            .takeProfit(46000.0)
            .status(Order.OrderStatus.PENDING)
            .createdAt(Instant.now())
            .build();
        
        when(llmProvider.generateReasoning(any(ReasoningContext.class)))
            .thenReturn(mockReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(expectedOrder);
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        verify(orderPlacementService).processReasoning(
            eq(testAgent),
            any(Perception.class),
            eq(mockReasoning)
        );
    }
    
    @Test
    void testExecuteIteration_PlacesSellOrder() {
        // Given
        Reasoning sellReasoning = new Reasoning(
            "Market overbought",
            "Bearish signals detected",
            "Medium risk",
            "SELL",
            75,
            Instant.now()
        );
        
        Order expectedOrder = Order.builder()
            .id(java.util.UUID.randomUUID().toString())
            .agentId(testAgent.getId().toString())
            .symbol("BTCUSDT")
            .direction(TradeDirection.SHORT)
            .price(45000.0)
            .quantity(0.15)
            .stopLoss(46000.0)
            .takeProfit(44000.0)
            .status(Order.OrderStatus.PENDING)
            .createdAt(Instant.now())
            .build();
        
        when(llmProvider.generateReasoning(any(ReasoningContext.class)))
            .thenReturn(sellReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(expectedOrder);
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        verify(orderPlacementService).processReasoning(
            eq(testAgent),
            any(Perception.class),
            eq(sellReasoning)
        );
        assertEquals("SELL", testAgent.getLastReasoning().getRecommendation());
    }
    
    @Test
    void testExecuteIteration_HoldDecision() {
        // Given
        Reasoning holdReasoning = new Reasoning(
            "Market unclear",
            "Conflicting signals",
            "High volatility",
            "HOLD",
            40,
            Instant.now()
        );
        
        when(llmProvider.generateReasoning(any(ReasoningContext.class)))
            .thenReturn(holdReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(null);
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        verify(orderPlacementService).processReasoning(any(), any(), eq(holdReasoning));
        assertEquals("HOLD", testAgent.getLastReasoning().getRecommendation());
    }
    
    @Test
    void testExecuteIteration_PassesCorrectReasoningContext() {
        // Given
        ArgumentCaptor<ReasoningContext> contextCaptor = ArgumentCaptor.forClass(ReasoningContext.class);
        
        when(llmProvider.generateReasoning(contextCaptor.capture()))
            .thenReturn(mockReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(null);
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        ReasoningContext capturedContext = contextCaptor.getValue();
        assertEquals(testAgent.getGoal(), capturedContext.getGoal());
        assertEquals("BTCUSDT", capturedContext.getTradingSymbol());
        assertEquals(10000.0, capturedContext.getCapital());
        assertNotNull(capturedContext.getPerception());
    }
    
    @Test
    void testExecuteIteration_HighConfidenceReasoning() {
        // Given
        Reasoning highConfidenceReasoning = new Reasoning(
            "Clear bullish breakout",
            "Multiple confirmations",
            "Very low risk",
            "BUY",
            95,
            Instant.now()
        );
        
        when(llmProvider.generateReasoning(any(ReasoningContext.class)))
            .thenReturn(highConfidenceReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(null);
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        assertEquals(95, testAgent.getLastReasoning().getConfidence());
    }
    
    @Test
    void testExecuteIteration_LowConfidenceReasoning() {
        // Given
        Reasoning lowConfidenceReasoning = new Reasoning(
            "Uncertain signals",
            "Contradictory data",
            "High risk",
            "HOLD",
            20,
            Instant.now()
        );
        
        when(llmProvider.generateReasoning(any(ReasoningContext.class)))
            .thenReturn(lowConfidenceReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(null);
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        assertEquals(20, testAgent.getLastReasoning().getConfidence());
    }
    
    @Test
    void testExecuteIteration_MultipleIterations() {
        // Given
        when(llmProvider.generateReasoning(any(ReasoningContext.class)))
            .thenReturn(mockReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(null);
        
        // When
        strategy.executeIteration(testAgent, null);
        int firstCount = testAgent.getState().getIterationCount();
        
        strategy.executeIteration(testAgent, null);
        int secondCount = testAgent.getState().getIterationCount();
        
        strategy.executeIteration(testAgent, null);
        int thirdCount = testAgent.getState().getIterationCount();
        
        // Then
        assertTrue(secondCount > firstCount);
        assertTrue(thirdCount > secondCount);
        verify(llmProvider, times(3)).generateReasoning(any(ReasoningContext.class));
        verify(orderPlacementService, times(3)).processReasoning(any(), any(), any());
    }
    
    @Test
    void testExecuteIteration_DifferentTradingSymbols() {
        // Given
        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "ETH trading");
        Agent ethAgent = Agent.create("ETH Agent", goal, "ETHUSDT", 5000.0);
        
        ArgumentCaptor<ReasoningContext> contextCaptor = ArgumentCaptor.forClass(ReasoningContext.class);
        
        when(llmProvider.generateReasoning(contextCaptor.capture()))
            .thenReturn(mockReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(null);
        
        // When
        strategy.executeIteration(ethAgent, null);
        
        // Then
        assertEquals("ETHUSDT", contextCaptor.getValue().getTradingSymbol());
        assertEquals("ETHUSDT", ethAgent.getLastPerception().getSymbol());
    }
    
    @Test
    void testExecuteIteration_DifferentAgentGoals() {
        // Given
        AgentGoal hedgeGoal = new AgentGoal(AgentGoal.GoalType.HEDGE_RISK, "Risk mitigation");
        Agent hedgeAgent = Agent.create("Hedge Agent", hedgeGoal, "BTCUSDT", 10000.0);
        
        ArgumentCaptor<ReasoningContext> contextCaptor = ArgumentCaptor.forClass(ReasoningContext.class);
        
        when(llmProvider.generateReasoning(contextCaptor.capture()))
            .thenReturn(mockReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(null);
        
        // When
        strategy.executeIteration(hedgeAgent, null);
        
        // Then
        assertEquals(hedgeGoal, contextCaptor.getValue().getGoal());
    }
    
    @Test
    void testExecuteIteration_DifferentCapitalAmounts() {
        // Given
        double largeCapital = 50000.0;
        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Large account");
        Agent largeAgent = Agent.create("Large Agent", goal, "BTCUSDT", largeCapital);
        
        ArgumentCaptor<ReasoningContext> contextCaptor = ArgumentCaptor.forClass(ReasoningContext.class);
        
        when(llmProvider.generateReasoning(contextCaptor.capture()))
            .thenReturn(mockReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(null);
        
        // When
        strategy.executeIteration(largeAgent, null);
        
        // Then
        assertEquals(largeCapital, contextCaptor.getValue().getCapital());
    }
    
    @Test
    void testExecuteIteration_PreservesPerceptionTimestamp() {
        // Given
        when(llmProvider.generateReasoning(any(ReasoningContext.class)))
            .thenReturn(mockReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(null);
        
        Instant before = Instant.now();
        
        // When
        strategy.executeIteration(testAgent, null);
        
        Instant after = Instant.now();
        
        // Then
        Instant perceptionTime = testAgent.getLastPerception().getTimestamp();
        assertTrue(perceptionTime.isAfter(before.minusSeconds(1)));
        assertTrue(perceptionTime.isBefore(after.plusSeconds(1)));
    }
    
    @Test
    void testExecuteIteration_IncrementsIterationCount() {
        // Given
        when(llmProvider.generateReasoning(any(ReasoningContext.class)))
            .thenReturn(mockReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(null);
        
        int initialCount = testAgent.getState().getIterationCount();
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        assertTrue(testAgent.getState().getIterationCount() > initialCount);
    }
}
