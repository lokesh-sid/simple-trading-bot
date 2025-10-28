package tradingbot.agent.infrastructure.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * AgentEntity - JPA Entity for Agent persistence
 * 
 * Maps Agent domain model to database table
 */
@Entity
@Table(name = "agents")
public class AgentEntity {
    
    @Id
    private String id;
    
    @Column(nullable = false, unique = true)
    private String name;
    
    @Column(name = "goal_type", nullable = false)
    private String goalType;
    
    @Column(name = "goal_description")
    private String goalDescription;
    
    @Column(name = "trading_symbol", nullable = false)
    private String tradingSymbol;
    
    @Column(nullable = false)
    private double capital;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AgentStatus status;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "last_active_at")
    private Instant lastActiveAt;
    
    @Column(name = "iteration_count")
    private int iterationCount;
    
    // Perception fields
    @Column(name = "last_price")
    private Double lastPrice;
    
    @Column(name = "last_trend")
    private String lastTrend;
    
    @Column(name = "last_sentiment")
    private String lastSentiment;
    
    @Column(name = "last_volume")
    private Double lastVolume;
    
    @Column(name = "perceived_at")
    private Instant perceivedAt;
    
    // Reasoning fields
    @Column(name = "last_observation", columnDefinition = "TEXT")
    private String lastObservation;
    
    @Column(name = "last_analysis", columnDefinition = "TEXT")
    private String lastAnalysis;
    
    @Column(name = "last_risk_assessment", columnDefinition = "TEXT")
    private String lastRiskAssessment;
    
    @Column(name = "last_recommendation", columnDefinition = "TEXT")
    private String lastRecommendation;
    
    @Column(name = "last_confidence")
    private Integer lastConfidence;
    
    @Column(name = "reasoned_at")
    private Instant reasonedAt;
    
    // Constructors
    protected AgentEntity() {
        // For JPA
    }
    
    public AgentEntity(String id, String name, String goalType, String goalDescription,
                      String tradingSymbol, double capital, AgentStatus status,
                      Instant createdAt) {
        this.id = id;
        this.name = name;
        this.goalType = goalType;
        this.goalDescription = goalDescription;
        this.tradingSymbol = tradingSymbol;
        this.capital = capital;
        this.status = status;
        this.createdAt = createdAt;
        this.iterationCount = 0;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }
    
    public String getGoalDescription() { return goalDescription; }
    public void setGoalDescription(String goalDescription) { this.goalDescription = goalDescription; }
    
    public String getTradingSymbol() { return tradingSymbol; }
    public void setTradingSymbol(String tradingSymbol) { this.tradingSymbol = tradingSymbol; }
    
    public double getCapital() { return capital; }
    public void setCapital(double capital) { this.capital = capital; }
    
    public AgentStatus getStatus() { return status; }
    public void setStatus(AgentStatus status) { this.status = status; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; }
    
    public int getIterationCount() { return iterationCount; }
    public void setIterationCount(int iterationCount) { this.iterationCount = iterationCount; }
    
    public Double getLastPrice() { return lastPrice; }
    public void setLastPrice(Double lastPrice) { this.lastPrice = lastPrice; }
    
    public String getLastTrend() { return lastTrend; }
    public void setLastTrend(String lastTrend) { this.lastTrend = lastTrend; }
    
    public String getLastSentiment() { return lastSentiment; }
    public void setLastSentiment(String lastSentiment) { this.lastSentiment = lastSentiment; }
    
    public Double getLastVolume() { return lastVolume; }
    public void setLastVolume(Double lastVolume) { this.lastVolume = lastVolume; }
    
    public Instant getPerceivedAt() { return perceivedAt; }
    public void setPerceivedAt(Instant perceivedAt) { this.perceivedAt = perceivedAt; }
    
    public String getLastObservation() { return lastObservation; }
    public void setLastObservation(String lastObservation) { this.lastObservation = lastObservation; }
    
    public String getLastAnalysis() { return lastAnalysis; }
    public void setLastAnalysis(String lastAnalysis) { this.lastAnalysis = lastAnalysis; }
    
    public String getLastRiskAssessment() { return lastRiskAssessment; }
    public void setLastRiskAssessment(String lastRiskAssessment) { this.lastRiskAssessment = lastRiskAssessment; }
    
    public String getLastRecommendation() { return lastRecommendation; }
    public void setLastRecommendation(String lastRecommendation) { this.lastRecommendation = lastRecommendation; }
    
    public Integer getLastConfidence() { return lastConfidence; }
    public void setLastConfidence(Integer lastConfidence) { this.lastConfidence = lastConfidence; }
    
    public Instant getReasonedAt() { return reasonedAt; }
    public void setReasonedAt(Instant reasonedAt) { this.reasonedAt = reasonedAt; }
    
    /**
     * AgentStatus - Entity status enum
     */
    public enum AgentStatus {
        IDLE, ACTIVE, PAUSED, STOPPED
    }
}
