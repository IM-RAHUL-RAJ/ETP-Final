package com.capstone.etp.notifications;

import java.util.regex.Pattern;


final class ContentGuard {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern LONG_DIGIT_RUN = Pattern.compile("(?<![\\d.])\\d{13,19}(?![\\d.])");
    private static final Pattern CREDENTIAL_WORDS =
            Pattern.compile("(?i)\\b(password|passwd|secret|api[-_ ]?key|bearer|authorization)\\b");

    private ContentGuard() {
    }

    /** @throws UnsafeContentException when the text looks like it carries something sensitive */
    static void requireSafe(String text) {
        if (text == null) {
            return;
        }
        // A line break in a subject is how a mail header is injected; no message this module builds contains one.
        if (text.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
            throw new UnsafeContentException("a control character");
        }
        if (EMAIL.matcher(text).find()) {
            throw new UnsafeContentException("an e-mail address");
        }
        if (LONG_DIGIT_RUN.matcher(text).find()) {
            throw new UnsafeContentException("a long digit sequence");
        }
        if (CREDENTIAL_WORDS.matcher(text).find()) {
            throw new UnsafeContentException("a credential word");
        }
    }

    /** Names what was found, never the text itself, so the refusal cannot leak what it refused. */
    static final class UnsafeContentException extends RuntimeException {
        UnsafeContentException(String found) {
            super("Message refused: it appears to contain " + found);
        }
    }
}
