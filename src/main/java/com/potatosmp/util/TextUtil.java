package com.potatosmp.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class TextUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final DecimalFormat INT_FORMAT = new DecimalFormat("#,##0");
    private static final DecimalFormat RATE_FORMAT = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd HH:mm").withZone(ZoneId.systemDefault());

    private TextUtil() {}

    public static Component mm(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return MM.deserialize(input).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static Component mm(String template, Map<String, String> placeholders) {
        String resolved = template == null ? "" : template;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return mm(resolved);
    }

    public static List<Component> mmList(List<String> lines) {
        return lines.stream().map(TextUtil::mm).collect(Collectors.toList());
    }

    public static String formatCredits(long credits) {
        return INT_FORMAT.format(credits);
    }

    public static String formatRate(double rate) {
        return RATE_FORMAT.format(rate);
    }

    public static String formatTimestamp(long epochMillis) {
        return TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis));
    }

    public static String formatDurationSeconds(long totalSeconds) {
        if (totalSeconds <= 0) {
            return "Expired";
        }
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(1, minutes) + "m";
    }
}
