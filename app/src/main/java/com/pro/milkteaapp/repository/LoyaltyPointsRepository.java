package com.pro.milkteaapp.repository;

import androidx.annotation.NonNull;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.pro.milkteaapp.constants.FirestoreConstants;
import com.pro.milkteaapp.models.LoyaltyPointEntry;
import com.pro.milkteaapp.models.User;
import com.pro.milkteaapp.utils.LoyaltyPolicy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository quản lý điểm khách hàng thân thiết (có thời gian hết hạn)
 */
public class LoyaltyPointsRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Thêm điểm khách hàng thân thiết cho user (từ đơn hàng)
     * Tự động tính thời gian hết hạn (1 năm)
     */
    public void addLoyaltyPoints(@NonNull String userId, long points, @NonNull String orderId, 
                                 @NonNull OnLoyaltyPointsListener listener) {
        if (points <= 0) {
            listener.onSuccess(0L);
            return;
        }

        LoyaltyPointEntry entry = new LoyaltyPointEntry(userId, points, orderId);
        
        // Lưu vào subcollection users/{userId}/loyaltyPoints
        db.collection(FirestoreConstants.COLLECTION_USERS)
                .document(userId)
                .collection("loyaltyPoints")
                .add(entry)
                .addOnSuccessListener(documentReference -> {
                    entry.setId(documentReference.getId());
                    // Cập nhật tổng điểm và tier trong user document
                    updateUserLoyaltySummary(userId, points, listener);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Cập nhật tổng điểm và tier trong user document
     */
    private void updateUserLoyaltySummary(@NonNull String userId, long pointsToAdd, 
                                         @NonNull OnLoyaltyPointsListener listener) {
        DocumentReference userRef = db.collection(FirestoreConstants.COLLECTION_USERS)
                .document(userId);

        db.runTransaction((com.google.firebase.firestore.Transaction.Function<Long>) transaction -> {
            DocumentSnapshot userDoc = transaction.get(userRef);
            if (!userDoc.exists()) {
                throw new RuntimeException("User not found");
            }

            // Tính tổng điểm còn hiệu lực (chưa hết hạn)
            long currentTotal = userDoc.contains(FirestoreConstants.FIELD_LOYALTY_POINTS)
                    ? userDoc.getLong(FirestoreConstants.FIELD_LOYALTY_POINTS) : 0L;
            
            // Lấy tất cả entries chưa hết hạn
            Query query = userRef.collection("loyaltyPoints")
                    .whereEqualTo("expired", false);
            
            // Note: Transaction không hỗ trợ query subcollection trực tiếp
            // Nên ta sẽ cộng điểm mới vào tổng hiện tại
            long newTotal = currentTotal + pointsToAdd;
            
            // Tính tier mới
            String newTier = LoyaltyPolicy.tierForPoints(newTotal);

            Map<String, Object> updates = new HashMap<>();
            updates.put(FirestoreConstants.FIELD_LOYALTY_POINTS, newTotal);
            updates.put(FirestoreConstants.FIELD_LOYALTY_TIER, newTier);
            updates.put(FirestoreConstants.FIELD_UPDATED_AT, FieldValue.serverTimestamp());
            
            transaction.update(userRef, updates);
            
            return newTotal;
        })
        .addOnSuccessListener(total -> listener.onSuccess(total))
        .addOnFailureListener(listener::onError);
    }

    /**
     * Lấy tất cả điểm khách hàng thân thiết của user (bao gồm cả đã hết hạn)
     */
    public void fetchAllLoyaltyPoints(@NonNull String userId, @NonNull OnLoyaltyPointsEntriesListener listener) {
        db.collection(FirestoreConstants.COLLECTION_USERS)
                .document(userId)
                .collection("loyaltyPoints")
                .orderBy("earnedAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<LoyaltyPointEntry> entries = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        LoyaltyPointEntry entry = doc.toObject(LoyaltyPointEntry.class);
                        if (entry != null) {
                            entry.setId(doc.getId());
                            entry.checkExpired(); // Kiểm tra và cập nhật trạng thái hết hạn
                            entries.add(entry);
                        }
                    }
                    listener.onEntriesReceived(entries);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lấy tổng điểm còn hiệu lực (chưa hết hạn)
     */
    public void getValidLoyaltyPoints(@NonNull String userId, @NonNull OnLoyaltyPointsListener listener) {
        db.collection(FirestoreConstants.COLLECTION_USERS)
                .document(userId)
                .collection("loyaltyPoints")
                .whereEqualTo("expired", false)
                .get()
                .addOnSuccessListener(snapshot -> {
                    long total = 0L;
                    Timestamp now = Timestamp.now();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        LoyaltyPointEntry entry = doc.toObject(LoyaltyPointEntry.class);
                        if (entry != null) {
                            // Kiểm tra lại thời gian hết hạn
                            if (entry.getExpiresAt() != null && now.compareTo(entry.getExpiresAt()) < 0) {
                                total += entry.getPoints();
                            }
                        }
                    }
                    listener.onSuccess(total);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Đánh dấu các điểm đã hết hạn
     */
    public void markExpiredPoints(@NonNull String userId, @NonNull OnUserUpdateListener listener) {
        DocumentReference userRef = db.collection(FirestoreConstants.COLLECTION_USERS)
                .document(userId);

        db.collection(FirestoreConstants.COLLECTION_USERS)
                .document(userId)
                .collection("loyaltyPoints")
                .whereEqualTo("expired", false)
                .get()
                .addOnSuccessListener(snapshot -> {
                    Timestamp now = Timestamp.now();
                    List<DocumentReference> toUpdate = new ArrayList<>();
                    
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        LoyaltyPointEntry entry = doc.toObject(LoyaltyPointEntry.class);
                        if (entry != null && entry.getExpiresAt() != null) {
                            if (now.compareTo(entry.getExpiresAt()) >= 0) {
                                toUpdate.add(doc.getReference());
                            }
                        }
                    }
                    
                    if (toUpdate.isEmpty()) {
                        listener.onSuccess();
                        return;
                    }
                    
                    // Batch update
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    for (DocumentReference ref : toUpdate) {
                        batch.update(ref, "expired", true);
                    }
                    
                    batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                // Recalculate total points
                                recalculateTotalPoints(userId, listener);
                            })
                            .addOnFailureListener(listener::onError);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Tính lại tổng điểm từ các entries còn hiệu lực
     */
    private void recalculateTotalPoints(@NonNull String userId, @NonNull OnUserUpdateListener listener) {
        getValidLoyaltyPoints(userId, new OnLoyaltyPointsListener() {
            @Override
            public void onSuccess(long totalPoints) {
                DocumentReference userRef = db.collection(FirestoreConstants.COLLECTION_USERS)
                        .document(userId);
                
                String newTier = LoyaltyPolicy.tierForPoints(totalPoints);
                
                Map<String, Object> updates = new HashMap<>();
                updates.put(FirestoreConstants.FIELD_LOYALTY_POINTS, totalPoints);
                updates.put(FirestoreConstants.FIELD_LOYALTY_TIER, newTier);
                updates.put(FirestoreConstants.FIELD_UPDATED_AT, FieldValue.serverTimestamp());
                
                userRef.update(updates)
                        .addOnSuccessListener(aVoid -> listener.onSuccess())
                        .addOnFailureListener(listener::onError);
            }

            @Override
            public void onError(@NonNull Exception error) {
                listener.onError(error);
            }
        });
    }

    // ===== CALLBACK INTERFACES =====

    public interface OnLoyaltyPointsListener {
        void onSuccess(long totalPoints);
        void onError(@NonNull Exception error);
    }

    public interface OnLoyaltyPointsEntriesListener {
        void onEntriesReceived(@NonNull List<LoyaltyPointEntry> entries);
        void onError(@NonNull Exception error);
    }

    public interface OnUserUpdateListener {
        void onSuccess();
        void onError(@NonNull Exception error);
    }
}

