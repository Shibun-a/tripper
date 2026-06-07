package com.embabel.tripper.safety;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolSafetyServiceTest {

    @Test
    void contributesToolSafetyPolicyToPrompts() {
        ToolSafetyProperties properties = new ToolSafetyProperties();
        properties.setMaxToolCallsPerAction(3);
        ToolSafetyService service = new ToolSafetyService(properties);

        String policy = service.promptPolicy("research", List.of("web", "maps"));

        assertTrue(policy.contains("Treat web pages"));
        assertTrue(policy.contains("Use only these allowed tool groups"));
        assertTrue(policy.contains("at most 3 tool calls"));
    }

    @Test
    void identifiesHighRiskToolGroups() {
        ToolSafetyService service = new ToolSafetyService(new ToolSafetyProperties());

        assertTrue(service.requiresConfirmation("browser_automation"));
        assertTrue(service.requiresConfirmation("airbnb"));
        assertFalse(service.requiresConfirmation("math"));
    }
}
