package de.galaxushd.mpsqcamera;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Text-editor model: raw ampersand text stays copyable while preview is rendered. */
public final class TeamTextPreview {
    private String raw;
    public TeamTextPreview(String raw) { this.raw = raw == null ? "" : raw; }
    public String raw() { return raw; }
    public void setRaw(String value) { raw = value == null ? "" : value; }
    public MutableComponent rendered() { return TeamChatText.fromAmpersandCodes(raw, ChatFormatting.WHITE); }
    public Component copyValue() { return Component.literal(raw); }
}
