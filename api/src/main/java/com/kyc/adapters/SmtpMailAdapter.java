package com.kyc.adapters;

import com.kyc.config.KycProperties;
import com.kyc.mail.MailMessage;
import com.kyc.mail.MailTemplateRenderer;
import com.kyc.ports.MailPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "kyc.mail", name = "mode", havingValue = "smtp")
public class SmtpMailAdapter implements MailPort {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailAdapter.class);

    private final JavaMailSender mailSender;
    private final KycProperties properties;
    private final MailTemplateRenderer renderer;
    private final Map<String, String> lastUrls = new ConcurrentHashMap<>();
    private final Map<String, String> lastByTemplate = new ConcurrentHashMap<>();

    public SmtpMailAdapter(JavaMailSender mailSender, KycProperties properties, MailTemplateRenderer renderer) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.renderer = renderer;
    }

    @Override
    public void send(String to, String correlationId, String template, String url) {
        lastUrls.put(correlationId, url);
        lastByTemplate.put(template, url);
        MailMessage message = MailMessage.of(template);
        String intendedTo = to == null ? "" : to.trim();
        String html = renderer.render(template, url, redirectNotice(intendedTo));
        sendHtml(intendedTo, message.subject(), html);
        log.info("mail sent to={} intended={} template={}", resolveTo(intendedTo), intendedTo, template);
    }

    @Override
    public Optional<String> lastUrl(String correlationId) {
        return Optional.ofNullable(lastUrls.get(correlationId));
    }

    @Override
    public Optional<String> lastUrlForTemplate(String template) {
        return Optional.ofNullable(lastByTemplate.get(template));
    }

    private void sendHtml(String intendedTo, String subject, String html) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.mail().fromAddress());
            helper.setTo(resolveTo(intendedTo));
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(mime);
        } catch (MailAuthenticationException e) {
            throw new IllegalStateException(
                    "SMTP authentication failed" + smtpEndpointHint()
                            + " — définir spring.mail.password (application-secrets.properties) ou KYC_SMTP_PASSWORD."
                            + " Cause: " + e.getMessage(),
                    e);
        } catch (MailException e) {
            throw new IllegalStateException("SMTP send failed" + smtpEndpointHint() + ": " + e.getMessage(), e);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to build email message: " + e.getMessage(), e);
        }
    }

    private String resolveTo(String intendedTo) {
        String redirectTo = properties.mail().redirectTo();
        if (redirectTo.isEmpty()) {
            return intendedTo;
        }
        if (!redirectTo.equalsIgnoreCase(intendedTo)) {
            log.info("mail redirect {} → {}", intendedTo, redirectTo);
        }
        return redirectTo;
    }

    private String redirectNotice(String intendedTo) {
        String redirectTo = properties.mail().redirectTo();
        if (redirectTo.isEmpty() || intendedTo.isEmpty() || intendedTo.equalsIgnoreCase(redirectTo)) {
            return null;
        }
        return intendedTo;
    }

    private String smtpEndpointHint() {
        if (!(mailSender instanceof JavaMailSenderImpl impl)) {
            return "";
        }
        String user = impl.getUsername();
        boolean userBlank = user == null || user.isBlank();
        boolean passwordBlank = impl.getPassword() == null || impl.getPassword().isBlank();
        return " [host=" + impl.getHost()
                + ":" + impl.getPort()
                + ", user=" + (userBlank ? "<vide>" : user)
                + ", password=" + (passwordBlank ? "<vide>" : "<défini>")
                + "]";
    }
}
