package com.capstone.etp.notifications;

import com.capstone.etp.preferences.AlertChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;


@Slf4j
@Component
class EmailChannelSender implements ChannelSender {

    private static final String DEFAULT_FROM = "no-reply@quorum.local";

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String smtpHost;
    private final String from;
    private final EmailTemplateRenderer renderer;

    EmailChannelSender(ObjectProvider<JavaMailSender> mailSender,
                       @Value("${spring.mail.host:}") String smtpHost,
                       @Value("${app.notifications.mail.from:no-reply@quorum.local}") String from,
                       EmailTemplateRenderer renderer) {
        this.renderer = renderer;
        this.mailSender = mailSender;
        this.smtpHost = smtpHost;
        // An empty SMTP_FROM in the environment arrives as a blank value, not as a missing one.
        this.from = from == null || from.isBlank() ? DEFAULT_FROM : from.trim();
    }

    @Override
    public AlertChannel channel() {
        return AlertChannel.EMAIL;
    }

    @Override
    public Mode send(OutboundMessage message) throws DeliveryException {
        JavaMailSender sender = smtpHost == null || smtpHost.isBlank() ? null : mailSender.getIfAvailable();
        if (sender == null) {
            log.info("[NOTIFY-STUB:EMAIL] no SMTP host configured; not sent. to={}", message.masked());
            return Mode.STUB;
        }
        try {
            MimeMessage mime = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(message.destination());
            helper.setSubject(message.subject());
            boolean styled = false;
            if (message.kind() != null) {
                try {
                    // HTML with a plain-text alternative, for clients that show only text.
                    EmailTemplateRenderer.Rendered rendered = renderer.render(
                            message.kind(), message.mascot(), message.subject(), message.body(), java.time.Instant.now());
                    helper.setText(rendered.text(), rendered.html());
                    // The animated mascot rides inside the message, referenced from the HTML as cid:mascot.
                    ClassPathResource mascot = new ClassPathResource(rendered.mascotResource());
                    if (mascot.exists()) {
                        helper.addInline("mascot", mascot, "image/gif");
                    }
                    styled = true;
                } catch (RuntimeException e) {
                    log.warn("[NOTIFY] the styled e-mail could not be built ({}); sending it as plain text", e.getClass().getSimpleName());
                }
            }
            if (!styled) {
                helper.setText(message.body(), false);
            }
            sender.send(mime);
            log.info("[NOTIFY] e-mail sent to={}", message.masked());
            return Mode.LIVE;
        } catch (MailException | MessagingException e) {
            // The exception text can quote the address; keep only the types, which say what went wrong
            // (authentication, a refused recipient, a timeout, a refused connection).
            throw new DeliveryException("E-mail provider failed: " + e.getClass().getSimpleName() + causeOf(e), e);
        }
    }

    /** " (SocketTimeoutException)" for the innermost cause, or nothing. */
    private static String causeOf(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root == e ? "" : " (" + root.getClass().getSimpleName() + ")";
    }
}
