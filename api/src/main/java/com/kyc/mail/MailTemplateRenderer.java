package com.kyc.mail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class MailTemplateRenderer {

    public String render(String template, String url, String redirectNotice) {
        return render(template, url, redirectNotice, Map.of());
    }

    public String render(String template, String url, String redirectNotice, Map<String, String> extras) {
        String body = load("mail/" + template + ".html");
        String layout = load("mail/layout.html");
        String escapedUrl = HtmlUtils.htmlEscape(url);
        String notice = redirectNotice == null || redirectNotice.isBlank()
                ? ""
                : load("mail/redirect-notice.html")
                        .replace("{{redirect_notice}}", HtmlUtils.htmlEscape(redirectNotice));
        Map<String, String> values = new LinkedHashMap<>();
        values.put("{{url}}", escapedUrl);
        values.put("{{redirect_notice}}", notice);
        values.put("{{year}}", String.valueOf(Year.now().getValue()));
        if (extras != null) {
            extras.forEach((key, value) -> {
                String placeholder = key.startsWith("{{") ? key : "{{" + key + "}}";
                values.put(placeholder, HtmlUtils.htmlEscape(value == null ? "" : value));
            });
        }
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
