package tradingbot.agent.domain.repository;

import java.util.List;
import java.util.Optional;

import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentId;

/**
 * AgentRepository - Repository interface for Agent aggregate
 * 
 * Domain repository following DDD principles
 */
public interface AgentRepository {
    
    /**
     * Save an agent (create or update)
     */
    Agent save(Agent agent);
    
    /**
     * Find agent by ID
     */
    Optional<Agent> findById(AgentId id);
    
    /**
     * Find agent by name
     */
    Optional<Agent> findByName(String name);
    
    /**
     * Find all agents
     */
    List<Agent> findAll();
    
    /**
     * Find all active agents (status = ACTIVE)
     */
    List<Agent> findAllActive();
    
    /**
     * Delete an agent
     */
    void delete(AgentId id);
    
    /**
     * Check if agent with name exists
     */
    boolean existsByName(String name);
}
