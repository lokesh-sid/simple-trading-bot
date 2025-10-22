package tradingbot.agent.api;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tradingbot.agent.api.dto.AgentDTOMapper;
import tradingbot.agent.api.dto.AgentResponse;
import tradingbot.agent.api.dto.CreateAgentRequest;
import tradingbot.agent.application.AgentService;
import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentId;

/**
 * AgentController - REST API for managing AI trading agents
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {
    
    private final AgentService agentService;
    
    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }
    
    /**
     * Create a new agent
     * POST /api/agents
     */
    @PostMapping
    public ResponseEntity<AgentResponse> createAgent(@Valid @RequestBody CreateAgentRequest request) {
        Agent agent = agentService.createAgent(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(AgentDTOMapper.toResponse(agent));
    }
    
    /**
     * Get all agents
     * GET /api/agents
     */
    @GetMapping
    public ResponseEntity<List<AgentResponse>> getAllAgents() {
        List<Agent> agents = agentService.getAllAgents();
        List<AgentResponse> responses = agents.stream()
            .map(AgentDTOMapper::toResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }
    
    /**
     * Get agent by ID
     * GET /api/agents/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<AgentResponse> getAgent(@PathVariable String id) {
        Agent agent = agentService.getAgent(new AgentId(id));
        return ResponseEntity.ok(AgentDTOMapper.toResponse(agent));
    }
    
    /**
     * Activate an agent
     * POST /api/agents/{id}/activate
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<AgentResponse> activateAgent(@PathVariable String id) {
        Agent agent = agentService.activateAgent(new AgentId(id));
        return ResponseEntity.ok(AgentDTOMapper.toResponse(agent));
    }
    
    /**
     * Pause an agent
     * POST /api/agents/{id}/pause
     */
    @PostMapping("/{id}/pause")
    public ResponseEntity<AgentResponse> pauseAgent(@PathVariable String id) {
        Agent agent = agentService.pauseAgent(new AgentId(id));
        return ResponseEntity.ok(AgentDTOMapper.toResponse(agent));
    }
    
    /**
     * Stop an agent
     * DELETE /api/agents/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> stopAgent(@PathVariable String id) {
        agentService.stopAgent(new AgentId(id));
        return ResponseEntity.noContent().build();
    }
}
