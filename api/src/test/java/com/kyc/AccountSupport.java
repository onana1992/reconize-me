package com.kyc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.ports.MailPort;
import com.kyc.services.SessionService;
import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MvcResult;

final class AccountSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    private AccountSupport() {}

    static String userId(MvcResult signup) throws Exception {
        return field(signup, "user_id");
    }

    static String field(MvcResult result, String name) throws Exception {
        JsonNode node = JSON.readTree(result.getResponse().getContentAsString());
        return node.get(name).asText();
    }

    static String tokenFromMail(MailPort mail, String userId, String queryKey) {
        String url = mail.lastUrl(userId).orElseThrow(() -> new IllegalStateException("No mail for " + userId));
        return query(url, queryKey);
    }

    static String tokenFromTemplate(MailPort mail, String template, String queryKey) {
        String url = mail.lastUrlForTemplate(template)
                .orElseThrow(() -> new IllegalStateException("No mail for template " + template));
        return query(url, queryKey);
    }

    static Cookie session(MvcResult login) {
        Cookie cookie = login.getResponse().getCookie(SessionService.COOKIE_NAME);
        if (cookie == null) {
            throw new IllegalStateException("Missing rm_session cookie");
        }
        return cookie;
    }

    private static String query(String url, String key) {
        int index = url.indexOf(key);
        if (index < 0) {
            throw new IllegalStateException("Query " + key + " missing in " + url);
        }
        String value = url.substring(index + key.length());
        int amp = value.indexOf('&');
        return amp < 0 ? value : value.substring(0, amp);
    }
}
