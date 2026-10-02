package com.stratasmp.strataleaderboards;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.List;
import java.util.Locale;

/** Turns the rows of a board into the text shown floating in the world. */
public final class BoardRenderer {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor SILVER = TextColor.color(0xC8CCD4);
    private static final TextColor BRONZE = TextColor.color(0xE08A3C);

    private BoardRenderer() {
    }

    /** font may be null for the normal Minecraft font. */
    public static Component render(BoardType type, List<Row> rows, Key font) {
        var lines = Component.text();
        lines.append(gradient(type.title(), type.gradient())).append(Component.newline());
        lines.append(Component.text("The best on StrataSMP", NamedTextColor.GRAY)).append(Component.newline());
        if (rows.isEmpty()) {
            lines.append(Component.newline()).append(Component.text("Nobody yet. Be the first!", NamedTextColor.DARK_GRAY));
        }
        for (int i = 0; i < rows.size(); i++) {
            lines.append(Component.newline()).append(row(type, i + 1, rows.get(i)));
        }
        var built = lines.build().decoration(TextDecoration.ITALIC, false);
        return font == null ? built : built.font(font);
    }

    static Component row(BoardType type, int place, Row row) {
        var name = row.name() == null || row.name().isBlank() ? "Unknown" : row.name();
        return Component.text()
            .append(Component.text(place + ". ", placeColour(place)).decoration(TextDecoration.BOLD, place <= 3))
            .append(Component.text(name, place <= 3 ? placeColour(place) : NamedTextColor.WHITE))
            .append(Component.text("  " + type.symbol() + " " + formatValue(type, row.value()), NamedTextColor.GRAY))
            .build();
    }

    /** Playtime is stored in seconds; every other board is a plain count with its unit appended. */
    private static String formatValue(BoardType type, long value) {
        if (type != BoardType.PLAYTIME) {
            return String.format(Locale.ROOT, "%,d", value) + " " + type.unit();
        }
        var hours = value / 3600;
        var minutes = (value % 3600) / 60;
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }

    static TextColor placeColour(int place) {
        return switch (place) {
            case 1 -> GOLD;
            case 2 -> SILVER;
            case 3 -> BRONZE;
            default -> NamedTextColor.DARK_GRAY;
        };
    }

    private static Component gradient(String text, List<String> hexStops) {
        var stops = hexStops.stream().map(TextColor::fromHexString).toList();
        var characters = text.toCharArray();
        var result = Component.text();
        for (int i = 0; i < characters.length; i++) {
            var position = characters.length == 1 ? 0.0 : (double) i / (characters.length - 1);
            var scaled = position * (stops.size() - 1);
            var index = Math.min((int) Math.floor(scaled), stops.size() - 2);
            result.append(Component.text(characters[i], TextColor.lerp((float) (scaled - index), stops.get(index), stops.get(index + 1))));
        }
        return result.build().decoration(TextDecoration.BOLD, true);
    }
}
