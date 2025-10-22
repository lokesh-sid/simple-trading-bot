package tradingbot.agent.infrastructure.persistence;

import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentGoal;
import tradingbot.agent.domain.model.AgentId;
import tradingbot.agent.domain.model.AgentState;
import tradingbot.agent.domain.model.Perception;
import tradingbot.agent.domain.model.Reasoning;

/**
 * AgentMapper - Maps between Agent domain model and AgentEntity
 */
public class AgentMapper {
    
    /**
     * Convert Agent domain model to AgentEntity
     */
    public static AgentEntity toEntity(Agent agent) {
        AgentEntity entity = new AgentEntity(
            agent.getId().getValue(),
            agent.getName(),
            agent.getGoal().getType().name(),
            agent.getGoal().getDescription(),
            agent.getTradingSymbol(),
            agent.getCapital(),
            mapStatus(agent.getState().getStatus()),
            agent.getCreatedAt()
        );
        
        entity.setLastActiveAt(agent.getState().getLastActiveAt());
        entity.setIterationCount(agent.getState().getIterationCount());
        
        // Map perception
        if (agent.getLastPerception() != null) {
            Perception perception = agent.getLastPerception();
            entity.setLastPrice(perception.getCurrentPrice());
            entity.setLastTrend(perception.getTrend());
            entity.setLastSentiment(perception.getSentiment());
            entity.setLastVolume(perception.getVolume());
            entity.setPerceivedAt(perception.getTimestamp());
        }
        
        // Map reasoning
        if (agent.getLastReasoning() != null) {
            Reasoning reasoning = agent.getLastReasoning();
            entity.setLastObservation(reasoning.getObservation());
            entity.setLastAnalysis(reasoning.getAnalysis());
            entity.setLastRiskAssessment(reasoning.getRiskAssessment());
            entity.setLastRecommendation(reasoning.getRecommendation());
            entity.setLastConfidence(reasoning.getConfidence());
            entity.setReasonedAt(reasoning.getTimestamp());
        }
        
        return entity;
    }
    
    /**
     * Convert AgentEntity to Agent domain model
     */
    public static Agent toDomain(AgentEntity entity) {
        // Create goal
        AgentGoal goal = new AgentGoal(
            AgentGoal.GoalType.valueOf(entity.getGoalType()),
            entity.getGoalDescription()
        );
        
        // Create state
        AgentState state = new AgentState(
            mapStatus(entity.getStatus()),
            entity.getLastActiveAt(),
            entity.getIterationCount()
        );
        
        // Create perception (if exists)
        Perception perception = null;
        if (entity.getLastPrice() != null) {
            perception = new Perception(
                entity.getTradingSymbol(),
                entity.getLastPrice(),
                entity.getLastTrend(),
                entity.getLastSentiment(),
                entity.getLastVolume() != null ? entity.getLastVolume() : 0.0,
                entity.getPerceivedAt()
            );
        }
        
        // Create reasoning (if exists)
        Reasoning reasoning = null;
        if (entity.getLastObservation() != null) {
            reasoning = new Reasoning(
                entity.getLastObservation(),
                entity.getLastAnalysis(),
                entity.getLastRiskAssessment(),
                entity.getLastRecommendation(),
                entity.getLastConfidence() != null ? entity.getLastConfidence() : 0,
                entity.getReasonedAt()
            );
        }
        
        // Create agent
        Agent agent = new Agent(
            new AgentId(entity.getId()),
            entity.getName(),
            goal,
            entity.getTradingSymbol(),
            entity.getCapital(),
            state,
            entity.getCreatedAt()
        );
        
        // Set perception and reasoning if they exist
        if (perception != null) {
            agent.perceive(perception);
        }
        if (reasoning != null) {
            agent.reason(reasoning);
        }
        
        return agent;
    }
    
    /**
     * Map domain AgentState.Status to entity AgentStatus
     */
    private static AgentEntity.AgentStatus mapStatus(AgentState.Status domainStatus) {
        return switch (domainStatus) {
            case IDLE -> AgentEntity.AgentStatus.IDLE;
            case ACTIVE -> AgentEntity.AgentStatus.ACTIVE;
            case PAUSED -> AgentEntity.AgentStatus.PAUSED;
            case STOPPED -> AgentEntity.AgentStatus.STOPPED;
        };
    }
    
    /**
     * Map entity AgentStatus to domain AgentState.Status
     */
    private static AgentState.Status mapStatus(AgentEntity.AgentStatus entityStatus) {
        return switch (entityStatus) {
            case IDLE -> AgentState.Status.IDLE;
            case ACTIVE -> AgentState.Status.ACTIVE;
            case PAUSED -> AgentState.Status.PAUSED;
            case STOPPED -> AgentState.Status.STOPPED;
        };
    }
}
