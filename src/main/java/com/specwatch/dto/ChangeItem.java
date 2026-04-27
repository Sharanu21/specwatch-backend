package com.specwatch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeItem {

    public enum Type {
        BREAKING, NON_BREAKING
    }

    private Type type;
    private String endpoint;
    private String description;
}
