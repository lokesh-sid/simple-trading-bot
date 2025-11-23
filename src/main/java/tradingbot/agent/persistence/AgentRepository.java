package tradingbot.agent.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentRepository extends JpaRepository<AgentEntity, String> {
    List<AgentEntity> findByStatus(AgentEntity.AgentStatus status);
}
