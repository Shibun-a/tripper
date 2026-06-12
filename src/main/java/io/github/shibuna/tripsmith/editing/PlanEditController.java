package io.github.shibuna.tripsmith.editing;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/plans")
public class PlanEditController {

    private final PlanEditingService planEditingService;

    public PlanEditController(PlanEditingService planEditingService) {
        this.planEditingService = planEditingService;
    }

    @GetMapping("/{runId}/edit")
    public String showEditor(
            @PathVariable String runId,
            Model model
    ) {
        PlanEditSession session = planEditingService.findSession(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Editable plan not found"));
        model.addAttribute("editSession", session);
        model.addAttribute("editForm", new PlanEditForm());
        return "plan-edit";
    }

    @PostMapping("/{runId}/edit")
    public String applyEdit(
            @PathVariable String runId,
            @ModelAttribute PlanEditForm editForm,
            Model model
    ) {
        PlanEditSession session = planEditingService.applyEdit(
                runId,
                editForm.getSelectedDate(),
                editForm.getInstruction()
        );
        model.addAttribute("editSession", session);
        model.addAttribute("editForm", new PlanEditForm());
        return "plan-edit";
    }

    public static class PlanEditForm {

        private String selectedDate = "all";
        private String instruction = "";

        public String getSelectedDate() {
            return selectedDate;
        }

        public void setSelectedDate(String selectedDate) {
            this.selectedDate = selectedDate;
        }

        public String getInstruction() {
            return instruction;
        }

        public void setInstruction(String instruction) {
            this.instruction = instruction;
        }
    }
}
