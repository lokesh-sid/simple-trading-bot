package tradingbot.agent.manager;

import java.util.ArrayList;
import java.util.List;

import tradingbot.agent.TradingAgent;

public class AgentManager {
    private final List<TradingAgent<?>> agents = new ArrayList<>();

    public void registerAgent(TradingAgent<?> agent) {
        agents.add(agent);
    }

    public void startAll() {
        for (TradingAgent<?> agent : agents) {
            agent.start();
        }
    }

    public void stopAll() {
        for (TradingAgent<?> agent : agents) {
            agent.stop();
        }
    }

    public List<TradingAgent<?>> getAgents() {
        return agents;
    }
}
