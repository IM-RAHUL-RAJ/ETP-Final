package com.capstone.etp.notifications;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills the HTML e-mail templates in {@code resources/email}. A template is a layout (logo, mascot, button, footer)
 * plus the words for one kind of message. Values written as {{name}} are HTML-escaped; only {{{content}}} is raw,
 * and that is a template file of ours, never customer text.
 */
@Component
class EmailTemplateRenderer {

    /** mascotResource is the classpath path of the animated GIF the message carries inline, or null if there is none. */
    record Rendered(String html, String text, String mascotResource) {
    }

    /** What differs between kinds: the template, the pose of the mascot, the colour, and where the button goes. */
    private record Style(String template, String pose, String accent, String ctaLabel, String ctaPath) {
    }

    /** The subject of a trade an automated strategy placed starts with this (see NotificationComposer). */
    static final String AUTOMATED_PREFIX = "Automated ";

    /** A strip across the top of the message: no one pressed a button, a strategy did. Our own markup, never customer text. */
    private static final String AUTOMATED_BANNER = """
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 18px 0;">
              <tr><td class="auto" style="background-color:#171717;padding:12px 16px;">
                <div style="font-family:'Courier New',Courier,monospace;font-size:14px;font-weight:700;letter-spacing:0.08em;color:#fafafa;text-align:center;">AUTOMATED TRADE TRIGGERED</div>
                <div style="font-family:'Courier New',Courier,monospace;font-size:12px;line-height:1.5;color:#fafafa;text-align:center;padding-top:4px;">Placed automatically by one of your strategies. You did not place this order.</div>
              </td></tr>
            </table>""";

    static boolean isAutomated(String subject) {
        return subject != null && subject.startsWith(AUTOMATED_PREFIX);
    }

    private static final Pattern RAW = Pattern.compile("\\{\\{\\{(\\w+)}}}");
    private static final Pattern ESCAPED = Pattern.compile("\\{\\{(\\w+)}}");
    private static final DateTimeFormatter SENT = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'", Locale.ENGLISH);

    private final Map<String, String> templates = new ConcurrentHashMap<>();
    private final String publicBaseUrl;

    EmailTemplateRenderer(@Value("${app.public.base-url}") String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    Rendered render(NotificationKind kind, String mascot, String subject, String body, Instant at) {
        return render(styleOf(kind), mascot, subject, body, at);
    }

    /** The welcome message sent when an account is opened. */
    Rendered renderWelcome(String mascot, String subject, String body, Instant at) {
        return render(new Style("welcome", "dance", "#171717", "Open Quorum", "/dashboard"), mascot, subject, body, at);
    }

    private Rendered render(Style style, String mascot, String subject, String body, Instant at) {
        String mascotKey = normaliseMascot(mascot);
        Map<String, String> values = new HashMap<>();
        values.put("subject", subject);
        values.put("body", body);
        values.put("preheader", body);
        values.put("accent", style.accent());
        values.put("ctaLabel", style.ctaLabel());
        values.put("ctaUrl", publicBaseUrl + style.ctaPath());
        values.put("settingsUrl", publicBaseUrl + "/settings");
        // The picture travels inside the message (cid:), so it shows even where the web app cannot be reached.
        values.put("mascotUrl", "cid:mascot");
        values.put("mascotName", mascotKey.substring(0, 1).toUpperCase(Locale.ROOT) + mascotKey.substring(1));
        values.put("sentAt", SENT.format((at == null ? Instant.now() : at).atOffset(ZoneOffset.UTC)));

        boolean automated = isAutomated(subject);
        String content = fill(template(style.template()), values, Map.of());
        String html = fill(template("layout"), values, Map.of("content", content, "banner", automated ? AUTOMATED_BANNER : ""));
        String text = (automated ? "*** AUTOMATED TRADE TRIGGERED ***\nPlaced automatically by one of your strategies. You did not place this order.\n\n" : "")
                + subject + "\n\n" + body + "\n\n" + style.ctaLabel() + ": " + values.get("ctaUrl") + "\n";
        return new Rendered(html, text, "email/mascots/" + mascotKey + "-" + style.pose() + ".gif");
    }

    private static String fill(String template, Map<String, String> escaped, Map<String, String> raw) {
        String out = replace(RAW, template, raw, false);
        // Raw content is filled first, so the escaped pass also fills the placeholders inside it.
        return replace(ESCAPED, out, escaped, true);
    }

    private static String replace(Pattern pattern, String input, Map<String, String> values, boolean escape) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            if (value == null) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(out, Matcher.quoteReplacement(escape ? HtmlUtils.htmlEscape(value) : value));
            }
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String template(String name) {
        return templates.computeIfAbsent(name, key -> {
            try (var in = new ClassPathResource("email/" + key + ".html").getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Missing e-mail template " + key, e);
            }
        });
    }

    static String normaliseMascot(String mascot) {
        String value = mascot == null ? "owl" : mascot.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "rabbit", "cat" -> value;
            default -> "owl";
        };
    }

    private static Style styleOf(NotificationKind kind) {
        return switch (kind) {
            case ORDER_FILLED -> new Style("order-placed", "cheer", "#171717", "View order in app", "/blotter");
            case ORDER_REJECTED -> new Style("order-rejected", "sad", "#171717", "See your orders", "/blotter");
            case ORDER_CANCELLED -> new Style("order-cancelled", "think", "#171717", "See your orders", "/blotter");
            case PRICE_ALERT -> new Style("price-alert", "wave", "#171717", "View alert", "/alerts");
            case FUNDS -> new Style("funds", "happy", "#171717", "View balance", "/profile");
        };
    }
}
