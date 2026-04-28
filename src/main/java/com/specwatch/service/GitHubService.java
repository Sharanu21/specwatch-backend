package com.specwatch.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Map;

@Service
@Slf4j
public class GitHubService {

    private final WebClient webClient;

    // Inject the GITHUB_TOKEN environment variable directly into the constructor
    public GitHubService(@Value("${GITHUB_TOKEN:}") String githubToken) {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .defaultHeader("User-Agent", "SpecWatch/1.0");

        // If the token exists, attach it to the API requests
        if (githubToken != null && !githubToken.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + githubToken);
            log.info("✅ GitHub Token successfully loaded into WebClient");
        } else {
            log.warn("⚠️ No GITHUB_TOKEN found. GitHub API requests might fail with 403 Forbidden.");
        }

        this.webClient = builder.build();
    }

    /**
     * Fetches the raw content of a file from a GitHub repo at a specific commit/ref.
     *
     * @param repoFullName  e.g. "octocat/hello-world"
     * @param filePath      e.g. "docs/openapi.yaml"
     * @param ref           commit SHA or branch name e.g. "main" or "abc123def"
     * @return raw file content as String
     */
    public String fetchFileContent(String repoFullName, String filePath, String ref) {
        try {
            String url = "/repos/" + repoFullName + "/contents/" + filePath + "?ref=" + ref;

            Map response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("content")) {
                throw new RuntimeException("File not found: " + filePath + " in " + repoFullName);
            }

            // GitHub returns content as base64
            String encodedContent = (String) response.get("content");
            // Remove newlines GitHub adds in the base64 string
            encodedContent = encodedContent.replaceAll("\\s", "");

            byte[] decodedBytes = Base64.getDecoder().decode(encodedContent);
            return new String(decodedBytes);

        } catch (Exception e) {
            log.error("Failed to fetch file {} from {}: {}", filePath, repoFullName, e.getMessage());
            throw new RuntimeException("GitHub fetch failed: " + e.getMessage());
        }
    }

    /**
     * Validates that a GitHub webhook signature is genuine.
     * GitHub signs payloads with HMAC-SHA256 using your webhook secret.
     */
    public boolean isValidSignature(String payload, String signatureHeader, String secret) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) return false;

        try {
            String expectedSig = signatureHeader.substring(7);
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"
            ));
            byte[] hmac = mac.doFinal(
                    payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
            String actualSig = bytesToHex(hmac);
            return expectedSig.equalsIgnoreCase(actualSig);
        } catch (Exception e) {
            log.error("Signature validation error: {}", e.getMessage());
            return false;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}