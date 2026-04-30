package com.specwatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.specwatch.dto.ChangeItem;
import com.specwatch.dto.DiffResult;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class SpecDiffService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public DiffResult diff(String oldSpec, String newSpec) {
        // --- ADDED LOGGING START ---
        log.info("=== DIFF CALLED === oldSpec length: {}, newSpec length: {}",
                oldSpec != null ? oldSpec.length() : 0,
                newSpec != null ? newSpec.length() : 0);
        // --- ADDED LOGGING END ---

        if (oldSpec == null || oldSpec.isBlank()) {
            return DiffResult.firstRun();
        }

        try {
            OpenAPI oldApi = parseSpec(oldSpec);
            OpenAPI newApi = parseSpec(newSpec);

            if (oldApi == null || newApi == null) {
                return DiffResult.error("Failed to parse OpenAPI spec — check YAML/JSON syntax");
            }

            List<ChangeItem> breaking = new ArrayList<>();
            List<ChangeItem> nonBreaking = new ArrayList<>();

            Map<String, PathItem> oldPaths = oldApi.getPaths() != null ? oldApi.getPaths() : new HashMap<>();
            Map<String, PathItem> newPaths = newApi.getPaths() != null ? newApi.getPaths() : new HashMap<>();

            // 1. Check for removed endpoints (BREAKING)
            for (String path : oldPaths.keySet()) {
                if (!newPaths.containsKey(path)) {
                    breaking.add(new ChangeItem(
                            ChangeItem.Type.BREAKING, path,
                            "Endpoint removed — all clients calling this will get 404"
                    ));
                    continue;
                }

                // 2. Compare operations on same path
                PathItem oldItem = oldPaths.get(path);
                PathItem newItem = newPaths.get(path);
                compareOperations(path, oldItem, newItem, breaking, nonBreaking);
            }

            // 3. Check for added endpoints (NON-BREAKING)
            for (String path : newPaths.keySet()) {
                if (!oldPaths.containsKey(path)) {
                    nonBreaking.add(new ChangeItem(
                            ChangeItem.Type.NON_BREAKING, path,
                            "New endpoint added"
                    ));
                }
            }

            boolean hasBreaking = !breaking.isEmpty();
            String summary = buildSummary(breaking, nonBreaking);

            List<ChangeItem> allChanges = new ArrayList<>();
            allChanges.addAll(breaking);
            allChanges.addAll(nonBreaking);

            String changesJson = objectMapper.writeValueAsString(allChanges);

            return DiffResult.builder()
                    .hasBreakingChanges(hasBreaking)
                    .breakingCount(breaking.size())
                    .nonBreakingCount(nonBreaking.size())
                    .summary(summary)
                    .changes(allChanges)
                    .changesJson(changesJson)
                    .isFirstRun(false)
                    .build();

        } catch (Exception e) {
            log.error("Error diffing specs: {}", e.getMessage(), e);
            return DiffResult.error("Failed to process spec: " + e.getMessage());
        }
    }

    // ... (rest of the methods remain exactly as provided)
    private void compareOperations(String path, PathItem oldItem, PathItem newItem, List<ChangeItem> breaking, List<ChangeItem> nonBreaking) {
        Map<String, Operation> oldOps = getOperations(oldItem);
        Map<String, Operation> newOps = getOperations(newItem);

        for (String method : oldOps.keySet()) {
            if (!newOps.containsKey(method)) {
                breaking.add(new ChangeItem(ChangeItem.Type.BREAKING, method + " " + path, "HTTP method removed"));
                continue;
            }
            compareParameters(method + " " + path, oldOps.get(method), newOps.get(method), breaking, nonBreaking);
            compareRequestBody(method + " " + path, oldOps.get(method), newOps.get(method), breaking, nonBreaking);
            compareResponses(method + " " + path, oldOps.get(method), newOps.get(method), breaking, nonBreaking);
        }

        for (String method : newOps.keySet()) {
            if (!oldOps.containsKey(method)) {
                nonBreaking.add(new ChangeItem(ChangeItem.Type.NON_BREAKING, method + " " + path, "New HTTP method added"));
            }
        }
    }

    private void compareParameters(String endpoint, Operation oldOp, Operation newOp, List<ChangeItem> breaking, List<ChangeItem> nonBreaking) {
        List<Parameter> oldParams = oldOp.getParameters() != null ? oldOp.getParameters() : new ArrayList<>();
        List<Parameter> newParams = newOp.getParameters() != null ? newOp.getParameters() : new ArrayList<>();
        Map<String, Parameter> oldParamMap = new HashMap<>();
        for (Parameter p : oldParams) oldParamMap.put(p.getName() + "_" + p.getIn(), p);
        Map<String, Parameter> newParamMap = new HashMap<>();
        for (Parameter p : newParams) newParamMap.put(p.getName() + "_" + p.getIn(), p);

        for (Map.Entry<String, Parameter> entry : oldParamMap.entrySet()) {
            Parameter oldParam = entry.getValue();
            if (!newParamMap.containsKey(entry.getKey())) {
                if (Boolean.TRUE.equals(oldParam.getRequired())) {
                    breaking.add(new ChangeItem(ChangeItem.Type.BREAKING, endpoint, "Required parameter '" + oldParam.getName() + "' (" + oldParam.getIn() + ") removed"));
                } else {
                    nonBreaking.add(new ChangeItem(ChangeItem.Type.NON_BREAKING, endpoint, "Optional parameter '" + oldParam.getName() + "' removed"));
                }
                continue;
            }
            Parameter newParam = newParamMap.get(entry.getKey());
            if (!Boolean.TRUE.equals(oldParam.getRequired()) && Boolean.TRUE.equals(newParam.getRequired())) {
                breaking.add(new ChangeItem(ChangeItem.Type.BREAKING, endpoint, "Parameter '" + oldParam.getName() + "' changed from optional → required"));
            }
        }
        for (Map.Entry<String, Parameter> entry : newParamMap.entrySet()) {
            if (!oldParamMap.containsKey(entry.getKey()) && Boolean.TRUE.equals(entry.getValue().getRequired())) {
                breaking.add(new ChangeItem(ChangeItem.Type.BREAKING, endpoint, "New required parameter '" + entry.getValue().getName() + "' added"));
            }
        }
    }

    private void compareRequestBody(String endpoint, Operation oldOp, Operation newOp, List<ChangeItem> breaking, List<ChangeItem> nonBreaking) {
        boolean hadBody = oldOp.getRequestBody() != null;
        boolean hasBody = newOp.getRequestBody() != null;
        if (hadBody && !hasBody) {
            nonBreaking.add(new ChangeItem(ChangeItem.Type.NON_BREAKING, endpoint, "Request body removed"));
        } else if (!hadBody && hasBody && Boolean.TRUE.equals(newOp.getRequestBody().getRequired())) {
            breaking.add(new ChangeItem(ChangeItem.Type.BREAKING, endpoint, "Required request body added"));
        }
    }

    private void compareResponses(String endpoint, Operation oldOp, Operation newOp, List<ChangeItem> breaking, List<ChangeItem> nonBreaking) {
        if (oldOp.getResponses() == null) return;
        oldOp.getResponses().forEach((statusCode, oldResponse) -> {
            if (newOp.getResponses() == null || !newOp.getResponses().containsKey(statusCode)) {
                breaking.add(new ChangeItem(ChangeItem.Type.BREAKING, endpoint, "Response code " + statusCode + " removed"));
            }
        });
    }

    private Map<String, Operation> getOperations(PathItem item) {
        Map<String, Operation> ops = new LinkedHashMap<>();
        if (item.getGet() != null) ops.put("GET", item.getGet());
        if (item.getPost() != null) ops.put("POST", item.getPost());
        if (item.getPut() != null) ops.put("PUT", item.getPut());
        if (item.getPatch() != null) ops.put("PATCH", item.getPatch());
        if (item.getDelete() != null) ops.put("DELETE", item.getDelete());
        return ops;
    }

    private OpenAPI parseSpec(String content) {
        try {
            ParseOptions options = new ParseOptions();
            options.setResolve(true);
            SwaggerParseResult result = new OpenAPIV3Parser().readContents(content, null, options);
            return result.getOpenAPI();
        } catch (Exception e) {
            log.error("Failed to parse spec: {}", e.getMessage());
            return null;
        }
    }

    private String buildSummary(List<ChangeItem> breaking, List<ChangeItem> nonBreaking) {
        if (breaking.isEmpty() && nonBreaking.isEmpty()) return "No changes detected in OpenAPI spec.";
        StringBuilder sb = new StringBuilder();
        if (!breaking.isEmpty()) {
            sb.append("🚨 *").append(breaking.size()).append(" BREAKING CHANGE(S)*\n");
            for (ChangeItem item : breaking) sb.append("  ✗ `").append(item.getEndpoint()).append("` — ").append(item.getDescription()).append("\n");
        }
        if (!nonBreaking.isEmpty()) {
            if (!breaking.isEmpty()) sb.append("\n");
            sb.append("ℹ️ *").append(nonBreaking.size()).append(" non-breaking change(s)*\n");
            for (ChangeItem item : nonBreaking) sb.append("  ⚠ `").append(item.getEndpoint()).append("` — ").append(item.getDescription()).append("\n");
        }
        return sb.toString().trim();
    }
}