package tradingbot.agent.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentId;
import tradingbot.agent.domain.repository.AgentRepository;

/**
 * AgentRepositoryImpl - Implementation of domain AgentRepository using Spring Data JPA
 */
@Component
public class AgentRepositoryImpl implements AgentRepository {
    
    private final JpaAgentRepository jpaRepository;
    
    public AgentRepositoryImpl(JpaAgentRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }
    
    @Override
    public Agent save(Agent agent) {
        AgentEntity entity = AgentMapper.toEntity(agent);
        AgentEntity saved = jpaRepository.save(entity);
        return AgentMapper.toDomain(saved);
    }
    
    @Override
    public Optional<Agent> findById(AgentId id) {
        return jpaRepository.findById(id.getValue())
            .map(AgentMapper::toDomain);
    }
    
    @Override
    public Optional<Agent> findByName(String name) {
        return jpaRepository.findByName(name)
            .map(AgentMapper::toDomain);
    }
    
    @Override
    public List<Agent> findAll() {
        return jpaRepository.findAll().stream()
            .map(AgentMapper::toDomain)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Agent> findAllActive() {
        return jpaRepository.findAllActive().stream()
            .map(AgentMapper::toDomain)
            .collect(Collectors.toList());
    }
    
    @Override
    public void delete(AgentId id) {
        jpaRepository.deleteById(id.getValue());
    }
    
    @Override
    public boolean existsByName(String name) {
        return jpaRepository.existsByName(name);
    }
}
