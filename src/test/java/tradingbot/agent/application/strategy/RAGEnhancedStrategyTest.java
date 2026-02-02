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
import tradingbot.agent.service.OrderPlacementService;
import tradingbot.agent.service.RAGService;

/**
 * Unit tests for RAGEnhancedStrategy
 * 
 * Tests the RAG-enhanced LLM strategy that combines:
 * - Market perception
 * - RAG-based reasoning with historical context
 * - Order placement
 */
@ExtendWith(MockitoExtension.class)
class RAGEnhancedStrategyTest {
    
    @Mock
    private RAGService ragService;
    
    @Mock
    private OrderPlacementService orderPlacementService;
    
    @InjectMocks
    private RAGEnhancedStrategy strategy;
    
    private Agent testAgent;
    private Reasoning mockReasoning;
    
    @BeforeEach
    void setUp() {
        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Maximize profits");
        testAgent = Agent.create("Test Agent", goal, "BTCUSDT", 10000.0);
        
        mockReasoning = new Reasoning(
            "Market is trending up",
            "Strong bullish momentum",
            "Low risk, good entry point",
            "BUY",
            85,
            Instant.now()
        );
    }
    
    @Test
    void testGetStrategyName() {
        assertEquals("RAG-Enhanced LLM", strategy.getStrategyName());
    }
    
