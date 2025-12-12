package com.pro.milkteaapp.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Utility class for string and value safety operations
 * Consolidates duplicate helper methods from adapters and activities
 */
public class StringUtils {

    private StringUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Returns empty string if input is null, otherwise returns trimmed string
     */
    @NonNull
    public static String safe(@Nullable String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * Returns empty string if input is null (no trimming)
     */
    @NonNull
    public static String safeStr(@Nullable String s) {
        return s == null ? "" : s;
    }

    /**
     * Returns 0.0 if input is null, otherwise returns the double value
     */
    public static double safeDouble(@Nullable Double d) {
        return d == null ? 0.0 : d;
    }

    /**
     * Returns "0" if input is null, otherwise returns string representation
     */
    @NonNull
    public static String safeDoubleStr(@Nullable Double d) {
        return d == null ? "0" : String.valueOf(d);
    }

    /**
     * Returns 0 if input is null, otherwise returns the int value
     */
    public static int safeInt(@Nullable Integer i) {
        return i == null ? 0 : i;
    }

    /**
     * Returns 0L if input is null, otherwise returns the long value
     */
    public static long safeLong(@Nullable Long l) {
        return l == null ? 0L : l;
    }

    /**
     * Normalizes string: trim and lowercase
     */
    @NonNull
    public static String normalize(@Nullable String s) {
        return s == null ? "" : s.trim().toLowerCase(java.util.Locale.getDefault());
    }
}

