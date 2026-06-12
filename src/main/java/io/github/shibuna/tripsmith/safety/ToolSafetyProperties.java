package io.github.shibuna.tripsmith.safety;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("tripsmith.safety.tools")
public class ToolSafetyProperties {

    private boolean enabled = true;
    private int maxToolCallsPerAction = 8;
    private List<String> highRiskToolGroups = List.of("browser", "browser_automation", "airbnb");

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxToolCallsPerAction() {
        return maxToolCallsPerAction;
    }

    public void setMaxToolCallsPerAction(int maxToolCallsPerAction) {
        this.maxToolCallsPerAction = maxToolCallsPerAction;
    }

    public List<String> getHighRiskToolGroups() {
        return highRiskToolGroups;
    }

    public void setHighRiskToolGroups(List<String> highRiskToolGroups) {
        this.highRiskToolGroups = List.copyOf(highRiskToolGroups == null ? List.of() : highRiskToolGroups);
    }
}
