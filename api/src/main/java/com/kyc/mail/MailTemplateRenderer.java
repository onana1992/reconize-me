package com.kyc.mail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class MailTemplateRenderer {

    public String render(String template, String url, String redirectNotice) {
        String body = load("mail/" + template + ".html");
        String layout = load("mail/layout.html");
        String escapedUrl = HtmlUtils.htmlEscape(url);
        String notice = redirectNotice == null || redirectNotice.isBlank()
                ? ""
                : load("mail/redirect-notice.html")
                        .replace("{{redirect_notice}}", HtmlUtils.htmlEscape(redirectNotice));
        Map<String, String> values = Map.of(
                "{{url}}",
                escapedUrl,
                "{{redirect_notice}}",
                notice,
                "{{year}}",
                String.valueOf(Year.now().getValue()));
        String filledBody = apply(body, values);
        return apply(layout.replace("{{body}}", filledBody), values);
    }

    private static String apply(String html, Map<String, String> values) {
        String result = html;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static String load(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream stream = resource.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Missing mail template " + path, e);
        }
    }
}
