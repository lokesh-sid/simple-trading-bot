package tradingbot.agent.application.strategy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentGoal;
import tradingbot.agent.domain.model.Reasoning;
import tradingbot.agent.domain.repository.AgentRepository;
import tradingbot.agent.infrastructure.llm.LLMProvider;
import tradingbot.agent.infrastructure.repository.PositionRepository;
import tradingbot.agent.service.OrderPlacementService;
import tradingbot.agent.service.RAGService;
import tradingbot.agent.service.TradingAgentService;

/**
 * Integration tests for Agent Strategy Pattern
 * 
 * Tests the dynamic strategy selection and switching in AgentOrchestrator:
 * - Configuration-based strategy selection
 * - Runtime strategy switching
 * - Strategy execution with proper dependencies
 * - End-to-end agent iteration flow
 */
@SpringBootTest(classes = {
    LangChain4jStrategy.class,
    RAGEnhancedStrategy.class,
    LegacyLLMStrategy.class
}, properties = {
    "agent.orchestrator.enabled=false",
    "spring.main.allow-bean-definition-overriding=true"
})
@ActiveProfiles("test")
class AgentStrategyIntegrationTest {
    
    @Autowired
    private LangChain4jStrategy langChain4jStrategy;
    
    @Autowired
    private RAGEnhancedStrategy ragEnhancedStrategy;
    
    @Autowired
    private LegacyLLMStrategy legacyLLMStrategy;
    
    @MockitoBean
    private AgentRepository agentRepository;
    
    @MockitoBean
    private TradingAgentService tradingAgentService;
    
    @MockitoBean
    private RAGService ragService;
    
    @MockitoBean
    private LLMProvider llmProvider;
    
    @MockitoBean
    private PositionRepository positionRepository;

    @MockitoBean
    private OrderPlacementService orderPlacementService;
    
    private Agent testAgent;
    
    @BeforeEach
    void setUp() {
        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Test goal");
        testAgent = Agent.create("Integration Test Agent", goal, "BTCUSDT", 10000.0);
        
        // Mock repository
        when(agentRepository.save(any(Agent.class))).thenReturn(testAgent);
    }
    
    @Test
    void testLangChain4jStrategyIsWired() {
        // Verify strategy beans are properly created
        assertNotNull(langChain4jStrategy);
        assertEquals("LangChain4j Agentic", langChain4jStrategy.getStrategyName());
    }
    
    @Test
    void testRAGEnhancedStrategyIsWired() {
        assertNotNull(ragEnhancedStrategy);
        assertEquals("RAG-Enhanced LLM", ragEnhancedStrategy.getStrategyName());
    }
    
    @Test
    void testLegacyLLMStrategyIsWired() {
        assertNotNull(legacyLLMStrategy);
        assertEquals("Legacy LLM", legacyLLMStrategy.getStrategyName());
    }
    
