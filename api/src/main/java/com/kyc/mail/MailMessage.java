package com.kyc.mail;

public enum MailMessage {
    EMAIL_VERIFY("email_verify", "Vérifiez votre e-mail — Recogniz-Me"),
    PASSWORD_RESET("password_reset", "Réinitialisez votre mot de passe — Recogniz-Me"),
    TEAM_INVITE("team_invite", "Invitation à rejoindre une équipe — Recogniz-Me");

    private final String template;
    private final String subject;

    MailMessage(String template, String subject) {
        this.template = template;
        this.subject = subject;
    }

    public String template() {
        return template;
    }

    public String subject() {
        return subject;
    }

    public static MailMessage of(String template) {
        for (MailMessage message : values()) {
            if (message.template.equals(template)) {
                return message;
            }
        }
        throw new IllegalArgumentException("Unknown mail template: " + template);
    }
}
