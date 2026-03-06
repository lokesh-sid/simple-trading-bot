package tradingbot.agent.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "trading_agents")
public class LegacyAgentEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String name;

    /**
     * Canonical set of agent types. Using an enum here eliminates raw-string
     * comparisons scattered across the codebase and lets the compiler catch typos.
     */
    public enum AgentType {
        FUTURES,
        FUTURES_PAPER;

        public boolean isPaper() {
            return this == FUTURES_PAPER;
        }

        /** Tolerant parse: falls back to FUTURES_PAPER on unknown values (safe default). */
        public static AgentType fromString(String value) {
            if (value == null) return FUTURES_PAPER;
            try {
                return AgentType.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return FUTURES_PAPER;
            }
        }
    }

    @Column(nullable = false)
    private String type; // stored as AgentType name, e.g. "FUTURES_PAPER"

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String direction = "LONG"; // Default to LONG

    @Column(nullable = false)
    private boolean sentimentEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentStatus status;

    @Column(columnDefinition = "TEXT")
    private String configurationJson;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public enum AgentStatus {
        CREATED,
        RUNNING,
        STOPPED,
        PAUSED,
        ERROR,
        DISABLED
    }

    // Constructors
    public LegacyAgentEntity() {}

    public LegacyAgentEntity(String id, String name, String type, String symbol, AgentStatus status, String configurationJson) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.symbol = symbol;
        this.status = status;
        this.configurationJson = configurationJson;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    /** Returns the strongly-typed AgentType, never null (safe fallback to FUTURES_PAPER). */
    public AgentType getAgentType() { return AgentType.fromString(type); }

    /** Convenience predicate used in lieu of raw "FUTURES_PAPER".equalsIgnoreCase(entity.getType()). */
    public boolean isPaperMode() { return getAgentType().isPaper(); }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public boolean isSentimentEnabled() { return sentimentEnabled; }
    public void setSentimentEnabled(boolean sentimentEnabled) { this.sentimentEnabled = sentimentEnabled; }

    public AgentStatus getStatus() { return status; }
    public void setStatus(AgentStatus status) { this.status = status; }

    public String getConfigurationJson() { return configurationJson; }
    public void setConfigurationJson(String configurationJson) { this.configurationJson = configurationJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
