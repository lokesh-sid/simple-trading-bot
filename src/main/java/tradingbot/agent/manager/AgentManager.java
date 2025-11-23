package tradingbot.agent.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import tradingbot.agent.TradingAgent;
import tradingbot.agent.factory.AgentFactory;
import tradingbot.agent.persistence.AgentEntity;
import tradingbot.agent.persistence.AgentRepository;

@Service
public class AgentManager {
    private static final Logger log = LoggerFactory.getLogger(AgentManager.class);

    private final Map<String, TradingAgent> agents = new ConcurrentHashMap<>();
    private final AgentRepository agentRepository;
    private final AgentFactory agentFactory;

    public AgentManager(AgentRepository agentRepository, AgentFactory agentFactory) {
        this.agentRepository = agentRepository;
        this.agentFactory = agentFactory;
    }

    @PostConstruct
    public void loadAgents() {
        log.info("Loading agents from database...");
        List<AgentEntity> entities = agentRepository.findAll();
        for (AgentEntity entity : entities) {
            try {
                TradingAgent agent = agentFactory.createAgent(entity);
                agents.put(agent.getId(), agent);
                
                if (entity.getStatus() == AgentEntity.AgentStatus.RUNNING) {
                    log.info("Starting agent: {}", agent.getName());
                    agent.start();
                }
            } catch (Exception e) {
                log.error("Failed to load agent: {}", entity.getId(), e);
            }
        }
        log.info("Loaded {} agents", agents.size());
    }

    public void registerAgent(TradingAgent agent) {
        agents.put(agent.getId(), agent);
        // Note: Persistence is handled by the creator of the agent (e.g. Controller/Service)
        // creating the AgentEntity first.
    }

    public void startAgent(String id) {
        TradingAgent agent = agents.get(id);
        if (agent != null) {
            if (!agent.isRunning()) {
                agent.start();
                updateAgentStatus(id, AgentEntity.AgentStatus.RUNNING);
                log.info("Agent {} started", id);
            }
        } else {
            log.warn("Agent not found: {}", id);
        }
    }

    public void stopAgent(String id) {
        TradingAgent agent = agents.get(id);
        if (agent != null) {
            if (agent.isRunning()) {
                agent.stop();
                updateAgentStatus(id, AgentEntity.AgentStatus.STOPPED);
                log.info("Agent {} stopped", id);
            }
        } else {
            log.warn("Agent not found: {}", id);
        }
    }

    public void startAll() {
        agents.keySet().forEach(this::startAgent);
    }

    public void stopAll() {
        agents.keySet().forEach(this::stopAgent);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down all agents...");
        // We stop the agents but we might NOT want to update the DB status to STOPPED
        // so that they resume on restart.
        // However, the requirement says "Implement Graceful Shutdown".
        // If we update DB to STOPPED, they won't auto-start.
        // So we should just call agent.stop() without updating DB status, 
        // OR we rely on the fact that the process is dying.
        
        // Let's just stop the in-memory agents.
        agents.values().forEach(TradingAgent::stop);
    }

    public List<TradingAgent> getAgents() {
        return new ArrayList<>(agents.values());
    }
    
    private void updateAgentStatus(String id, AgentEntity.AgentStatus status) {
        agentRepository.findById(id).ifPresent(entity -> {
            entity.setStatus(status);
            entity.setUpdatedAt(java.time.Instant.now());
            agentRepository.save(entity);
        });
    }

    public TradingAgent createAgent(AgentEntity entity) {
        agentRepository.save(entity);
        TradingAgent agent = agentFactory.createAgent(entity);
        agents.put(agent.getId(), agent);
        return agent;
    }

    public void deleteAgent(String id) {
        TradingAgent agent = agents.remove(id);
        if (agent != null && agent.isRunning()) {
            agent.stop();
        }
        agentRepository.deleteById(id);
    }

    public TradingAgent getAgent(String id) {
        return agents.get(id);
    }

    public void refreshAgent(String id) {
        stopAgent(id);
        agentRepository.findById(id).ifPresent(entity -> {
            try {
                TradingAgent agent = agentFactory.createAgent(entity);
                agents.put(id, agent);
            } catch (Exception e) {
                log.error("Failed to refresh agent: {}", id, e);
            }
        });
    }
}
