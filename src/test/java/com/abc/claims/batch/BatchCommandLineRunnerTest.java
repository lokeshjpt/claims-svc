package com.abc.claims.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class BatchCommandLineRunnerTest {

    @Test
    @DisplayName("no args → no-op (REST/GUI startup)")
    void no_args_is_noop() {
        BatchProcessor bp = mock(BatchProcessor.class);
        new BatchCommandLineRunner(bp).execute(List.of());
        verify(bp, never()).processFile(any(), any());
    }

    @Test
    @DisplayName("non-batch first arg → no-op")
    void other_first_arg_is_noop() {
        BatchProcessor bp = mock(BatchProcessor.class);
        new BatchCommandLineRunner(bp).execute(List.of("--server.port=9090"));
        verify(bp, never()).processFile(any(), any());
    }

    @Test
    @DisplayName("batch with two paths → delegates to BatchProcessor.processFile")
    void batch_with_paths_invokes_processor() {
        BatchProcessor bp = mock(BatchProcessor.class);
        new BatchCommandLineRunner(bp).execute(List.of("batch", "in.csv", "out.csv"));
        verify(bp, times(1)).processFile("in.csv", "out.csv");
    }

    @Test
    @DisplayName("batch without enough args → IllegalArgumentException with usage hint")
    void batch_missing_args_throws() {
        BatchProcessor bp = mock(BatchProcessor.class);
        BatchCommandLineRunner runner = new BatchCommandLineRunner(bp);
        assertThatThrownBy(() -> runner.execute(List.of("batch", "only-one.csv")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Usage:");
    }

    private static String any() {
        return org.mockito.ArgumentMatchers.anyString();
    }
}
