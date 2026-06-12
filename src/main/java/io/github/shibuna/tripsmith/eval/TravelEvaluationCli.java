package io.github.shibuna.tripsmith.eval;

import io.github.shibuna.tripsmith.verification.ItineraryVerificationService;
import io.github.shibuna.tripsmith.verification.PlanVerificationRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class TravelEvaluationCli {

    private TravelEvaluationCli() {
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        List<TravelEvalCase> cases = TravelEvalDataset.load(options.datasetPath());

        TravelEvaluationHarness harness = new TravelEvaluationHarness(
                new DeterministicEvalPlanCandidateFactory(),
                new ItineraryVerificationService(new PlanVerificationRepository())
        );
        EvaluationReport report = harness.run(
                cases,
                options.datasetPath().toString(),
                options.limit()
        );
        TravelEvaluationReportWriter.WrittenReports writtenReports = TravelEvaluationReportWriter.write(
                report,
                options.outputDirectory()
        );

        System.out.println("Evaluation cases: " + report.metrics().caseCount());
        System.out.println("Date coverage: " + String.format(Locale.ROOT, "%.1f%%", report.metrics().dateCoverageRate() * 100.0));
        System.out.println("Verifier errors: " + report.metrics().verifierErrorCount());
        System.out.println("JSON report: " + writtenReports.jsonPath());
        System.out.println("Markdown report: " + writtenReports.markdownPath());
    }

    private record Options(
            Path datasetPath,
            Path outputDirectory,
            int limit
    ) {

        static Options parse(String[] args) {
            Path datasetPath = TravelEvalDataset.DEFAULT_PATH;
            Path outputDirectory = Path.of("target", "evals");
            int limit = 0;

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("--dataset".equals(arg) && i + 1 < args.length) {
                    datasetPath = Path.of(args[++i]);
                } else if ("--output-dir".equals(arg) && i + 1 < args.length) {
                    outputDirectory = Path.of(args[++i]);
                } else if ("--limit".equals(arg) && i + 1 < args.length) {
                    limit = Integer.parseInt(args[++i]);
                } else {
                    throw new IllegalArgumentException("Unknown or incomplete argument: " + arg);
                }
            }
            return new Options(datasetPath, outputDirectory, limit);
        }
    }
}
