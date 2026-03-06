package tradingbot.bot.service;

import static tradingbot.agent.persistence.LegacyAgentEntity.AgentType.*;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tradingbot.agent.manager.AgentManager;
import tradingbot.agent.persistence.AgentRepository;
import tradingbot.agent.persistence.LegacyAgentEntity;
import tradingbot.bot.TradeDirection;

@Service
public class BotStateService {

    private static final Logger logger = LoggerFactory.getLogger(BotStateService.class);

    private final AgentManager agentManager;
    private final AgentRepository agentRepository;

    // ✅ Constructor injection only — no runtime parameter passing
    public BotStateService(AgentManager agentManager, AgentRepository agentRepository) {
        this.agentManager = agentManager;
        this.agentRepository = agentRepository;
    }

    @Transactional
    public void startBot(String botId, boolean resolvedPaperMode, TradeDirection direction) {
        agentRepository.findById(botId).ifPresent(entity -> {
            if (direction != null) {
                entity.setDirection(direction.toString());
            }
            entity.setType(resolvedPaperMode
                    ? FUTURES_PAPER.name()
                    : FUTURES.name());
            entity.setStatus(LegacyAgentEntity.AgentStatus.RUNNING);
            entity.setUpdatedAt(Instant.now());
            agentRepository.save(entity);
        });

        agentManager.refreshAgent(botId);
        agentManager.startAgent(botId);

        logger.info("Bot {} started — paperMode={}, direction={}", botId, resolvedPaperMode, direction);
    }

    @Transactional
    public void stopBot(String botId) {
        agentManager.stopAgent(botId);

        agentRepository.findById(botId).ifPresent(entity -> {
            entity.setStatus(LegacyAgentEntity.AgentStatus.STOPPED);
            entity.setUpdatedAt(Instant.now());
            agentRepository.save(entity);
        });

        logger.info("Bot {} stopped", botId);
    }

    @Transactional
    public void pauseBot(String botId) {
        agentManager.stopAgent(botId);

        agentRepository.findById(botId).ifPresent(entity -> {
            entity.setStatus(LegacyAgentEntity.AgentStatus.PAUSED);
            entity.setUpdatedAt(Instant.now());
            agentRepository.save(entity);
        });

        logger.info("Bot {} paused", botId);
    }
}