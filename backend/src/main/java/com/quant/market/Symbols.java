package com.quant.market;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Symbols {

    // US tickers: letters, optionally a class suffix like BRK.B
    private static final Pattern VALID = Pattern.compile("^[A-Z]{1,5}([.][A-Z]{1,2})?$");

    private Symbols() {
    }

    public static String normalize(String raw) {
        String s = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!VALID.matcher(s).matches()) {
            throw new IllegalArgumentException("Invalid symbol: " + raw);
        }
        return s;
    }
}
