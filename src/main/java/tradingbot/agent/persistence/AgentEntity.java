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
public class AgentEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type; // e.g., "FUTURES", "SPOT"

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
        ERROR,
        DISABLED
    }

    // Constructors
    public AgentEntity() {}

    public AgentEntity(String id, String name, String type, String symbol, AgentStatus status, String configurationJson) {
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
