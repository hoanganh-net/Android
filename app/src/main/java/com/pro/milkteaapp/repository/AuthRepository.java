package com.pro.milkteaapp.repository;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import com.pro.milkteaapp.SessionManager;

import java.util.function.Consumer;

public class AuthRepository {

    private static final String TAG = "AuthRepository";

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;
    private final SessionManager sessionManager;
    private final boolean emailVerifyRequired;

    public interface AutoLoginCallback {
        void onSuccess(String role);
        void onNotAvailable();
        void onError(String message);
    }

    public interface LoginCallback {
        void onSuccess(String role);
        void onError(String message);
    }

    public AuthRepository(@NonNull FirebaseAuth auth,
                          @NonNull FirebaseFirestore db,
                          @NonNull Context context,
                          boolean emailVerifyRequired) {
        this.auth = auth;
        this.db = db;
        this.sessionManager = new SessionManager(context);
        this.emailVerifyRequired = emailVerifyRequired;
    }

    // ====== AUTO LOGIN NẾU CÓ SESSION CŨ ======
    public void autoLoginIfPossible(@NonNull AutoLoginCallback callback) {
        if (!sessionManager.isLoggedIn()) {
            callback.onNotAvailable();
            return;
        }

        FirebaseUser fbUser = auth.getCurrentUser();
        if (fbUser == null) {
            sessionManager.setLoggedIn(false);
            callback.onError("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.");
            return;
        }

        if (emailVerifyRequired && !fbUser.isEmailVerified()) {
            sessionManager.setLoggedIn(false);
            callback.onError("Email của bạn chưa được xác thực.");
            return;
        }

        // Ưu tiên dùng custom userId (USRxxxxx) từ SessionManager nếu đã có
        String cachedCustomId = sessionManager.getUid();
        if (!TextUtils.isEmpty(cachedCustomId)) {
            fetchRoleByCustomId(cachedCustomId, new RoleCallback() {
                @Override
                public void onSuccess(String customId, String role, @Nullable String displayName) {
                    sessionManager.saveUserSession(customId, null, displayName, role);
                    callback.onSuccess(role);
                }

                @Override
                public void onFailure(Exception e) {
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && errorMsg.contains("vô hiệu hóa")) {
                        // User bị vô hiệu hóa, đăng xuất và báo lỗi
                        sessionManager.setLoggedIn(false);
                        callback.onError(errorMsg);
                        return;
                    }
                    Log.w(TAG, "fetchRoleByCustomId failed in autoLogin: " + errorMsg);
                    // Không lấy được role vẫn cho vào user
                    sessionManager.saveUserSession(cachedCustomId, null, null, "user");
                    callback.onSuccess("user");
                }
            });
            return;
        }

