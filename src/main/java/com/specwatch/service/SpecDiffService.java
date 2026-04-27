package com.specwatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
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

    private void compareOperations(
            String path,
            PathItem oldItem,
            PathItem newItem,
            List<ChangeItem> breaking,
            List<ChangeItem> nonBreaking
    ) {
        Map<String, Operation> oldOps = getOperations(oldItem);
        Map<String, Operation> newOps = getOperations(newItem);

        // Removed HTTP methods (BREAKING)
        for (String method : oldOps.keySet()) {
            if (!newOps.containsKey(method)) {
                breaking.add(new ChangeItem(
                        ChangeItem.Type.BREAKING,
                        method + " " + path,
                        "HTTP method removed"
                ));
                continue;
            }

            Operation oldOp = oldOps.get(method);
            Operation newOp = newOps.get(method);
            String endpoint = method + " " + path;

            // Compare parameters
            compareParameters(endpoint, oldOp, newOp, breaking, nonBreaking);

            // Compare request body
            compareRequestBody(endpoint, oldOp, newOp, breaking, nonBreaking);

            // Compare responses
            compareResponses(endpoint, oldOp, newOp, breaking, nonBreaking);
        }

        // Added HTTP methods (NON-BREAKING)
        for (String method : newOps.keySet()) {
            if (!oldOps.containsKey(method)) {
                nonBreaking.add(new ChangeItem(
                        ChangeItem.Type.NON_BREAKING,
                        method + " " + path,
                        "New HTTP method added"
                ));
            }
        }
    }

    private void compareParameters(
            String endpoint,
            Operation oldOp,
            Operation newOp,
            List<ChangeItem> breaking,
            List<ChangeItem> nonBreaking
    ) {
        List<Parameter> oldParams = oldOp.getParameters() != null ? oldOp.getParameters() : new ArrayList<>();
        List<Parameter> newParams = newOp.getParameters() != null ? newOp.getParameters() : new ArrayList<>();

        Map<String, Parameter> oldParamMap = new HashMap<>();
        for (Parameter p : oldParams) oldParamMap.put(p.getName() + "_" + p.getIn(), p);

        Map<String, Parameter> newParamMap = new HashMap<>();
        for (Parameter p : newParams) newParamMap.put(p.getName() + "_" + p.getIn(), p);

        // Removed parameters
        for (Map.Entry<String, Parameter> entry : oldParamMap.entrySet()) {
            Parameter oldParam = entry.getValue();
            if (!newParamMap.containsKey(entry.getKey())) {
                if (Boolean.TRUE.equals(oldParam.getRequired())) {
                    breaking.add(new ChangeItem(
                            ChangeItem.Type.BREAKING, endpoint,
                            "Required parameter '" + oldParam.getName() + "' (" + oldParam.getIn() + ") removed"
                    ));
                } else {
                    nonBreaking.add(new ChangeItem(
                            ChangeItem.Type.NON_BREAKING, endpoint,
                            "Optional parameter '" + oldParam.getName() + "' removed"
                    ));
                }
                continue;
            }

            // Check required change: optional → required (BREAKING for clients)
            Parameter newParam = newParamMap.get(entry.getKey());
            boolean wasRequired = Boolean.TRUE.equals(oldParam.getRequired());
            boolean isRequired = Boolean.TRUE.equals(newParam.getRequired());

            if (!wasRequired && isRequired) {
                breaking.add(new ChangeItem(
                        ChangeItem.Type.BREAKING, endpoint,
                        "Parameter '" + oldParam.getName() + "' changed from optional → required"
                ));
            } else if (wasRequired && !isRequired) {
                nonBreaking.add(new ChangeItem(
                        ChangeItem.Type.NON_BREAKING, endpoint,
                        "Parameter '" + oldParam.getName() + "' changed from required → optional"
                ));
            }

            // Check type change
            if (oldParam.getSchema() != null && newParam.getSchema() != null) {
                String oldType = oldParam.getSchema().getType();
                String newType = newParam.getSchema().getType();
                if (oldType != null && !oldType.equals(newType)) {
                    breaking.add(new ChangeItem(
                            ChangeItem.Type.BREAKING, endpoint,
                            "Parameter '" + oldParam.getName() + "' type changed: " + oldType + " → " + newType
                    ));
                }
            }
        }

        // Added required parameters (BREAKING — existing clients won't send them)
        for (Map.Entry<String, Parameter> entry : newParamMap.entrySet()) {
            if (!oldParamMap.containsKey(entry.getKey())) {
                Parameter newParam = entry.getValue();
                if (Boolean.TRUE.equals(newParam.getRequired())) {
                    breaking.add(new ChangeItem(
                            ChangeItem.Type.BREAKING, endpoint,
                            "New required parameter '" + newParam.getName() + "' added — existing clients won't send it"
                    ));
                } else {
                    nonBreaking.add(new ChangeItem(
                            ChangeItem.Type.NON_BREAKING, endpoint,
                            "New optional parameter '" + newParam.getName() + "' added"
                    ));
                }
            }
        }
    }

    private void compareRequestBody(
            String endpoint,
            Operation oldOp,
            Operation newOp,
            List<ChangeItem> breaking,
            List<ChangeItem> nonBreaking
    ) {
        boolean hadBody = oldOp.getRequestBody() != null;
        boolean hasBody = newOp.getRequestBody() != null;

        if (hadBody && !hasBody) {
            nonBreaking.add(new ChangeItem(
                    ChangeItem.Type.NON_BREAKING, endpoint,
                    "Request body removed"
            ));
            return;
        }

        if (!hadBody && hasBody) {
            boolean required = Boolean.TRUE.equals(newOp.getRequestBody().getRequired());
            if (required) {
                breaking.add(new ChangeItem(
                        ChangeItem.Type.BREAKING, endpoint,
                        "Required request body added — existing clients won't send it"
                ));
            }
            return;
        }

        if (!hadBody) return;

        // Compare schemas inside request body
        try {
            Schema<?> oldSchema = extractSchema(oldOp.getRequestBody().getContent());
            Schema<?> newSchema = extractSchema(newOp.getRequestBody().getContent());
            compareSchemas(endpoint + " [request body]", oldSchema, newSchema, breaking, nonBreaking, false);
        } catch (Exception e) {
            log.debug("Could not compare request body schemas for {}: {}", endpoint, e.getMessage());
        }
    }

    private void compareResponses(
            String endpoint,
            Operation oldOp,
            Operation newOp,
            List<ChangeItem> breaking,
            List<ChangeItem> nonBreaking
    ) {
        if (oldOp.getResponses() == null) return;

        oldOp.getResponses().forEach((statusCode, oldResponse) -> {
            if (newOp.getResponses() == null || !newOp.getResponses().containsKey(statusCode)) {
                breaking.add(new ChangeItem(
                        ChangeItem.Type.BREAKING, endpoint,
                        "Response code " + statusCode + " removed"
                ));
                return;
            }

            try {
                Schema<?> oldSchema = extractSchema(oldResponse.getContent());
                Schema<?> newSchema = extractSchema(newOp.getResponses().get(statusCode).getContent());
                // Response field removal is breaking, addition is non-breaking
                compareSchemas(
                        endpoint + " [response " + statusCode + "]",
                        oldSchema, newSchema,
                        breaking, nonBreaking,
                        true // isResponse
                );
            } catch (Exception e) {
                log.debug("Could not compare response schemas for {}: {}", endpoint, e.getMessage());
            }
        });
    }

    @SuppressWarnings("rawtypes")
    private void compareSchemas(
            String context,
            Schema<?> oldSchema,
            Schema<?> newSchema,
            List<ChangeItem> breaking,
            List<ChangeItem> nonBreaking,
            boolean isResponse
    ) {
        if (oldSchema == null || newSchema == null) return;

        // Type change
        if (oldSchema.getType() != null && !oldSchema.getType().equals(newSchema.getType())) {
            breaking.add(new ChangeItem(
                    ChangeItem.Type.BREAKING, context,
                    "Type changed: " + oldSchema.getType() + " → " + newSchema.getType()
            ));
            return;
        }

        Map<String, Schema> oldProps = oldSchema.getProperties() != null
                ? oldSchema.getProperties() : new HashMap<>();
        Map<String, Schema> newProps = newSchema.getProperties() != null
                ? newSchema.getProperties() : new HashMap<>();

        List<String> oldRequired = oldSchema.getRequired() != null
                ? oldSchema.getRequired() : new ArrayList<>();
        List<String> newRequired = newSchema.getRequired() != null
                ? newSchema.getRequired() : new ArrayList<>();

        // Removed fields
        for (String field : oldProps.keySet()) {
            if (!newProps.containsKey(field)) {
                if (isResponse) {
                    // Removing a response field is always breaking
                    breaking.add(new ChangeItem(
                            ChangeItem.Type.BREAKING, context,
                            "Field '" + field + "' removed from response — clients depending on it will break"
                    ));
                } else {
                    // Removing a request field
                    if (oldRequired.contains(field)) {
                        breaking.add(new ChangeItem(
                                ChangeItem.Type.BREAKING, context,
                                "Required field '" + field + "' removed from request body"
                        ));
                    } else {
                        nonBreaking.add(new ChangeItem(
                                ChangeItem.Type.NON_BREAKING, context,
                                "Optional field '" + field + "' removed"
                        ));
                    }
                }
                continue;
            }

            // Type change on existing field
            Schema<?> oldFieldSchema = (Schema<?>) oldProps.get(field);
            Schema<?> newFieldSchema = (Schema<?>) newProps.get(field);

            if (oldFieldSchema.getType() != null && newFieldSchema.getType() != null
                    && !oldFieldSchema.getType().equals(newFieldSchema.getType())) {
                breaking.add(new ChangeItem(
                        ChangeItem.Type.BREAKING, context,
                        "Field '" + field + "' type changed: "
                                + oldFieldSchema.getType() + " → " + newFieldSchema.getType()
                ));
            }

            // Required → Optional change
            if (oldRequired.contains(field) && !newRequired.contains(field)) {
                nonBreaking.add(new ChangeItem(
                        ChangeItem.Type.NON_BREAKING, context,
                        "Field '" + field + "' changed from required → optional"
                ));
            }

            // Optional → Required change (BREAKING)
            if (!oldRequired.contains(field) && newRequired.contains(field)) {
                breaking.add(new ChangeItem(
                        ChangeItem.Type.BREAKING, context,
                        "Field '" + field + "' changed from optional → required"
                ));
            }
        }

        // Added fields
        for (String field : newProps.keySet()) {
            if (!oldProps.containsKey(field)) {
                if (!isResponse && newRequired.contains(field)) {
                    breaking.add(new ChangeItem(
                            ChangeItem.Type.BREAKING, context,
                            "New required field '" + field + "' added to request — existing clients won't send it"
                    ));
                } else {
                    nonBreaking.add(new ChangeItem(
                            ChangeItem.Type.NON_BREAKING, context,
                            "New field '" + field + "' added"
                    ));
                }
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private Schema<?> extractSchema(
            Map<String, io.swagger.v3.oas.models.media.MediaType> content
    ) {
        if (content == null) return null;
        return content.values().stream()
                .filter(mt -> mt.getSchema() != null)
                .map(io.swagger.v3.oas.models.media.MediaType::getSchema)
                .findFirst()
                .orElse(null);
    }

    private Map<String, Operation> getOperations(PathItem item) {
        Map<String, Operation> ops = new LinkedHashMap<>();
        if (item.getGet() != null)    ops.put("GET",    item.getGet());
        if (item.getPost() != null)   ops.put("POST",   item.getPost());
        if (item.getPut() != null)    ops.put("PUT",    item.getPut());
        if (item.getPatch() != null)  ops.put("PATCH",  item.getPatch());
        if (item.getDelete() != null) ops.put("DELETE", item.getDelete());
        if (item.getHead() != null)   ops.put("HEAD",   item.getHead());
        if (item.getOptions() != null) ops.put("OPTIONS", item.getOptions());
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
        if (breaking.isEmpty() && nonBreaking.isEmpty()) {
            return "No changes detected in OpenAPI spec.";
        }

        StringBuilder sb = new StringBuilder();

        if (!breaking.isEmpty()) {
            sb.append("🚨 *").append(breaking.size())
                    .append(" BREAKING CHANGE").append(breaking.size() > 1 ? "S" : "").append("*\n");
            for (ChangeItem item : breaking) {
                sb.append("  ✗ `").append(item.getEndpoint()).append("` — ")
                        .append(item.getDescription()).append("\n");
            }
        }

        if (!nonBreaking.isEmpty()) {
            if (!breaking.isEmpty()) sb.append("\n");
            sb.append("ℹ️ *").append(nonBreaking.size()).append(" non-breaking change")
                    .append(nonBreaking.size() > 1 ? "s" : "").append("*\n");
            for (ChangeItem item : nonBreaking) {
                sb.append("  ⚠ `").append(item.getEndpoint()).append("` — ")
                        .append(item.getDescription()).append("\n");
            }
        }

        return sb.toString().trim();
    }
}