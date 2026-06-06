package com.embabel.tripper.observability;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/runs")
public class AgentRunTraceController {

    private final AgentRunTraceService traceService;

    public AgentRunTraceController(AgentRunTraceService traceService) {
        this.traceService = traceService;
    }

    @GetMapping
    public String listRuns(Model model) {
        model.addAttribute("runs", traceService.findRecent());
        return "runs";
    }

    @GetMapping("/{runId}")
    public String showRun(
            @PathVariable String runId,
            Model model
    ) {
        AgentRunTrace trace = traceService.findTrace(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run trace not found"));
        model.addAttribute("trace", trace);
        return "run-detail";
    }
}
