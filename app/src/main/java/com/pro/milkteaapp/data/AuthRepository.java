package com.pro.milkteaapp.data;

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
    private final UserLocalStore localStore;
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
                          @NonNull UserLocalStore localStore,
                          boolean emailVerifyRequired) {
        this.auth = auth;
        this.db = db;
        this.localStore = localStore;
        this.emailVerifyRequired = emailVerifyRequired;
    }

    // ====== AUTO LOGIN NẾU CÓ SESSION CŨ ======
    public void autoLoginIfPossible(@NonNull AutoLoginCallback callback) {
        if (!localStore.isLoggedIn()) {
            callback.onNotAvailable();
            return;
        }

        FirebaseUser fbUser = auth.getCurrentUser();
        if (fbUser == null) {
            localStore.clearLoggedInFlag();
            callback.onError("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.");
            return;
        }

        if (emailVerifyRequired && !fbUser.isEmailVerified()) {
            localStore.clearLoggedInFlag();
            callback.onError("Email của bạn chưa được xác thực.");
            return;
        }

        // Ưu tiên dùng custom userId (USRxxxxx) từ SessionManager nếu đã có
        String cachedCustomId = localStore.getCachedUidFromSession();
        if (!TextUtils.isEmpty(cachedCustomId)) {
            fetchRoleByCustomId(cachedCustomId, new RoleCallback() {
                @Override
                public void onSuccess(String customId, String role, @Nullable String displayName) {
                    localStore.cacheIdentityOnLogin(customId, displayName, role);
                    callback.onSuccess(role);
                }

                @Override
                public void onFailure(Exception e) {
                    Log.w(TAG, "fetchRoleByCustomId failed in autoLogin: " + e.getMessage());
                    // Không lấy được role vẫn cho vào user
                    localStore.cacheIdentityOnLogin(cachedCustomId, null, "user");
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
                        localStore.cacheIdentityOnLogin(cid, displayName, role);
                        callback.onSuccess(role);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Log.w(TAG, "fetchRoleByCustomId failed (autoLogin resolve): " + e.getMessage());
                        // Fallback legacy
                        fetchRoleByLegacyUid(fbUser.getUid(), new RoleCallback() {
                            @Override
                            public void onSuccess(String cid, String role, @Nullable String displayName) {
                                localStore.cacheIdentityOnLogin(cid, displayName, role);
                                callback.onSuccess(role);
                            }

                            @Override
                            public void onFailure(Exception ex) {
                                localStore.cacheIdentityOnLogin(fbUser.getUid(), null, "user");
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
                            localStore.cacheIdentityOnLogin(cid, displayName, role);
                            callback.onSuccess(role);
                        }

                        @Override
                        public void onFailure(Exception ex) {
                            localStore.cacheIdentityOnLogin(fbUser.getUid(), null, "user");
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
                                    localStore.cacheIdentityOnLogin(cid, displayName, role);
                                    callback.onSuccess(role);
                                }

                                @Override
                                public void onFailure(Exception e) {
                                    Log.w(TAG, "fetchRoleByCustomId failed in login: " + e.getMessage());
                                    // Fallback legacy
                                    fetchRoleByLegacyUid(fbUser.getUid(), new RoleCallback() {
                                        @Override
                                        public void onSuccess(String cid, String role, @Nullable String displayName) {
                                            localStore.cacheIdentityOnLogin(cid, displayName, role);
                                            callback.onSuccess(role);
                                        }

                                        @Override
                                        public void onFailure(Exception ex) {
                                            // Nếu DB hoàn toàn không có gì, vẫn cho vào user
                                            localStore.cacheIdentityOnLogin(fbUser.getUid(), null, "user");
                                            callback.onSuccess("user");
                                        }
                                    });
                                }
                            }),
                            e -> fetchRoleByLegacyUid(fbUser.getUid(), new RoleCallback() {
                                @Override
                                public void onSuccess(String cid, String role, @Nullable String displayName) {
                                    localStore.cacheIdentityOnLogin(cid, displayName, role);
                                    callback.onSuccess(role);
                                }

                                @Override
                                public void onFailure(Exception ex) {
                                    localStore.cacheIdentityOnLogin(fbUser.getUid(), null, "user");
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
                    String role = (snap != null && snap.exists() && snap.getString("role") != null)
                            ? snap.getString("role") : "user";
                    String displayName = snap != null ? snap.getString("fullName") : null;
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
                    String role = (snap != null && snap.exists() && snap.getString("role") != null)
                            ? snap.getString("role") : "user";
                    String displayName = snap != null ? snap.getString("fullName") : null;
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