        // Chưa có customId → map từ Firebase user → users/{USRxxxxx}
        resolveCustomUserId(
                fbUser.getUid(),
                fbUser.getEmail(),
                customId -> fetchRoleByCustomId(customId, new RoleCallback() {
                    @Override
                    public void onSuccess(String cid, String role, @Nullable String displayName) {
                        sessionManager.saveUserSession(cid, null, displayName, role);
                        callback.onSuccess(role);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Log.w(TAG, "fetchRoleByCustomId failed (autoLogin resolve): " + e.getMessage());
                        // Fallback legacy
                        fetchRoleByLegacyUid(fbUser.getUid(), new RoleCallback() {
                            @Override
                            public void onSuccess(String cid, String role, @Nullable String displayName) {
                                sessionManager.saveUserSession(cid, null, displayName, role);
                                callback.onSuccess(role);
                            }

                            @Override
                            public void onFailure(Exception ex) {
                                sessionManager.saveUserSession(fbUser.getUid(), null, null, "user");
                                callback.onSuccess("user");
                            }
                        });
                    }
                }),
                e -> {
                    // Fallback cực đoan về users/{FirebaseUID} (giữ tương thích nếu DB cũ)
                    Log.w(TAG, "Fallback to legacy users/{FirebaseUID} in autoLogin: " + e.getMessage());
                    fetchRoleByLegacyUid(fbUser.getUid(), new RoleCallback() {
                        @Override
                        public void onSuccess(String cid, String role, @Nullable String displayName) {
                            sessionManager.saveUserSession(cid, null, displayName, role);
                            callback.onSuccess(role);
                        }

                        @Override
                        public void onFailure(Exception ex) {
                            sessionManager.saveUserSession(fbUser.getUid(), null, null, "user");
                            callback.onSuccess("user");
                        }
                    });
                }
        );
    }

    // ====== LOGIN TỪ UI ======
    public void login(@NonNull String email,
                      @NonNull String password,
                      @NonNull LoginCallback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser fbUser = authResult.getUser();
                    if (fbUser == null) {
                        callback.onError("Không tìm thấy UID người dùng.");
                        return;
                    }

                    if (emailVerifyRequired && !fbUser.isEmailVerified()) {
                        // Nếu bắt buộc xác thực email, không set is_logged_in
                        callback.onError("Vui lòng xác thực email trước khi đăng nhập.");
                        return;
                    }

                    // Map Firebase user -> users/{USRxxxxx}
                    resolveCustomUserId(
                            fbUser.getUid(),
                            fbUser.getEmail(),
                            customId -> fetchRoleByCustomId(customId, new RoleCallback() {
                                @Override
                                public void onSuccess(String cid, String role, @Nullable String displayName) {
                                    sessionManager.saveUserSession(cid, fbUser.getEmail(), displayName, role);
                                    callback.onSuccess(role);
                                }

                                @Override
                                public void onFailure(Exception e) {
                                    String errorMsg = e.getMessage();
                                    if (errorMsg != null && errorMsg.contains("vô hiệu hóa")) {
                                        // User bị vô hiệu hóa
                                        callback.onError(errorMsg);
                                        return;
                                    }
                                    Log.w(TAG, "fetchRoleByCustomId failed in login: " + errorMsg);
                                    // Fallback legacy
                                    fetchRoleByLegacyUid(fbUser.getUid(), new RoleCallback() {
                                        @Override
                                        public void onSuccess(String cid, String role, @Nullable String displayName) {
                                            sessionManager.saveUserSession(cid, fbUser.getEmail(), displayName, role);
                                            callback.onSuccess(role);
                                        }

                                        @Override
                                        public void onFailure(Exception ex) {
                                            String exMsg = ex.getMessage();
                                            if (exMsg != null && exMsg.contains("vô hiệu hóa")) {
                                                // User bị vô hiệu hóa
                                                callback.onError(exMsg);
                                                return;
                                            }
                                            // Nếu DB hoàn toàn không có gì, vẫn cho vào user
                                            sessionManager.saveUserSession(fbUser.getUid(), fbUser.getEmail(), null, "user");
                                            callback.onSuccess("user");
                                        }
                                    });
                                }
                            }),
                            e -> fetchRoleByLegacyUid(fbUser.getUid(), new RoleCallback() {
                                @Override
                                public void onSuccess(String cid, String role, @Nullable String displayName) {
                                    sessionManager.saveUserSession(cid, fbUser.getEmail(), displayName, role);
                                    callback.onSuccess(role);
                                }

                                @Override
                                public void onFailure(Exception ex) {
                                    String exMsg = ex.getMessage();
                                    if (exMsg != null && exMsg.contains("vô hiệu hóa")) {
                                        // User bị vô hiệu hóa
                                        callback.onError(exMsg);
                                        return;
                                    }
                                    sessionManager.saveUserSession(fbUser.getUid(), fbUser.getEmail(), null, "user");
                                    callback.onSuccess("user");
                                }
                            })
                    );
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "signIn failed", e);
                    callback.onError("Đăng nhập thất bại: " + e.getMessage());
                });
    }

    // ====== MAP FirebaseUser -> users/{USRxxxxx} ======
    /**
     * Tìm document người dùng theo thứ tự:
     *  1) users where authUid == firebaseUid
     *  2) users where email == email
     *  → Lấy documentId (USRxxxxx) và trả về.
     */
    private void resolveCustomUserId(@Nullable String firebaseUid,
                                     @Nullable String email,
                                     @NonNull Consumer<String> onFound,
                                     @NonNull Consumer<Exception> onNotFound) {
        // Query 1: by authUid
        if (!TextUtils.isEmpty(firebaseUid)) {
            db.collection("users")
                    .whereEqualTo("authUid", firebaseUid)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(q1 -> {
                        if (!q1.isEmpty()) {
                            DocumentSnapshot d = q1.getDocuments().get(0);
                            String customId = d.getId();
                            // Cache minimal lần đầu nếu muốn, nhưng vẫn để fetchRoleByCustomId chuẩn hoá role
                            onFound.accept(customId);
                        } else {
                            // Query 2: by email
                            queryByEmailFallback(email, onFound, onNotFound);
                        }
                    })
                    .addOnFailureListener(e -> queryByEmailFallback(email, onFound, onNotFound));
        } else {
            queryByEmailFallback(email, onFound, onNotFound);
        }
    }

    private void queryByEmailFallback(@Nullable String email,
                                      @NonNull Consumer<String> onFound,
                                      @NonNull Consumer<Exception> onNotFound) {
        if (TextUtils.isEmpty(email)) {
            onNotFound.accept(new IllegalStateException("Missing email for mapping"));
            return;
        }
        db.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .addOnSuccessListener(q2 -> {
                    if (!q2.isEmpty()) {
                        DocumentSnapshot d = q2.getDocuments().get(0);
                        String customId = d.getId();
                        onFound.accept(customId);
                    } else {
                        onNotFound.accept(new IllegalStateException("No users by email"));
                    }
                })
                .addOnFailureListener(onNotFound::accept);
    }

    // ====== LẤY ROLE THEO USRxxxxx ======
    private interface RoleCallback {
        void onSuccess(String customId, String role, @Nullable String displayName);
        void onFailure(Exception e);
    }

    private void fetchRoleByCustomId(@NonNull String customId,
                                     @NonNull RoleCallback callback) {
        db.collection("users").document(customId).get()
                .addOnSuccessListener(snap -> {
                    if (snap == null || !snap.exists()) {
                        callback.onFailure(new Exception("User not found"));
                        return;
                    }
                    
                    // Kiểm tra active status
                    Boolean active = snap.getBoolean("active");
                    if (active != null && !active) {
                        // User bị vô hiệu hóa
                        callback.onFailure(new Exception("Tài khoản đã bị vô hiệu hóa"));
                        return;
                    }
                    
                    String role = snap.getString("role") != null ? snap.getString("role") : "user";
                    String displayName = snap.getString("fullName");
                    callback.onSuccess(customId, normalizeRole(role), displayName);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "fetchRoleByCustomId failed: " + e.getMessage());
                    callback.onFailure(e);
                });
    }

    // ====== FALLBACK LEGACY: users/{FirebaseUID} ======
    private void fetchRoleByLegacyUid(@NonNull String legacyUid,
                                      @NonNull RoleCallback callback) {
        db.collection("users").document(legacyUid).get()
                .addOnSuccessListener(snap -> {
                    if (snap == null || !snap.exists()) {
                        callback.onFailure(new Exception("User not found"));
                        return;
                    }
                    
                    // Kiểm tra active status
                    Boolean active = snap.getBoolean("active");
                    if (active != null && !active) {
                        // User bị vô hiệu hóa
                        callback.onFailure(new Exception("Tài khoản đã bị vô hiệu hóa"));
                        return;
                    }
                    
                    String role = snap.getString("role") != null ? snap.getString("role") : "user";
                    String displayName = snap.getString("fullName");
                    callback.onSuccess(legacyUid, normalizeRole(role), displayName);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "fetchRoleByLegacyUid failed: " + e.getMessage());
                    callback.onFailure(e);
                });
    }

    // ====== TIỆN ÍCH / CHUNG ======
    public String normalizeRole(@Nullable String role) {
        if (role == null) return "user";
        String r = role.trim().toLowerCase();
        return ("admin".equals(r)) ? "admin" : "user";
    }
}
