package com.specwatch.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class DiffResult {

    private boolean hasBreakingChanges;
    private int breakingCount;
    private int nonBreakingCount;
    private String summary;
    private List<ChangeItem> changes;
    private String changesJson;
    private boolean isFirstRun;
    private boolean isError;
    private String errorMessage;

    public static DiffResult firstRun() {
        return DiffResult.builder()
            .isFirstRun(true)
            .hasBreakingChanges(false)
            .breakingCount(0)
            .nonBreakingCount(0)
            .summary("Initial spec snapshot saved. Future pushes will be compared against this.")
            .build();
    }

    public static DiffResult error(String message) {
        return DiffResult.builder()
            .isError(true)
            .errorMessage(message)
            .hasBreakingChanges(false)
            .summary("Error processing spec: " + message)
            .build();
    }
}