    @Test
    void testExecuteIteration_SuccessfulBuyOrder() {
        // Given
        Order expectedOrder = Order.builder()
            .id(java.util.UUID.randomUUID().toString())
            .agentId(testAgent.getId().toString())
            .symbol("BTCUSDT")
            .direction(TradeDirection.LONG)
            .price(45000.0)
            .quantity(0.1)
            .stopLoss(44000.0)
            .takeProfit(46000.0)
            .status(Order.OrderStatus.PENDING)
            .createdAt(Instant.now())
            .build();
        
        when(ragService.generateReasoningWithRAG(any(Agent.class), any(ReasoningContext.class)))
            .thenReturn(mockReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(expectedOrder);
        
        // When
        strategy.executeIteration(testAgent);
        
        // Then
        verify(ragService).generateReasoningWithRAG(eq(testAgent), any(ReasoningContext.class));
        verify(orderPlacementService).processReasoning(eq(testAgent), any(Perception.class), eq(mockReasoning));
        
        // Verify agent state was updated
        assertEquals(mockReasoning, testAgent.getLastReasoning());
    }
    
    @Test
    void testExecuteIteration_HoldDecision() {
        // Given
        Reasoning holdReasoning = new Reasoning(
            "Market unclear",
            "Mixed signals",
            "High volatility",
            "HOLD",
            45,
            Instant.now()
        );
        
        when(ragService.generateReasoningWithRAG(any(Agent.class), any(ReasoningContext.class)))
            .thenReturn(holdReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(null);
        
        // When
        strategy.executeIteration(testAgent);
        
        // Then
        verify(ragService).generateReasoningWithRAG(eq(testAgent), any(ReasoningContext.class));
        verify(orderPlacementService).processReasoning(eq(testAgent), any(Perception.class), eq(holdReasoning));
        assertEquals(holdReasoning, testAgent.getLastReasoning());
    }
    
    @Test
    void testExecuteIteration_UpdatesAgentPerception() {
        // Given
        when(ragService.generateReasoningWithRAG(any(Agent.class), any(ReasoningContext.class)))
            .thenReturn(mockReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(null);
        
        // When
        strategy.executeIteration(testAgent);
        
        // Then
        assertNotNull(testAgent.getLastPerception());
        assertEquals("BTCUSDT", testAgent.getLastPerception().getSymbol());
        assertEquals(45000.0, testAgent.getLastPerception().getCurrentPrice());
    }
    
    @Test
    void testExecuteIteration_PassesCorrectReasoningContext() {
        // Given
        ArgumentCaptor<ReasoningContext> contextCaptor = ArgumentCaptor.forClass(ReasoningContext.class);
        
        when(ragService.generateReasoningWithRAG(any(Agent.class), contextCaptor.capture()))
            .thenReturn(mockReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(null);
        
        // When
        strategy.executeIteration(testAgent);
        
        // Then
        ReasoningContext capturedContext = contextCaptor.getValue();
        assertEquals(testAgent.getGoal(), capturedContext.getGoal());
        assertEquals("BTCUSDT", capturedContext.getTradingSymbol());
        assertEquals(10000.0, capturedContext.getCapital());
        assertNotNull(capturedContext.getPerception());
    }
    
    @Test
    void testExecuteIteration_SellOrder() {
        // Given
        Reasoning sellReasoning = new Reasoning(
            "Market overbought",
            "Bearish divergence detected",
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
            .quantity(0.1)
            .stopLoss(46000.0)
            .takeProfit(44000.0)
            .status(Order.OrderStatus.PENDING)
            .createdAt(Instant.now())
            .build();
        
        when(ragService.generateReasoningWithRAG(any(Agent.class), any(ReasoningContext.class)))
            .thenReturn(sellReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(expectedOrder);
        
        // When
        strategy.executeIteration(testAgent);
        
        // Then
        verify(orderPlacementService).processReasoning(eq(testAgent), any(Perception.class), eq(sellReasoning));
        assertEquals(sellReasoning, testAgent.getLastReasoning());
    }
    
    @Test
    void testExecuteIteration_HighConfidenceDecision() {
        // Given
        Reasoning highConfidenceReasoning = new Reasoning(
            "Strong bullish breakout",
            "Multiple confirmations",
            "Very low risk",
            "BUY",
            95,
            Instant.now()
        );
        
        when(ragService.generateReasoningWithRAG(any(Agent.class), any(ReasoningContext.class)))
            .thenReturn(highConfidenceReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(null);
        
        // When
        strategy.executeIteration(testAgent);
        
        // Then
        assertEquals(95, testAgent.getLastReasoning().getConfidence());
    }
    
    @Test
    void testExecuteIteration_LowConfidenceDecision() {
        // Given
        Reasoning lowConfidenceReasoning = new Reasoning(
            "Unclear signals",
            "Contradictory indicators",
            "High risk",
            "HOLD",
            25,
            Instant.now()
        );
        
        when(ragService.generateReasoningWithRAG(any(Agent.class), any(ReasoningContext.class)))
            .thenReturn(lowConfidenceReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(null);
        
        // When
        strategy.executeIteration(testAgent);
        
        // Then
        assertEquals(25, testAgent.getLastReasoning().getConfidence());
        verify(orderPlacementService).processReasoning(any(), any(), eq(lowConfidenceReasoning));
    }
    
    @Test
    void testExecuteIteration_MultipleIterations() {
        // Given
        when(ragService.generateReasoningWithRAG(any(Agent.class), any(ReasoningContext.class)))
            .thenReturn(mockReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(null);
        
        // When
        strategy.executeIteration(testAgent);
        int firstIterationCount = testAgent.getState().getIterationCount();
        
        strategy.executeIteration(testAgent);
        int secondIterationCount = testAgent.getState().getIterationCount();
        
        // Then
        assertTrue(secondIterationCount > firstIterationCount);
        verify(ragService, times(2)).generateReasoningWithRAG(any(Agent.class), any(ReasoningContext.class));
        verify(orderPlacementService, times(2)).processReasoning(any(), any(), any());
    }
    
    @Test
    void testExecuteIteration_WithDifferentSymbols() {
        // Given
        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Maximize profits");
        Agent ethAgent = Agent.create("ETH Agent", goal, "ETHUSDT", 5000.0);
        
        when(ragService.generateReasoningWithRAG(any(Agent.class), any(ReasoningContext.class)))
            .thenReturn(mockReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(null);
        
        // When
        strategy.executeIteration(ethAgent);
        
        // Then
        assertEquals("ETHUSDT", ethAgent.getLastPerception().getSymbol());
        verify(ragService).generateReasoningWithRAG(eq(ethAgent), any(ReasoningContext.class));
    }
    
    @Test
    void testExecuteIteration_PreservesAgentGoal() {
        // Given
        AgentGoal hedgeGoal = new AgentGoal(AgentGoal.GoalType.HEDGE_RISK, "Minimize risk");
        Agent hedgeAgent = Agent.create("Hedge Agent", hedgeGoal, "BTCUSDT", 10000.0);
        
        when(ragService.generateReasoningWithRAG(any(Agent.class), any(ReasoningContext.class)))
            .thenReturn(mockReasoning);
        when(orderPlacementService.processReasoning(any(Agent.class), any(Perception.class), any(Reasoning.class)))
            .thenReturn(null);
        
        ArgumentCaptor<ReasoningContext> contextCaptor = ArgumentCaptor.forClass(ReasoningContext.class);
        
        // When
        strategy.executeIteration(hedgeAgent);
        
        // Then
        verify(ragService).generateReasoningWithRAG(eq(hedgeAgent), contextCaptor.capture());
        assertEquals(hedgeGoal, contextCaptor.getValue().getGoal());
    }
}
