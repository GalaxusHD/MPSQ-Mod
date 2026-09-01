package de.galaxushd.mpsqcamera;

import java.util.UUID;

/** A reusable script line and the side that speaks it. */
public record TeamTemplate(UUID id, String text, Speaker speaker) {
    public enum Speaker {
        OFFICER("offizier"), FRONTMAN("frontman");

        private final String id;
        Speaker(String id) { this.id = id; }
        public String id() { return id; }
        public static Speaker fromId(String value) {
            return "frontman".equalsIgnoreCase(value) ? FRONTMAN : OFFICER;
        }
    }
}
