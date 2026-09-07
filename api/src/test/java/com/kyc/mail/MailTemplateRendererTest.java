package com.kyc.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MailTemplateRendererTest {

    private final MailTemplateRenderer renderer = new MailTemplateRenderer();

    @Test
    void rendersVerifyWithBrandAccentAndRedirectNotice() {
        String html = renderer.render(
                "email_verify", "http://localhost:3000/verify?token=abc", "owner@example.com");

        assertThat(html).contains("Confirmez votre adresse");
        assertThat(html).contains("#0f7a4d");
        assertThat(html).contains("Recogniz-Me");
        assertThat(html).contains("owner@example.com");
        assertThat(html).contains("http://localhost:3000/verify?token=abc");
    }

    @Test
    void omitsRedirectBannerWhenNoticeBlank() {
        String html = renderer.render("password_reset", "http://localhost:3000/reset?token=xyz", null);

        assertThat(html).contains("Nouveau mot de passe");
        assertThat(html).doesNotContain("Environnement de test");
    }
}
