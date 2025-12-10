package com.pro.milkteaapp.utils;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * Utility class để chuẩn hóa error handling trong app.
 * Cung cấp các method để log và hiển thị errors một cách nhất quán.
 */
public class ErrorHandler {
    
    private static final String DEFAULT_TAG = "ErrorHandler";
    
    /**
     * Log error và hiển thị Toast message cho user.
     * 
     * @param context Context để hiển thị Toast (có thể null)
     * @param tag Tag để log
     * @param message Message để hiển thị cho user
     * @param error Exception (có thể null)
     */
    public static void handleError(@Nullable Context context, 
                                   @NonNull String tag, 
                                   @NonNull String message, 
                                   @Nullable Throwable error) {
        // Log error
        if (error != null) {
            Log.e(tag, message, error);
        } else {
            Log.e(tag, message);
        }
        
        // Hiển thị Toast nếu có context
        if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Log error và hiển thị Toast message cho user (sử dụng default tag).
     */
    public static void handleError(@Nullable Context context, 
                                   @NonNull String message, 
                                   @Nullable Throwable error) {
        handleError(context, DEFAULT_TAG, message, error);
    }
    
    /**
     * Log error và hiển thị Toast message cho user từ Fragment.
     * Tự động check isAdded() để tránh crash.
     * 
     * @param fragment Fragment để lấy context
     * @param tag Tag để log
     * @param message Message để hiển thị cho user
     * @param error Exception (có thể null)
     */
    public static void handleError(@Nullable Fragment fragment,
                                    @NonNull String tag,
                                    @NonNull String message,
                                    @Nullable Throwable error) {
        // Log error
        if (error != null) {
            Log.e(tag, message, error);
        } else {
            Log.e(tag, message);
        }
        
        // Hiển thị Toast nếu fragment còn attached
        if (fragment != null && fragment.isAdded() && fragment.getContext() != null) {
            Toast.makeText(fragment.getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Log error và hiển thị Toast message cho user từ Fragment (sử dụng default tag).
     */
    public static void handleError(@Nullable Fragment fragment,
                                    @NonNull String message,
                                    @Nullable Throwable error) {
        handleError(fragment, DEFAULT_TAG, message, error);
    }
    
    /**
     * Chỉ log error mà không hiển thị Toast (dùng cho background operations).
     * 
     * @param tag Tag để log
     * @param message Message để log
     * @param error Exception (có thể null)
     */
    public static void logError(@NonNull String tag, @NonNull String message, @Nullable Throwable error) {
        if (error != null) {
            Log.e(tag, message, error);
        } else {
            Log.e(tag, message);
        }
    }
    
    /**
     * Chỉ log error mà không hiển thị Toast (sử dụng default tag).
     */
    public static void logError(@NonNull String message, @Nullable Throwable error) {
        logError(DEFAULT_TAG, message, error);
    }
    
    /**
     * Log warning (không phải error nghiêm trọng).
     * 
     * @param tag Tag để log
     * @param message Message để log
     * @param error Exception (có thể null)
     */
    public static void logWarning(@NonNull String tag, @NonNull String message, @Nullable Throwable error) {
        if (error != null) {
            Log.w(tag, message, error);
        } else {
            Log.w(tag, message);
        }
    }
    
    /**
     * Log warning (sử dụng default tag).
     */
    public static void logWarning(@NonNull String message, @Nullable Throwable error) {
        logWarning(DEFAULT_TAG, message, error);
    }
}

