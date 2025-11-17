package tradingbot.agent.infrastructure.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * OrderEntity - JPA entity for Order persistence
 */
@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_orders_agent_id", columnList = "agent_id"),
    @Index(name = "idx_orders_symbol", columnList = "symbol"),
    @Index(name = "idx_orders_status", columnList = "status"),
    @Index(name = "idx_orders_created_at", columnList = "created_at")
})
public class OrderEntity {
    
    @Id
    private String id;
    
    @Column(name = "agent_id", nullable = false)
    private String agentId;
    
    @Column(nullable = false)
    private String symbol;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Direction direction;
    
    @Column(nullable = false)
    private double price;
    
    @Column(nullable = false)
    private double quantity;
    
    @Column(name = "stop_loss")
    private Double stopLoss;
    
    @Column(name = "take_profit")
    private Double takeProfit;
    
    @Column
    private Integer leverage;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "executed_at")
    private Instant executedAt;
    
    @Column(name = "exchange_order_id")
    private String exchangeOrderId;
    
    @Column(name = "failure_reason", length = 1000)
    private String failureReason;
    
    // Constructors
    public OrderEntity() {}
    
    public OrderEntity(String id, String agentId, String symbol, Direction direction,
                      double price, double quantity, Double stopLoss, Double takeProfit,
                      Integer leverage, Status status, Instant createdAt) {
        this.id = id;
        this.agentId = agentId;
        this.symbol = symbol;
        this.direction = direction;
        this.price = price;
        this.quantity = quantity;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.leverage = leverage;
        this.status = status;
        this.createdAt = createdAt;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    
    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }
    
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    
    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }
    
    public Double getStopLoss() { return stopLoss; }
    public void setStopLoss(Double stopLoss) { this.stopLoss = stopLoss; }
    
    public Double getTakeProfit() { return takeProfit; }
    public void setTakeProfit(Double takeProfit) { this.takeProfit = takeProfit; }
    
    public Integer getLeverage() { return leverage; }
    public void setLeverage(Integer leverage) { this.leverage = leverage; }
    
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
    
    public String getExchangeOrderId() { return exchangeOrderId; }
    public void setExchangeOrderId(String exchangeOrderId) { this.exchangeOrderId = exchangeOrderId; }
    
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    
    // Enums
    public enum Direction {
        LONG, SHORT
    }
    
    public enum Status {
        PENDING, SUBMITTED, EXECUTED, FAILED, CANCELLED
    }
}
