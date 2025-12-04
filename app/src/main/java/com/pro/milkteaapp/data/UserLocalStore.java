package com.pro.milkteaapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pro.milkteaapp.SessionManager;

public class UserLocalStore {

    private static final String PREF_NAME = "MyPrefs";

    private final Context context;
    private final SharedPreferences prefs;

    public UserLocalStore(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean("is_logged_in", false);
    }

    public void setLoggedIn(boolean loggedIn) {
        prefs.edit().putBoolean("is_logged_in", loggedIn).apply();
    }

    public void clearLoggedInFlag() {
        setLoggedIn(false);
    }

    /**
     * Lưu thông tin đăng nhập tối thiểu vào SharedPreferences + SessionManager:
     * - user_id = documentId (ưu tiên USRxxxxx; fallback: Firebase UID)
     * - role, fullName (nếu có)
     * - is_logged_in = true
     */
    public void cacheIdentityOnLogin(@NonNull String userIdDoc,
                                     @Nullable String displayName,
                                     @NonNull String role) {
        String safeUserId = safe(userIdDoc);
        String safeDisplayName = safe(displayName);

        prefs.edit()
                .putString("user_id", safeUserId)      // USRxxxxx nếu map thành công
                .putString("fullName", safeDisplayName)
                .putString("role", role)
                .putBoolean("is_logged_in", true)
                .apply();

        // Ghi vào SessionManager nếu có
        try {
            SessionManager sm = new SessionManager(context);
            sm.setUid(safeUserId);
            sm.setRole(role);
            if (!TextUtils.isEmpty(safeDisplayName)) {
                sm.setDisplayName(safeDisplayName);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Lấy UID đã cache trong SessionManager (USRxxxxx hoặc legacy FirebaseUID).
     */
    @Nullable
    public String getCachedUidFromSession() {
        try {
            SessionManager sm = new SessionManager(context);
            String uid = sm.getUid();
            if (TextUtils.isEmpty(uid)) return null;
            return uid;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String safe(@Nullable String s) {
        return s == null ? "" : s;
    }
}
