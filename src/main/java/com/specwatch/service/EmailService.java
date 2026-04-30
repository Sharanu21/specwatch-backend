
package com.specwatch.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import com.specwatch.dto.ChangeItem;
import com.specwatch.dto.DiffResult;
import com.specwatch.model.Project;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    private final Resend resend;
    private final String fromEmail;

    public EmailService(
            @Value("${resend.api.key:}") String apiKey,
            @Value("${resend.from.email:notifications@specwatch.dev}") String fromEmail
    ) {
        this.resend = apiKey != null && !apiKey.isBlank() ? new Resend(apiKey) : null;
        this.fromEmail = fromEmail;
    }

    public boolean sendBreakingChangeAlert(
            String toEmail,
            Project project,
            DiffResult result,
            String commitSha,
            String pushedBy
    ) {
        if (resend == null) {
            log.warn("Resend API key not configured — skipping email");
            return false;
        }

        // Only send email for breaking changes
        if (!result.isHasBreakingChanges()) {
            return false;
        }

        try {
            String subject = "🚨 Breaking API Change in " + project.getName();
            String html = buildEmailHtml(project, result, commitSha, pushedBy);

            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(fromEmail)
                    .to(toEmail)
                    .subject(subject)
                    .html(html)
                    .build();

            CreateEmailResponse response = resend.emails().send(params);
            log.info("Email sent to {} for project {}", toEmail, project.getName());
            return true;

        } catch (ResendException e) {
            log.error("Failed to send email: {}", e.getMessage());
            return false;
        }
    }

    private String buildEmailHtml(Project project, DiffResult result, String commitSha, String pushedBy) {
        StringBuilder sb = new StringBuilder();

        sb.append("""
            <!DOCTYPE html>
            <html>
            <head>
            <style>
                body { font-family: -apple-system, sans-serif; background: #0a0a0f; color: #e8e8f0; margin: 0; padding: 0; }
                .container { max-width: 600px; margin: 0 auto; padding: 40px 20px; }
                .header { text-align: center; margin-bottom: 32px; }
                .logo { font-size: 24px; font-weight: 700; font-family: monospace; color: #e8e8f0; }
                .logo span { color: #ff4d6d; }
                .alert-box { background: rgba(255,77,109,0.1); border: 1px solid #ff4d6d; border-radius: 10px; padding: 24px; margin-bottom: 24px; }
                .alert-title { font-size: 18px; font-weight: 700; color: #ff4d6d; margin-bottom: 8px; }
                .meta { background: #111118; border-radius: 8px; padding: 16px; margin-bottom: 24px; font-size: 14px; }
                .meta-row { display: flex; justify-content: space-between; margin-bottom: 8px; }
                .meta-label { color: #6b6b80; }
                .meta-value { color: #e8e8f0; font-family: monospace; }
                .change-item { background: #111118; border-left: 3px solid #ff4d6d; border-radius: 4px; padding: 12px 16px; margin-bottom: 8px; }
                .change-endpoint { font-family: monospace; font-size: 13px; color: #e8e8f0; font-weight: 600; }
                .change-desc { font-size: 13px; color: #6b6b80; margin-top: 4px; }
                .footer { text-align: center; font-size: 12px; color: #6b6b80; margin-top: 32px; }
                .btn { display: inline-block; background: #ff4d6d; color: white; padding: 12px 24px; border-radius: 6px; text-decoration: none; font-weight: 600; margin-top: 16px; }
            </style>
            </head>
            <body>
            <div class="container">
                <div class="header">
                    <div class="logo">Spec<span>Watch</span></div>
                </div>
            """);

        sb.append("<div class=\"alert-box\">");
        sb.append("<div class=\"alert-title\">🚨 ")
                .append(result.getBreakingCount())
                .append(" Breaking Change")
                .append(result.getBreakingCount() > 1 ? "s" : "")
                .append(" Detected</div>");
        sb.append("<div style=\"font-size:14px;color:#e8e8f0;\">Your API spec in <strong>")
                .append(project.getName())
                .append("</strong> has breaking changes that may affect your clients.</div>");
        sb.append("</div>");

        // Meta info
        sb.append("<div class=\"meta\">");
        sb.append("<div class=\"meta-row\"><span class=\"meta-label\">Repository</span><span class=\"meta-value\">")
                .append(project.getRepoFullName()).append("</span></div>");
        sb.append("<div class=\"meta-row\"><span class=\"meta-label\">Branch</span><span class=\"meta-value\">")
                .append(project.getBranch()).append("</span></div>");
        if (commitSha != null) {
            sb.append("<div class=\"meta-row\"><span class=\"meta-label\">Commit</span><span class=\"meta-value\">")
                    .append(commitSha, 0, Math.min(7, commitSha.length())).append("</span></div>");
        }
        if (pushedBy != null) {
            sb.append("<div class=\"meta-row\"><span class=\"meta-label\">Pushed by</span><span class=\"meta-value\">")
                    .append(pushedBy).append("</span></div>");
        }
        sb.append("</div>");

        // Breaking changes
        sb.append("<div style=\"margin-bottom:24px;\">");
        sb.append("<div style=\"font-size:14px;font-weight:600;margin-bottom:12px;color:#ff4d6d;\">Breaking Changes</div>");

        if (result.getChanges() != null) {
            result.getChanges().stream()
                    .filter(c -> c.getType() == ChangeItem.Type.BREAKING)
                    .forEach(c -> {
                        sb.append("<div class=\"change-item\">");
                        sb.append("<div class=\"change-endpoint\">✗ ").append(c.getEndpoint()).append("</div>");
                        sb.append("<div class=\"change-desc\">").append(c.getDescription()).append("</div>");
                        sb.append("</div>");
                    });
        }
        sb.append("</div>");

        // CTA
        sb.append("<div style=\"text-align:center;\">");
        sb.append("<a href=\"https://specwatch.netlify.app\" class=\"btn\">View Full Report →</a>");
        sb.append("</div>");

        sb.append("""
                <div class="footer">
                    <p>You're receiving this because you set up SpecWatch monitoring for this repository.</p>
                    <p>SpecWatch — API Breaking Change Detector</p>
                </div>
            </div>
            </body>
            </html>
            """);

        return sb.toString();
    }
}