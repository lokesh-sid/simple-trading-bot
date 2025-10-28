package tradingbot.agent.api.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentGoal;
import tradingbot.agent.domain.model.AgentState;
import tradingbot.agent.domain.model.Perception;
import tradingbot.agent.domain.model.Reasoning;

/**
 * AgentMapper - MapStruct-based mapper for Agent domain model and DTOs
 * 
 * MapStruct generates implementation at compile time for zero runtime overhead.
 * This is a Spring component that can be injected.
 */
@Mapper(componentModel = "spring")
public interface AgentMapper {
    
    AgentMapper INSTANCE = Mappers.getMapper(AgentMapper.class);
    
    /**
     * Convert Agent domain model to AgentResponse DTO
     */
    @Mapping(source = "id.value", target = "id")
    @Mapping(source = "goal.type", target = "goalType", qualifiedByName = "goalTypeToString")
    @Mapping(source = "goal.description", target = "goalDescription")
    @Mapping(source = "state.status", target = "status", qualifiedByName = "statusToString")
    @Mapping(source = "state.lastActiveAt", target = "lastActiveAt")
    @Mapping(source = "state.iterationCount", target = "iterationCount")
    @Mapping(source = "lastPerception", target = "lastPerception")
    @Mapping(source = "lastReasoning", target = "lastReasoning")
    AgentResponse toResponse(Agent agent);
    
    /**
     * Convert Perception to PerceptionDTO
     */
    @Mapping(source = "symbol", target = "symbol")
    @Mapping(source = "currentPrice", target = "currentPrice")
    @Mapping(source = "trend", target = "trend")
    @Mapping(source = "sentiment", target = "sentiment")
    @Mapping(source = "volume", target = "volume")
    @Mapping(source = "timestamp", target = "timestamp")
    AgentResponse.PerceptionDTO toPerceptionDTO(Perception perception);
    
    /**
     * Convert Reasoning to ReasoningDTO
     */
    @Mapping(source = "observation", target = "observation")
    @Mapping(source = "analysis", target = "analysis")
    @Mapping(source = "riskAssessment", target = "riskAssessment")
    @Mapping(source = "recommendation", target = "recommendation")
    @Mapping(source = "confidence", target = "confidence")
    @Mapping(source = "timestamp", target = "timestamp")
    AgentResponse.ReasoningDTO toReasoningDTO(Reasoning reasoning);
    
    /**
     * Convert CreateAgentRequest to Agent domain model
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "lastPerception", ignore = true)
    @Mapping(target = "lastReasoning", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(source = "name", target = "name")
    @Mapping(source = "tradingSymbol", target = "tradingSymbol")
    @Mapping(source = "capital", target = "capital")
    @Mapping(source = "request", target = "goal", qualifiedByName = "requestToGoal")
    Agent toDomain(CreateAgentRequest request);
    
    /**
     * Custom mapping: GoalType enum to String
     */
    @Named("goalTypeToString")
    default String goalTypeToString(AgentGoal.GoalType goalType) {
        return goalType != null ? goalType.name() : null;
    }
    
    /**
     * Custom mapping: AgentStatus enum to String
     */
    @Named("statusToString")
    default String statusToString(AgentState.Status status) {
        return status != null ? status.name() : null;
    }
    
    /**
     * Custom mapping: CreateAgentRequest to AgentGoal
     */
    @Named("requestToGoal")
    default AgentGoal requestToGoal(CreateAgentRequest request) {
        if (request == null) {
            return null;
        }
        AgentGoal.GoalType goalType = AgentGoal.GoalType.valueOf(request.goalType());
        return new AgentGoal(goalType, request.goalDescription());
    }
}
