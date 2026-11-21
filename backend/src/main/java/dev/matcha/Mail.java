package dev.matcha;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

public final class Mail {
    private final Config config;
    private final Session session;

    public Mail(Config config) {
        this.config = config;
        Properties properties = new Properties();
        properties.setProperty("mail.smtp.host", config.smtpHost());
        properties.setProperty("mail.smtp.port", String.valueOf(config.smtpPort()));
        properties.setProperty("mail.smtp.connectiontimeout", "5000");
        properties.setProperty("mail.smtp.timeout", "5000");
        properties.setProperty("mail.smtp.writetimeout", "5000");
        session = Session.getInstance(properties);
    }

    public void send(String to, String subject, String body) {
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(config.mailFrom()));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(subject, "UTF-8");
            message.setText(body, "UTF-8");
            Transport.send(message);
        } catch (jakarta.mail.MessagingException error) {
            throw new ApiError(503, "mail_unavailable", "Email delivery is temporarily unavailable. Please try again.");
        }
    }
}
