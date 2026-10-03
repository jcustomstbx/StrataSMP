package com.stratasmp.strataeconomy.currency;

import java.text.DecimalFormat;

public final class Amounts {

    private static final DecimalFormat SHORT = new DecimalFormat("#.##");

    /** Largest amount a single command may move (a quadrillion). */
    public static final long MAX_AMOUNT = 1_000_000_000_000_000L;

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
        // BigDecimal rejects Infinity/NaN and keeps "1.001m" exact
        java.math.BigDecimal base = new java.math.BigDecimal(s);
        if (base.signum() < 0) {
            throw new NumberFormatException("negative");
        }
        java.math.BigDecimal out = base.multiply(java.math.BigDecimal.valueOf(mult))
                .setScale(0, java.math.RoundingMode.FLOOR);
        if (out.compareTo(java.math.BigDecimal.valueOf(MAX_AMOUNT)) > 0) {
            throw new NumberFormatException("too large");
        }
        return out.longValueExact();
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
