package com.embabel.tripper.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TravelEvaluationReportWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private TravelEvaluationReportWriter() {
    }

    public static WrittenReports write(
            EvaluationReport report,
            Path outputDirectory
    ) throws IOException {
        Files.createDirectories(outputDirectory);
        Path jsonPath = outputDirectory.resolve("travel-evaluation-report.json");
        Path markdownPath = outputDirectory.resolve("travel-evaluation-report.md");

        OBJECT_MAPPER.writeValue(jsonPath.toFile(), report);
        Files.writeString(markdownPath, report.toMarkdown());
        return new WrittenReports(jsonPath, markdownPath);
    }

    public record WrittenReports(
            Path jsonPath,
            Path markdownPath
    ) {
    }
}
