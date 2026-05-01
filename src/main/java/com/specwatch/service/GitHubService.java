package com.specwatch.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Service
@Slf4j
public class GitHubService {

    private final WebClient webClient;
    private final String githubBaseUrl;

    public GitHubService(
            @Value("${GITHUB_TOKEN:}") String githubToken,
            @Value("${github.api.base-url}") String githubBaseUrl) {

        this.githubBaseUrl = githubBaseUrl;

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(githubBaseUrl)
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .defaultHeader("User-Agent", "SpecWatch/1.0");

        if (githubToken != null && !githubToken.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + githubToken);
            log.info("✅ GitHub Token successfully loaded into WebClient");
        } else {
            log.warn("⚠️ No GITHUB_TOKEN found. GitHub API requests might fail with 403 Forbidden.");
        }

        this.webClient = builder.build();
    }

    // Overloaded method to support project-specific tokens
    public String fetchFileContent(String repoFullName, String filePath, String ref, String overrideToken) {
        try {
            String url = "/repos/" + repoFullName + "/contents/" + filePath + "?ref=" + ref;

            // Use specialized client if token is provided, otherwise use default
            WebClient client = (overrideToken != null && !overrideToken.isBlank())
                    ? WebClient.builder()
                    .baseUrl(githubBaseUrl)
                    .defaultHeader("Accept", "application/vnd.github.v3+json")
                    .defaultHeader("User-Agent", "SpecWatch/1.0")
                    .defaultHeader("Authorization", "Bearer " + overrideToken)
                    .build()
                    : this.webClient;

            Map response = client.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response == null || !response.containsKey("content")) {
                throw new RuntimeException("File not found: " + filePath + " in " + repoFullName);
            }

            String encodedContent = (String) response.get("content");
            encodedContent = encodedContent.replaceAll("\\s", "");

            byte[] decodedBytes = Base64.getDecoder().decode(encodedContent);
            return new String(decodedBytes);

        } catch (Exception e) {
            log.error("Failed to fetch file {} from {}: {}", filePath, repoFullName, e.getMessage());
            throw new RuntimeException("GitHub fetch failed: " + e.getMessage());
        }
    }

    // Existing method for backward compatibility
    public String fetchFileContent(String repoFullName, String filePath, String ref) {
        return fetchFileContent(repoFullName, filePath, ref, null);
    }

    public boolean isValidSignatureBytes(byte[] rawPayload, String signatureHeader, String secret) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            log.warn("Invalid or missing signature header");
            return false;
        }

        try {
            String expectedSig = signatureHeader.substring(7).trim();
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            ));

            byte[] hmac = mac.doFinal(rawPayload);
            String actualSig = bytesToHex(hmac);

            return expectedSig.equalsIgnoreCase(actualSig);
        } catch (Exception e) {
            log.error("Signature validation error: {}", e.getMessage());
            return false;
        }
    }

    public boolean isValidSignature(String payload, String signatureHeader, String secret) {
        if (payload == null) return false;
        return isValidSignatureBytes(payload.getBytes(StandardCharsets.UTF_8), signatureHeader, secret);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}