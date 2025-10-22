package tradingbot.agent.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tradingbot.agent.api.dto.CreateAgentRequest;
import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentGoal;
import tradingbot.agent.domain.model.AgentId;
import tradingbot.agent.domain.model.AgentState;
import tradingbot.agent.domain.repository.AgentRepository;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {
    
    @Mock
    private AgentRepository agentRepository;
    
    @InjectMocks
    private AgentService agentService;
    
    private CreateAgentRequest createRequest;
    private Agent testAgent;
    
    @BeforeEach
    void setUp() {
        createRequest = new CreateAgentRequest(
            "Test Agent",
            "MAXIMIZE_PROFIT",
            "Maximize BTC profits",
            "BTCUSDT",
            10000.0
        );
        
        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Maximize BTC profits");
        testAgent = Agent.create("Test Agent", goal, "BTCUSDT", 10000.0);
    }
    
    @Test
    void testCreateAgent_Success() {
        // Given
        when(agentRepository.existsByName("Test Agent")).thenReturn(false);
        when(agentRepository.save(any(Agent.class))).thenReturn(testAgent);
        
        // When
        Agent result = agentService.createAgent(createRequest);
        
        // Then
        assertNotNull(result);
        assertEquals("Test Agent", result.getName());
        verify(agentRepository).existsByName("Test Agent");
        verify(agentRepository).save(any(Agent.class));
    }
    
    @Test
    void testCreateAgent_AlreadyExists() {
        // Given
        when(agentRepository.existsByName("Test Agent")).thenReturn(true);
        
        // When & Then
        assertThrows(AgentAlreadyExistsException.class, () -> {
            agentService.createAgent(createRequest);
        });
        
        verify(agentRepository).existsByName("Test Agent");
        verify(agentRepository, never()).save(any(Agent.class));
    }
    
    @Test
    void testGetAllAgents() {
        // Given
        Agent agent1 = testAgent;
        Agent agent2 = Agent.create("Agent 2", 
            new AgentGoal(AgentGoal.GoalType.HEDGE_RISK, "Hedge"), 
            "ETHUSDT", 5000.0);
        when(agentRepository.findAll()).thenReturn(Arrays.asList(agent1, agent2));
        
        // When
        List<Agent> result = agentService.getAllAgents();
        
        // Then
        assertEquals(2, result.size());
        verify(agentRepository).findAll();
    }
    
    @Test
    void testGetAgent_Success() {
        // Given
        AgentId agentId = testAgent.getId();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(testAgent));
        
        // When
        Agent result = agentService.getAgent(agentId);
        
        // Then
        assertNotNull(result);
        assertEquals(testAgent.getId(), result.getId());
        verify(agentRepository).findById(agentId);
    }
    
    @Test
    void testGetAgent_NotFound() {
        // Given
        AgentId agentId = AgentId.generate();
        when(agentRepository.findById(agentId)).thenReturn(Optional.empty());
        
        // When & Then
        assertThrows(AgentNotFoundException.class, () -> {
            agentService.getAgent(agentId);
        });
        
        verify(agentRepository).findById(agentId);
    }
    
    @Test
    void testActivateAgent() {
        // Given
        AgentId agentId = testAgent.getId();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(testAgent));
        when(agentRepository.save(testAgent)).thenReturn(testAgent);
        
        // When
        Agent result = agentService.activateAgent(agentId);
        
        // Then
        assertEquals(AgentState.Status.ACTIVE, result.getState().getStatus());
        verify(agentRepository).findById(agentId);
        verify(agentRepository).save(testAgent);
    }
    
    @Test
    void testPauseAgent() {
        // Given
        AgentId agentId = testAgent.getId();
        testAgent.activate();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(testAgent));
        when(agentRepository.save(testAgent)).thenReturn(testAgent);
        
        // When
        Agent result = agentService.pauseAgent(agentId);
        
        // Then
        assertEquals(AgentState.Status.PAUSED, result.getState().getStatus());
        verify(agentRepository).findById(agentId);
        verify(agentRepository).save(testAgent);
    }
    
    @Test
    void testStopAgent() {
        // Given
        AgentId agentId = testAgent.getId();
        testAgent.activate();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(testAgent));
        when(agentRepository.save(testAgent)).thenReturn(testAgent);
        
        // When
        agentService.stopAgent(agentId);
        
        // Then
        assertEquals(AgentState.Status.STOPPED, testAgent.getState().getStatus());
        verify(agentRepository).findById(agentId);
        verify(agentRepository).save(testAgent);
    }
}
