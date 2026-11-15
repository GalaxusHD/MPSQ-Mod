package de.galaxushd.mpsqcamera;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Zentrale Theme-Konstanten und Hilfsrenderer für alle Mod-Screens.
 *
 * Farbpalette: Rot / Weinrot / Dunkelgrau / Schwarz
 */
public final class MpsqTheme {

    private MpsqTheme() {}

    // ── Farb-Palette ─────────────────────────────────────────────────────────

    /** Primärrot – Akzentfarbe, Rahmen oben */
    public static final int ROT          = 0xFFB30000;
    /** Dunkelrot – äußerer Rahmen */
    public static final int DUNKELROT    = 0xFF8B0000;
    /** Weinrot – Sekundär-Akzent, Rahmen unten / Panel-Rand */
    public static final int WEINROT      = 0xFF7A0020;
    /** Dunkelgrau – Hintergrund oben */
    public static final int DUNKELGRAU   = 0xFF1A1A1A;
    /** Fast Schwarz – Hintergrund unten */
    public static final int SCHWARZ      = 0xFF050505;
    /** Halbtransparentes dunkles Grau für Panels */
    public static final int PANEL_BG     = 0x99101010;
    /** Panel-Rahmen (Weinrot) */
    public static final int RAHMEN       = 0xFF7A0020;
    /** Titel-Text – helles Rot */
    public static final int TEXT_TITEL   = 0xFFFF4444;
    /** Normaler Text – fast Weiß */
    public static final int TEXT_NORMAL  = 0xFFEEEEEE;
    /** Gedämpfter Text – Grau */
    public static final int TEXT_GEDAEMPT = 0xFF888888;

    // ── Hilfsmethoden ────────────────────────────────────────────────────────

    /**
     * Zeichnet den benutzerdefinierten Hintergrund.
     * Muss nach {@code super.extractBackground(...)} aufgerufen werden.
     * Transparenter, neutraler dunkler Hintergrund – kein Rot-Ton mehr.
     */
    public static void drawBackground(GuiGraphicsExtractor context, int width, int height) {
        // Transparenter dunkelgrauer Hintergrund
        context.fillGradient(0, 0, width, height, 0x991A1A1A, 0x99050505);
    }

    /**
     * Zeichnet ein halbtransparentes Panel mit weinrotem Rahmen.
     */
    public static void drawPanel(GuiGraphicsExtractor context, int x, int y, int w, int h) {
        context.fill(x, y, x + w, y + h, PANEL_BG);
    }
}
