package de.galaxushd.mpsqcamera;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Text-editor model: raw ampersand text stays copyable while preview is rendered. */
public final class TeamTextPreview {
    private String raw;
    public TeamTextPreview(String raw) { this.raw = raw == null ? "" : raw; }
    public String raw() { return raw; }
    public void setRaw(String value) { raw = value == null ? "" : value; }
    public MutableText rendered() { return TeamChatText.fromAmpersandCodes(raw, Formatting.WHITE); }
    public Text copyValue() { return Text.literal(raw); }
}