    @Test
    void testLangChain4jStrategy_ExecutesWithDependencies() {
        // Given
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyDouble(), anyInt(), anyString()))
            .thenReturn("Decision: BUY. Confidence: 85%");
        
        // When
        assertDoesNotThrow(() -> langChain4jStrategy.executeIteration(testAgent));
        
        // Then
        verify(tradingAgentService).analyzeAndDecide(
            eq("BTCUSDT"),
            anyString(),
            eq(10000.0),
            anyInt(),
            anyString()
        );
        assertNotNull(testAgent.getLastReasoning());
    }
    
    @Test
    void testRAGEnhancedStrategy_ExecutesWithDependencies() {
        // Given
        when(ragService.generateReasoningWithRAG(any(), any()))
            .thenReturn(createMockReasoning());
        when(orderPlacementService.processReasoning(any(), any(), any()))
            .thenReturn(null);
        
        // When
        assertDoesNotThrow(() -> ragEnhancedStrategy.executeIteration(testAgent));
        
        // Then
        verify(ragService).generateReasoningWithRAG(eq(testAgent), any());
        verify(orderPlacementService).processReasoning(any(), any(), any());
        assertNotNull(testAgent.getLastPerception());
        assertNotNull(testAgent.getLastReasoning());
    }
    
    @Test
    void testLegacyLLMStrategy_ExecutesWithDependencies() {
        // Given
        when(llmProvider.generateReasoning(any()))
            .thenReturn(createMockReasoning());
        when(orderPlacementService.processReasoning(any(), any(), any()))
            .thenReturn(null);
        
        // When
        assertDoesNotThrow(() -> legacyLLMStrategy.executeIteration(testAgent));
        
        // Then
        verify(llmProvider).generateReasoning(any());
        verify(orderPlacementService).processReasoning(any(), any(), any());
        assertNotNull(testAgent.getLastPerception());
        assertNotNull(testAgent.getLastReasoning());
    }
    
    @Test
    void testAllStrategies_UpdateAgentState() {
        // Test LangChain4j
        when(tradingAgentService.analyzeAndDecide(anyString(), anyString(), anyDouble(), anyInt(), anyString()))
            .thenReturn("BUY");
        
        langChain4jStrategy.executeIteration(testAgent);
        int langchainIterations = testAgent.getState().getIterationCount();
        assertTrue(langchainIterations > 0);
        
        // Test RAG Enhanced
        when(ragService.generateReasoningWithRAG(any(), any()))
            .thenReturn(createMockReasoning());
        when(orderPlacementService.processReasoning(any(), any(), any()))
            .thenReturn(null);
        
        ragEnhancedStrategy.executeIteration(testAgent);
        int ragIterations = testAgent.getState().getIterationCount();
        assertTrue(ragIterations > langchainIterations);
        
        // Test Legacy
        when(llmProvider.generateReasoning(any()))
            .thenReturn(createMockReasoning());
        
        legacyLLMStrategy.executeIteration(testAgent);
        int legacyIterations = testAgent.getState().getIterationCount();
        assertTrue(legacyIterations > ragIterations);
    }
    
    @Test
    void testStrategiesAreIndependent() {
        // Each strategy should work independently without affecting others
        
        // Execute with LangChain4j
        when(tradingAgentService.analyzeAndDecide(anyString(), anyString(), anyDouble(), anyInt(), anyString()))
            .thenReturn("BUY");
        langChain4jStrategy.executeIteration(testAgent);
        
        // Execute with RAG Enhanced
        when(ragService.generateReasoningWithRAG(any(), any()))
            .thenReturn(createMockReasoning());
        when(orderPlacementService.processReasoning(any(), any(), any()))
            .thenReturn(null);
        ragEnhancedStrategy.executeIteration(testAgent);
        
        // Execute with Legacy
        when(llmProvider.generateReasoning(any()))
            .thenReturn(createMockReasoning());
        legacyLLMStrategy.executeIteration(testAgent);
        
        // All should have executed successfully
        assertTrue(testAgent.getState().getIterationCount() >= 3);
    }
    
    @Test
    void testLangChain4jStrategy_WithDifferentAgents() {
        // Test with BTC agent
        Agent btcAgent = Agent.create("BTC Agent", 
            new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "BTC"), 
            "BTCUSDT", 10000.0);
        
        when(tradingAgentService.analyzeAndDecide(anyString(), anyString(), anyDouble(), anyInt(), anyString()))
            .thenReturn("BUY");
        
        langChain4jStrategy.executeIteration(btcAgent);
        
        // Test with ETH agent
        Agent ethAgent = Agent.create("ETH Agent",
            new AgentGoal(AgentGoal.GoalType.HEDGE_RISK, "ETH"),
            "ETHUSDT", 5000.0);
        
        langChain4jStrategy.executeIteration(ethAgent);
        
        // Both should execute successfully
        assertTrue(btcAgent.getState().getIterationCount() > 0);
        assertTrue(ethAgent.getState().getIterationCount() > 0);
    }
    
    @Test
    void testRAGEnhancedStrategy_WithDifferentAgents() {
        // Test with multiple agents
        Agent agent1 = Agent.create("Agent 1",
            new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Goal 1"),
            "BTCUSDT", 10000.0);
        
        Agent agent2 = Agent.create("Agent 2",
            new AgentGoal(AgentGoal.GoalType.HEDGE_RISK, "Goal 2"),
            "ETHUSDT", 5000.0);
        
        when(ragService.generateReasoningWithRAG(any(), any()))
            .thenReturn(createMockReasoning());
        when(orderPlacementService.processReasoning(any(), any(), any()))
            .thenReturn(null);
        
        ragEnhancedStrategy.executeIteration(agent1);
        ragEnhancedStrategy.executeIteration(agent2);
        
        assertTrue(agent1.getState().getIterationCount() > 0);
        assertTrue(agent2.getState().getIterationCount() > 0);
    }
    
    @Test
    void testLegacyLLMStrategy_WithDifferentAgents() {
        Agent agent1 = Agent.create("Legacy Agent 1",
            new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Goal 1"),
            "BTCUSDT", 20000.0);
        
        Agent agent2 = Agent.create("Legacy Agent 2",
            new AgentGoal(AgentGoal.GoalType.HEDGE_RISK, "Goal 2"),
            "SOLUSDT", 3000.0);
        
        when(llmProvider.generateReasoning(any()))
            .thenReturn(createMockReasoning());
        when(orderPlacementService.processReasoning(any(), any(), any()))
            .thenReturn(null);
        
        legacyLLMStrategy.executeIteration(agent1);
        legacyLLMStrategy.executeIteration(agent2);
        
        assertTrue(agent1.getState().getIterationCount() > 0);
        assertTrue(agent2.getState().getIterationCount() > 0);
    }
    
    @Test
    void testStrategiesHandleExceptionsGracefully() {
        // LangChain4j with exception
        when(tradingAgentService.analyzeAndDecide(anyString(), anyString(), anyDouble(), anyInt(), anyString()))
            .thenThrow(new RuntimeException("LLM error"));
        
        assertThrows(RuntimeException.class, () -> 
            langChain4jStrategy.executeIteration(testAgent));
        
        // RAG Enhanced with exception (should be caught internally)
        when(ragService.generateReasoningWithRAG(any(), any()))
            .thenThrow(new RuntimeException("RAG error"));
        
        assertThrows(RuntimeException.class, () ->
            ragEnhancedStrategy.executeIteration(testAgent));
        
        // Legacy with exception
        when(llmProvider.generateReasoning(any()))
            .thenThrow(new RuntimeException("LLM error"));
        
        assertThrows(RuntimeException.class, () ->
            legacyLLMStrategy.executeIteration(testAgent));
    }
    
    @Test
    void testStrategies_PreserveAgentGoals() {
        AgentGoal maximizeProfit = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Max profit");
        Agent profitAgent = Agent.create("Profit Agent", maximizeProfit, "BTCUSDT", 10000.0);
        
        AgentGoal hedgeRisk = new AgentGoal(AgentGoal.GoalType.HEDGE_RISK, "Hedge risk");
        Agent hedgeAgent = Agent.create("Hedge Agent", hedgeRisk, "BTCUSDT", 10000.0);
        
        when(tradingAgentService.analyzeAndDecide(anyString(), anyString(), anyDouble(), anyInt(), anyString()))
            .thenReturn("BUY");
        when(ragService.generateReasoningWithRAG(any(), any()))
            .thenReturn(createMockReasoning());
        when(llmProvider.generateReasoning(any()))
            .thenReturn(createMockReasoning());
        when(orderPlacementService.processReasoning(any(), any(), any()))
            .thenReturn(null);
        
        // Execute with different strategies
        langChain4jStrategy.executeIteration(profitAgent);
        assertEquals(maximizeProfit, profitAgent.getGoal());
        
        ragEnhancedStrategy.executeIteration(hedgeAgent);
        assertEquals(hedgeRisk, hedgeAgent.getGoal());
        
        legacyLLMStrategy.executeIteration(profitAgent);
        assertEquals(maximizeProfit, profitAgent.getGoal());
    }
    
    private Reasoning createMockReasoning() {
        return new Reasoning(
            "Market analysis",
            "Bullish momentum",
            "Low risk",
            "BUY",
            80,
            Instant.now()
        );
    }
}
