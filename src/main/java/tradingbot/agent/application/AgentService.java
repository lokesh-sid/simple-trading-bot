package tradingbot.agent.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tradingbot.agent.api.dto.AgentDTOMapper;
import tradingbot.agent.api.dto.CreateAgentRequest;
import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentId;
import tradingbot.agent.domain.repository.AgentRepository;

/**
 * AgentService - Application service for agent management
 */
@Service
@Transactional
public class AgentService {
    
    private final AgentRepository agentRepository;
    
    public AgentService(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }
    
    /**
     * Create a new agent
     */
    public Agent createAgent(CreateAgentRequest request) {
        // Check if agent with same name already exists
        if (agentRepository.existsByName(request.name())) {
            throw new AgentAlreadyExistsException("Agent with name '" + request.name() + "' already exists");
        }
        
        // Create and save agent
        Agent agent = AgentDTOMapper.toDomain(request);
        return agentRepository.save(agent);
    }
    
    /**
     * Get all agents
     */
    public List<Agent> getAllAgents() {
        return agentRepository.findAll();
    }
    
    /**
     * Get agent by ID
     */
    public Agent getAgent(AgentId id) {
        return agentRepository.findById(id)
            .orElseThrow(() -> new AgentNotFoundException("Agent not found: " + id.getValue()));
    }
    
    /**
     * Activate an agent
     */
    public Agent activateAgent(AgentId id) {
        Agent agent = getAgent(id);
        agent.activate();
        return agentRepository.save(agent);
    }
    
    /**
     * Pause an agent
     */
    public Agent pauseAgent(AgentId id) {
        Agent agent = getAgent(id);
        agent.pause();
        return agentRepository.save(agent);
    }
    
    /**
     * Stop an agent
     */
    public void stopAgent(AgentId id) {
        Agent agent = getAgent(id);
        agent.stop();
        agentRepository.save(agent);
    }
}

/**
 * AgentAlreadyExistsException
 */
class AgentAlreadyExistsException extends RuntimeException {
    public AgentAlreadyExistsException(String message) {
        super(message);
    }
}

/**
 * AgentNotFoundException
 */
class AgentNotFoundException extends RuntimeException {
    public AgentNotFoundException(String message) {
        super(message);
    }
}
