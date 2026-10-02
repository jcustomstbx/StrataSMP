package com.stratasmp.strataleaderboards;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The boards there are: what they are called, how their title is coloured and how a value is written. */
public enum BoardType {

    // Gradients are light->base->dark shades of the same wool colour as each board's backing block on the wall.
    KILLS("TOP KILLS", "kills", List.of("#FF8A80", "#B02E26", "#6E1B16"), "⚔"),
    STREAK("BEST KILL STREAK", "streak", List.of("#B47CFF", "#FF5FA8", "#FFB347"), "✦"),
    DEATHS("TOP DEATHS", "deaths", List.of("#C299F2", "#7B2FBE", "#4B1B75"), "☠"),
    PLAYTIME("TOP PLAYTIME", "played", List.of("#8FE0F5", "#3AB3DA", "#1B6E8C"), "⏱"),
    STRATAS("TOP STRATAS", "stratas", List.of("#FFF199", "#FED83D", "#C9A012"), "$");

    private final String title;
    private final String unit;
    private final List<String> gradient;
    private final String symbol;

    BoardType(String title, String unit, List<String> gradient, String symbol) {
        this.title = title;
        this.unit = unit;
        this.gradient = gradient;
        this.symbol = symbol;
    }

    public String title() {
        return title;
    }

    public String unit() {
        return unit;
    }

    public List<String> gradient() {
        return gradient;
    }

    public String symbol() {
        return symbol;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<BoardType> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        var wanted = text.toLowerCase(Locale.ROOT);
        for (var type : values()) {
            if (type.id().equals(wanted)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
