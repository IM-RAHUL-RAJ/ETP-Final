package com.capstone.etp.notifications;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailTemplateRendererTest {

    private final EmailTemplateRenderer renderer = new EmailTemplateRenderer("https://app.example/");
    private final Instant at = Instant.parse("2026-10-07T14:05:11Z");

    @Test
    void everyKindRendersWithItsOwnHeadlineMascotAndButton() {
        for (NotificationKind kind : NotificationKind.values()) {
            EmailTemplateRenderer.Rendered rendered = renderer.render(kind, "RABBIT", "Subject", "Body text.", at);

            assertTrue(rendered.html().contains("Body text."), kind + " carries the message");
            assertTrue(rendered.mascotResource().contains("rabbit-"), kind + " carries the chosen mascot");
            assertTrue(rendered.html().contains("src=\"cid:mascot\""), kind + " shows it from the message itself");
            assertFalse(rendered.html().contains("{{"), kind + " has no unfilled placeholder");
            assertTrue(rendered.text().contains("https://app.example/"), kind + " text version has the link");
        }
    }

    @Test
    void thePoseFitsTheMessage() {
        assertEquals("email/mascots/owl-cheer.gif", renderer.render(NotificationKind.ORDER_FILLED, "OWL", "s", "b", at).mascotResource());
        assertEquals("email/mascots/owl-sad.gif", renderer.render(NotificationKind.ORDER_REJECTED, "OWL", "s", "b", at).mascotResource());
        assertEquals("email/mascots/cat-wave.gif", renderer.render(NotificationKind.PRICE_ALERT, "CAT", "s", "b", at).mascotResource());
        assertEquals("email/mascots/owl-dance.gif", renderer.renderWelcome("OWL", "s", "b", at).mascotResource());
    }

    @Test
    void customerTextIsEscapedSoItCannotInjectMarkup() {
        EmailTemplateRenderer.Rendered rendered = renderer.render(NotificationKind.PRICE_ALERT, "OWL",
                "<script>alert(1)</script>", "x \" onerror=\"y <b>bold</b>", at);

        assertFalse(rendered.html().contains("<script>alert"));
        assertFalse(rendered.html().contains("<b>bold</b>"));
        assertTrue(rendered.html().contains("&lt;b&gt;bold&lt;/b&gt;"));
    }

    @Test
    void anUnknownMascotFallsBackToTheOwl() {
        assertEquals("owl", EmailTemplateRenderer.normaliseMascot("DRAGON"));
        assertEquals("owl", EmailTemplateRenderer.normaliseMascot(null));
        assertEquals("cat", EmailTemplateRenderer.normaliseMascot("CAT"));
    }

    @Test
    void theTemplateSupportsDarkModeAndSmallScreens() {
        String html = renderer.render(NotificationKind.FUNDS, "OWL", "s", "b", at).html();

        assertTrue(html.contains("prefers-color-scheme: dark"));
        assertTrue(html.contains("max-width: 600px"));
        assertTrue(html.contains("name=\"viewport\""));
    }

    @Test
    void anAutomatedTradeCarriesABannerInTheHtmlAndInTheTextVersion() {
        EmailTemplateRenderer.Rendered rendered = renderer.render(NotificationKind.ORDER_FILLED, "OWL",
                "Automated order filled: BUY 1 AAPL", "This trade was placed automatically by your strategy #5 on AAPL. Your BUY order was filled.", at);

        assertTrue(rendered.html().contains("AUTOMATED TRADE TRIGGERED"));
        assertTrue(rendered.html().contains("You did not place this order."));
        assertTrue(rendered.text().startsWith("*** AUTOMATED TRADE TRIGGERED ***"));
        assertFalse(rendered.html().contains("{{"), "no unfilled placeholder");
    }

    @Test
    void anOrderYouPlacedYourselfHasNoBanner() {
        EmailTemplateRenderer.Rendered rendered = renderer.render(NotificationKind.ORDER_FILLED, "OWL", "Order filled: BUY 1 AAPL", "Filled.", at);

        assertFalse(rendered.html().contains("AUTOMATED TRADE TRIGGERED"));
        assertFalse(rendered.text().contains("AUTOMATED"));
        assertFalse(rendered.html().contains("{{"), "no unfilled placeholder");
    }

    @Test
    void theBannerComesFromTheSubjectMarkerOnlyAndCannotBeFakedByTheMessageBody() {
        EmailTemplateRenderer.Rendered rendered = renderer.render(NotificationKind.PRICE_ALERT, "OWL", "Price alert: AAPL", "Automated order filled", at);

        assertFalse(rendered.html().contains("AUTOMATED TRADE TRIGGERED"));
    }
}
