package tradingbot.agent.infrastructure.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * JpaAgentRepository - Spring Data JPA repository for AgentEntity
 */
@Repository
public interface JpaAgentRepository extends JpaRepository<AgentEntity, String> {
    
    /**
     * Find agent by name
     */
    Optional<AgentEntity> findByName(String name);
    
    /**
     * Find all active agents
     */
    @Query("SELECT a FROM AgentEntity a WHERE a.status = 'ACTIVE'")
    List<AgentEntity> findAllActive();
    
    /**
     * Check if agent with name exists
     */
    boolean existsByName(String name);
}
