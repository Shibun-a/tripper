package com.embabel.tripper.web;

import com.embabel.tripper.rag.TravelKnowledgeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/knowledge")
public class TravelKnowledgeController {

    private final TravelKnowledgeService travelKnowledgeService;

    public TravelKnowledgeController(TravelKnowledgeService travelKnowledgeService) {
        this.travelKnowledgeService = travelKnowledgeService;
    }

    @GetMapping
    public String index(Model model) {
        addKnowledgeModel(model);
        return "knowledge";
    }

    @PostMapping("/text")
    public String addText(
            @RequestParam String title,
            @RequestParam String content,
            Model model
    ) {
        travelKnowledgeService.addPastedText(title, content);
        addKnowledgeModel(model);
        model.addAttribute("message", "Knowledge text added.");
        return "knowledge";
    }

    @PostMapping("/upload")
    public String upload(
            @RequestParam(required = false) String title,
            @RequestParam MultipartFile file,
            Model model
    ) throws IOException {
        String filename = file.getOriginalFilename() == null ? file.getName() : file.getOriginalFilename();
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        travelKnowledgeService.addUploadedText(title, filename, content);
        addKnowledgeModel(model);
        model.addAttribute("message", "Knowledge file uploaded.");
        return "knowledge";
    }

    @PostMapping("/url")
    public String addUrl(
            @RequestParam String url,
            @RequestParam(required = false) String title,
            Model model
    ) {
        travelKnowledgeService.addUrl(url, title);
        addKnowledgeModel(model);
        model.addAttribute("message", "Knowledge URL imported.");
        return "knowledge";
    }

    @PostMapping("/clear")
    public String clear(Model model) {
        travelKnowledgeService.clear();
        addKnowledgeModel(model);
        model.addAttribute("message", "Knowledge base cleared.");
        return "knowledge";
    }

    @GetMapping("/debug")
    public String debug(
            @RequestParam(required = false) String query,
            Model model
    ) {
        var hits = query == null || query.isBlank()
                ? java.util.List.of()
                : travelKnowledgeService.search(query);
        model.addAttribute("query", query == null ? "" : query);
        model.addAttribute("hits", hits);
        model.addAttribute("documents", travelKnowledgeService.documents());
        return "knowledge-debug";
    }

    private void addKnowledgeModel(Model model) {
        model.addAttribute("documents", travelKnowledgeService.documents());
    }
}
