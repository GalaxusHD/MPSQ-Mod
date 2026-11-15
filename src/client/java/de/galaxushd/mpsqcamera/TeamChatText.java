package de.galaxushd.mpsqcamera;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Turns familiar ampersand Minecraft codes into safe client chat text. */
public final class TeamChatText {
    private static int rgb(ChatFormatting format) { var color=net.minecraft.network.chat.TextColor.fromLegacyFormat(format); return color==null?0xFFFFFF:color.getValue(); }
    private TeamChatText() {
    }

    public static MutableComponent fromAmpersandCodes(String raw, ChatFormatting initialColor) {
        MutableComponent result = Component.empty();
        String text = raw == null ? "" : raw;
        int color = rgb(initialColor);
        EnumSet<ChatFormatting> styles = EnumSet.noneOf(ChatFormatting.class);
        StringBuilder part = new StringBuilder();

        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '&' && index + 1 < text.length()) {
                char rawCode = Character.toLowerCase(text.charAt(index + 1));
                Integer customColor = customColor(rawCode);
                ChatFormatting code = formatting(rawCode);
                if (code != null || customColor != null) {
                    append(result, part, color, styles);
                    part.setLength(0);
                    if (customColor != null) {
                        color = customColor;
                        styles.clear();
                    } else if (code == ChatFormatting.RESET) {
                        color = rgb(initialColor);
                        styles.clear();
                    } else if (net.minecraft.network.chat.TextColor.fromLegacyFormat(code) != null) {
                        color = rgb(code);
                        styles.clear();
                    } else {
                        styles.add(code);
                    }
                    index++;
                    continue;
                }
            }
            part.append(current);
        }
        append(result, part, color, styles);
        return result;
    }

    private static void append(MutableComponent target, StringBuilder part, int color, EnumSet<ChatFormatting> styles) {
        if (part.isEmpty()) return;
        target.append(Component.literal(part.toString())
                .withStyle(styles.toArray(ChatFormatting[]::new))
                .withStyle(style -> style.withColor(color)));
    }

    /** Extra MixelPixel chat colors that deliberately do not collide with vanilla codes. */
    private static Integer customColor(char code) {
        return switch (code) {
            case 'g' -> 0x1597D4; // kräftiges Himmelblau
            case 'h' -> 0x55D51C; // Limettengrün
            case 'i' -> 0x6515E8; // Violett
            case 'j' -> 0xE6D19A; // helles Beige/Gold
            case 'p' -> 0x793434; // dunkles Weinrot
            case 'q' -> 0xFF6A00; // Orange
            default -> null;
        };
    }

    private static ChatFormatting formatting(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> ChatFormatting.BLACK;
            case '1' -> ChatFormatting.DARK_BLUE;
            case '2' -> ChatFormatting.DARK_GREEN;
            case '3' -> ChatFormatting.DARK_AQUA;
            case '4' -> ChatFormatting.DARK_RED;
            case '5' -> ChatFormatting.DARK_PURPLE;
            case '6' -> ChatFormatting.GOLD;
            case '7' -> ChatFormatting.GRAY;
            case '8' -> ChatFormatting.DARK_GRAY;
            case '9' -> ChatFormatting.BLUE;
            case 'a' -> ChatFormatting.GREEN;
            case 'b' -> ChatFormatting.AQUA;
            case 'c' -> ChatFormatting.RED;
            case 'd' -> ChatFormatting.LIGHT_PURPLE;
            case 'e' -> ChatFormatting.YELLOW;
            case 'f' -> ChatFormatting.WHITE;
            case 'k' -> ChatFormatting.OBFUSCATED;
            case 'l' -> ChatFormatting.BOLD;
            case 'm' -> ChatFormatting.STRIKETHROUGH;
            case 'n' -> ChatFormatting.UNDERLINE;
            case 'o' -> ChatFormatting.ITALIC;
            case 'r' -> ChatFormatting.RESET;
            default -> null;
        };
    }
}
