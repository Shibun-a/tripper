package io.github.shibuna.tripsmith.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class TravelEvalDataset {

    public static final Path DEFAULT_PATH = Path.of("evals", "travel-eval-cases.json");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TravelEvalDataset() {
    }

    public static List<TravelEvalCase> loadDefault() throws IOException {
        return load(DEFAULT_PATH);
    }

    public static List<TravelEvalCase> load(Path path) throws IOException {
        return OBJECT_MAPPER.readValue(path.toFile(), new TypeReference<>() {
        });
    }
}
