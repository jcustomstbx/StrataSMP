package com.stratasmp.stratakits;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Pattern;

final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");

    private Text() {
    }

    /** '&' codes plus &#RRGGBB, via the section-sign form the serializer understands. */
    static Component color(String raw) {
        String expanded = HEX.matcher(raw).replaceAll(m -> "§x" + m.group(1).replaceAll("(.)", "§$1"));
        return LEGACY.deserialize(expanded.replace('&', '§'));
    }

    /** For item names and lore - the client italicises these unless italics are switched off explicitly. */
    static Component item(String raw) {
        return color(raw).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    static String duration(long millis) {
        long minutes = Math.max(1, millis / 60_000);
        long days = minutes / (60 * 24);
        long hours = minutes / 60 % 24;
        long mins = minutes % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            sb.append(hours).append("h ");
        }
        sb.append(mins).append("m");
        return sb.toString();
    }
}