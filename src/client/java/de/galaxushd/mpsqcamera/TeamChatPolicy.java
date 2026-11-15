package de.galaxushd.mpsqcamera;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Shared, client-side safety rules for the private MPSQ Team chat. */
public final class TeamChatPolicy {
    public static final int MAX_LENGTH = 256;

    /* The server enforces the same list. This client-side check gives the
       sender immediate feedback instead of first uploading an invalid text. */
    private static final Set<String> FORBIDDEN_FRAGMENTS = Set.of(
            "arschloch", "bastard", "behindert", "fick", "fotze", "hurensohn",
            "missgeburt", "neger", "nigger", "scheisse", "schwuchtel", "spast", "wichser"
    );

    private TeamChatPolicy() {
    }

    public static String prepare(String message) {
        if (message == null) return "";
        String prepared = message.trim().replace('\u00A7', '&');
        return prepared.substring(0, Math.min(prepared.length(), MAX_LENGTH));
    }

    public static boolean containsForbiddenContent(String message) {
        String normalized = normalizeForFilter(message);
        return FORBIDDEN_FRAGMENTS.stream().anyMatch(normalized::contains);
    }

    private static String normalizeForFilter(String message) {
        String withoutFormatting = prepare(message).replaceAll("(?i)&[0-9a-fk-or]", "");
        String normalized = Normalizer.normalize(withoutFormatting, Normalizer.Form.NFKD)
                .replace("\u00DF", "ss")
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return normalized
                .replace('0', 'o').replace('1', 'i').replace('3', 'e')
                .replace('4', 'a').replace('5', 's').replace('7', 't')
                .replace('@', 'a').replace('$', 's')
                .replaceAll("[^a-z0-9]", "");
    }
}
