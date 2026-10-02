package com.stratasmp.strataeconomy.currency;

import java.text.DecimalFormat;

public final class Amounts {

    private static final DecimalFormat SHORT = new DecimalFormat("#.##");

    private Amounts() {
    }

    /** "1", "2.5k", "10K", "3m", "1,000" -> long. Throws NumberFormatException on junk or negatives. */
    public static long parse(String input) {
        String s = input.toLowerCase().replace(",", "").trim();
        long mult = 1L;
        if (!s.isEmpty()) {
            char last = s.charAt(s.length() - 1);
            switch (last) {
                case 'k' -> mult = 1_000L;
                case 'm' -> mult = 1_000_000L;
                case 'b' -> mult = 1_000_000_000L;
                case 't' -> mult = 1_000_000_000_000L;
                default -> mult = 1L;
            }
            if (mult != 1L) {
                s = s.substring(0, s.length() - 1);
            }
        }
        double base = Double.parseDouble(s);
        if (base < 0) {
            throw new NumberFormatException("negative");
        }
        long out = (long) Math.floor(base * mult);
        if (out < 0) {
            throw new NumberFormatException("overflow");
        }
        return out;
    }

    public static String format(long amount, String symbol, String plural) {
        return symbol + " " + String.format("%,d", amount) + " " + plural;
    }

    public static String formatShort(long amount) {
        if (amount >= 1_000_000_000_000L) {
            return SHORT.format(amount / 1e12) + "T";
        }
        if (amount >= 1_000_000_000L) {
            return SHORT.format(amount / 1e9) + "B";
        }
        if (amount >= 1_000_000L) {
            return SHORT.format(amount / 1e6) + "M";
        }
        return String.format("%,d", amount);
    }
}
