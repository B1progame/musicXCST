package de.coulees.B1progame.musicxcst.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

public final class ChatFeedback {
    private ChatFeedback() {
    }

    public static MutableComponent info(String message) {
        return labeled("INFO", ChatFormatting.AQUA, message, ChatFormatting.WHITE);
    }

    public static MutableComponent success(String message) {
        return labeled("OK", ChatFormatting.GREEN, message, ChatFormatting.GREEN);
    }

    public static MutableComponent warning(String message) {
        return labeled("WARN", ChatFormatting.GOLD, message, ChatFormatting.YELLOW);
    }

    public static MutableComponent error(String message) {
        return labeled("ERROR", ChatFormatting.DARK_RED, message, ChatFormatting.RED);
    }

    public static MutableComponent progress(String message) {
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        ChatFormatting color = lower.contains("failed") || lower.contains("error")
                ? ChatFormatting.RED
                : lower.contains("completed") || lower.contains("finished") || lower.contains("ready")
                ? ChatFormatting.GREEN
                : ChatFormatting.GOLD;
        return labeled("STATUS", color, message, color == ChatFormatting.GOLD ? ChatFormatting.YELLOW : color);
    }

    public static MutableComponent status(String message) {
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (lower.contains("failed") || lower.contains("error") || lower.contains("missing")
                || lower.contains("unavailable") || lower.contains("cannot") || lower.contains("invalid")
                || lower.contains("interrupted") || lower.contains("cancelled")) {
            return error(message);
        }
        if (lower.contains("warning") || lower.contains("limit") || lower.contains("not found")
                || lower.contains("disabled") || lower.contains("already")) {
            return warning(message);
        }
        if (lower.contains("created") || lower.contains("saved") || lower.contains("installed")
                || lower.contains("finished") || lower.contains("ready") || lower.contains("deleted")
                || lower.contains("reloaded") || lower.contains("repaired") || lower.contains("verified")) {
            return success(message);
        }
        return info(message);
    }

    public static MutableComponent command(String command) {
        return Component.literal(command).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withBold(true)
                .withUnderlined(true));
    }

    public static MutableComponent detail(String text) {
        return Component.literal(text == null ? "" : text).withStyle(ChatFormatting.GRAY);
    }

    public static MutableComponent fieldLabel(String text) {
        return Component.literal(text == null ? "" : text).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD);
    }

    public static MutableComponent fieldValue(String text, ChatFormatting color) {
        return Component.literal(text == null ? "" : text).withStyle(color);
    }

    public static MutableComponent actionBarProgress(String stage, double percent, String eta) {
        ChatFormatting percentColor = percent >= 100.0D
                ? ChatFormatting.GREEN
                : percent >= 75.0D
                ? ChatFormatting.AQUA
                : percent >= 40.0D
                ? ChatFormatting.YELLOW
                : ChatFormatting.GOLD;
        return Component.literal("")
                .append(Component.literal(stage).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD))
                .append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format(Locale.ROOT, "%.1f", percent) + "%").withStyle(percentColor, ChatFormatting.BOLD))
                .append(Component.literal(" | ETA ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(eta == null ? "0s" : eta).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static MutableComponent labeled(String label, ChatFormatting labelColor, String message, ChatFormatting messageColor) {
        return Component.literal("[")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(label).withStyle(labelColor, ChatFormatting.BOLD))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(message == null ? "" : message).withStyle(messageColor));
    }
}
