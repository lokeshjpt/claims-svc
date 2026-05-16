package com.abc.claims.batch;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Optional CLI entry-point. When the application is started with
 * {@code java -jar claims-processor.jar batch <input.csv> <output.csv>},
 * this runner delegates to {@link BatchProcessor#processFile(String, String)}
 * and then exits. In all other startup modes (REST, GUI) it is a no-op.
 */
@Component
public class BatchCommandLineRunner implements ApplicationRunner {

    private final BatchProcessor batchProcessor;

    public BatchCommandLineRunner(BatchProcessor batchProcessor) {
        this.batchProcessor = batchProcessor;
    }

    @Override
    public void run(ApplicationArguments args) {
        execute(args.getSourceArgs() == null ? List.of() : List.of(args.getSourceArgs()));
    }

    void execute(List<String> sourceArgs) {
        if (sourceArgs.isEmpty() || !"batch".equalsIgnoreCase(sourceArgs.getFirst())) {
            return;
        }
        if (sourceArgs.size() < 3) {
            throw new IllegalArgumentException(
                    "Usage: java -jar claims-processor.jar batch <input.csv> <output.csv>");
        }
        batchProcessor.processFile(sourceArgs.get(1), sourceArgs.get(2));
    }
}
