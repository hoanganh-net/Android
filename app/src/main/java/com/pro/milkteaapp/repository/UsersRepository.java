package com.pro.milkteaapp.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Transaction;
import com.pro.milkteaapp.constants.FirestoreConstants;
import com.pro.milkteaapp.models.User;
import com.pro.milkteaapp.utils.LoyaltyPolicy;

import java.util.HashMap;
import java.util.Map;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository để quản lý các thao tác với Users trong Firestore.
 */
public class UsersRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Lấy một user theo ID (docId dạng USRxxxxx)
     */
    public void fetchUserById(@NonNull String userId, @NonNull OnUserListener listener) {
        db.collection(FirestoreConstants.COLLECTION_USERS)
                .document(userId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document != null && document.exists()) {
                        User user = parseUser(document);
                        if (user != null) {
                            listener.onUserReceived(user);
                        } else {
                            listener.onError(new Exception("Failed to parse user"));
                        }
                    } else {
                        listener.onError(new Exception("User not found"));
                    }
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lấy user theo Firebase Auth UID
     */
    public void fetchUserByAuthUid(@NonNull String authUid, @NonNull OnUserListener listener) {
        db.collection(FirestoreConstants.COLLECTION_USERS)
                .whereEqualTo(FirestoreConstants.FIELD_AUTH_UID, authUid)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null && !snapshot.isEmpty()) {
                        User user = parseUser(snapshot.getDocuments().get(0));
                        if (user != null) {
                            listener.onUserReceived(user);
                        } else {
                            listener.onError(new Exception("Failed to parse user"));
                        }
                    } else {
                        listener.onError(new Exception("User not found"));
                    }
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lấy user theo email
     */
    public void fetchUserByEmail(@NonNull String email, @NonNull OnUserListener listener) {
        db.collection(FirestoreConstants.COLLECTION_USERS)
                .whereEqualTo(FirestoreConstants.FIELD_EMAIL, email)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null && !snapshot.isEmpty()) {
                        User user = parseUser(snapshot.getDocuments().get(0));
                        if (user != null) {
                            listener.onUserReceived(user);
                        } else {
                            listener.onError(new Exception("Failed to parse user"));
                        }
                    } else {
                        listener.onError(new Exception("User not found"));
                    }
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lấy tất cả users (dành cho admin)
     */
    public void fetchAllUsers(@NonNull OnUsersListener listener) {
        db.collection(FirestoreConstants.COLLECTION_USERS)
                .orderBy(FirestoreConstants.FIELD_FULL_NAME, Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<User> users = parseUsers(snapshot);
                    listener.onUsersReceived(users);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lắng nghe realtime tất cả users (dành cho admin)
     */
    public ListenerRegistration listenAllUsers(@NonNull OnUsersListener listener) {
        Query query = db.collection(FirestoreConstants.COLLECTION_USERS)
                .orderBy(FirestoreConstants.FIELD_FULL_NAME, Query.Direction.ASCENDING);

        return query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            List<User> users = parseUsers(snapshot);
            listener.onUsersReceived(users);
        });
    }

    /**
     * Lắng nghe realtime một user theo ID
     */
    public ListenerRegistration listenUserById(@NonNull String userId, @NonNull OnUserListener listener) {
        return db.collection(FirestoreConstants.COLLECTION_USERS)
                .document(userId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(error);
                        return;
                    }
                    if (snapshot != null && snapshot.exists()) {
                        User user = parseUser(snapshot);
                        if (user != null) {
                            listener.onUserReceived(user);
                        } else {
                            listener.onError(new Exception("Failed to parse user"));
                        }
                    } else {
                        listener.onError(new Exception("User not found"));
                    }
                });
    }

    /**
     * Lấy users theo role
     */
    public void fetchUsersByRole(@NonNull String role, @NonNull OnUsersListener listener) {
        db.collection(FirestoreConstants.COLLECTION_USERS)
                .whereEqualTo(FirestoreConstants.FIELD_ROLE, role)
                .orderBy(FirestoreConstants.FIELD_FULL_NAME, Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<User> users = parseUsers(snapshot);
                    listener.onUsersReceived(users);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Parse QuerySnapshot thành List<User>
     */
    @NonNull
    private List<User> parseUsers(@Nullable QuerySnapshot snapshot) {
        List<User> users = new ArrayList<>();
        if (snapshot != null && !snapshot.isEmpty()) {
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                User user = parseUser(doc);
                if (user != null) {
                    users.add(user);
                }
            }
        }
        return users;
    }

    /**
     * Cập nhật thông tin user
     */
    public void updateUser(@NonNull String userId, @NonNull Map<String, Object> updates, @NonNull OnUserUpdateListener listener) {
        db.collection(FirestoreConstants.COLLECTION_USERS)
                .document(userId)
                .update(updates)
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    /**
     * Xóa user
     */
    public void deleteUser(@NonNull String userId, @NonNull OnUserUpdateListener listener) {
        db.collection(FirestoreConstants.COLLECTION_USERS)
                .document(userId)
                .delete()
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    /**
     * Cập nhật totalSpent của user (increment)
     */
    public void incrementTotalSpent(@NonNull String userId, long amount, @NonNull OnUserUpdateListener listener) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("totalSpent", FieldValue.increment(amount));
        updateUser(userId, updates, listener);
    }

    /**
     * Cộng loyalty points và tự động cập nhật tier dựa trên tổng điểm
     * Sử dụng transaction để đảm bảo tính nhất quán
     */
    public void addLoyaltyPoints(@NonNull String userId, long pointsToAdd, @NonNull OnUserUpdateListener listener) {
        if (pointsToAdd <= 0) {
            listener.onSuccess(); // Không có điểm để cộng, coi như thành công
            return;
        }

        final com.google.firebase.firestore.DocumentReference userRef = 
                db.collection(FirestoreConstants.COLLECTION_USERS).document(userId);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            com.google.firebase.firestore.DocumentSnapshot userDoc = transaction.get(userRef);
            if (!userDoc.exists()) {
                try {
                    throw new Exception("User not found");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            long currentPoints = userDoc.contains("loyaltyPoints") 
                    ? userDoc.getLong("loyaltyPoints") : 0L;
            long newTotalPoints = currentPoints + pointsToAdd;

            // Sử dụng LoyaltyPolicy để tính tier
            String newTier = LoyaltyPolicy.tierForPoints(newTotalPoints);

            Map<String, Object> updates = new HashMap<>();
            updates.put("loyaltyPoints", newTotalPoints);
            updates.put("loyaltyTier", newTier);
            transaction.update(userRef, updates);

            return null;
        })
        .addOnSuccessListener(aVoid -> listener.onSuccess())
        .addOnFailureListener(listener::onError);
    }

    /**
     * Cập nhật loyalty tier dựa trên điểm hiện tại (đồng bộ hóa tier)
     */
    public void syncLoyaltyTier(@NonNull String userId, @NonNull OnUserUpdateListener listener) {
        final com.google.firebase.firestore.DocumentReference userRef = 
                db.collection(FirestoreConstants.COLLECTION_USERS).document(userId);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            com.google.firebase.firestore.DocumentSnapshot userDoc = transaction.get(userRef);
            if (!userDoc.exists()) {
                try {
                    throw new Exception("User not found");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            long currentPoints = userDoc.contains("loyaltyPoints") 
                    ? userDoc.getLong("loyaltyPoints") : 0L;
            String correctTier = LoyaltyPolicy.tierForPoints(currentPoints);

            Map<String, Object> updates = new HashMap<>();
            updates.put("loyaltyTier", correctTier);
            transaction.update(userRef, updates);

            return null;
        })
        .addOnSuccessListener(aVoid -> listener.onSuccess())
        .addOnFailureListener(listener::onError);
    }

    /**
     * Parse DocumentSnapshot thành User object
     */
    @Nullable
    private User parseUser(@NonNull DocumentSnapshot doc) {
        try {
            User user = doc.toObject(User.class);
            if (user == null) return null;

            // Đảm bảo UID được set từ docId
            if (user.getUid() == null || user.getUid().isEmpty()) {
                user.setUid(doc.getId());
            }

            return user;
        } catch (Exception e) {
            return null;
        }
    }

    // ===== CALLBACK INTERFACES =====

    public interface OnUsersListener {
        void onUsersReceived(@NonNull List<User> users);
        void onError(@NonNull Exception error);
    }

    public interface OnUserListener {
        void onUserReceived(@NonNull User user);
        void onError(@NonNull Exception error);
    }

    public interface OnUserUpdateListener {
        void onSuccess();
        void onError(@NonNull Exception error);
    }
}

