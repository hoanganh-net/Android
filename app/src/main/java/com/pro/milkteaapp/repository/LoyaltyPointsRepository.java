package com.pro.milkteaapp.repository;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.constants.FirestoreConstants;
import com.pro.milkteaapp.models.LoyaltyPointEntry;
import com.pro.milkteaapp.utils.LoyaltyPolicy;

import java.util.HashMap;
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

        db.runTransaction(transaction -> {
            DocumentSnapshot userDoc = transaction.get(userRef);
            if (!userDoc.exists()) {
                throw new RuntimeException("User not found");
            }

            // Tính tổng điểm còn hiệu lực (chưa hết hạn)
            Long loyaltyPointsLong = userDoc.getLong(FirestoreConstants.FIELD_LOYALTY_POINTS);
            long currentTotal = loyaltyPointsLong != null ? loyaltyPointsLong : 0L;
            
            // Lấy tất cả entries chưa hết hạn

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
        .addOnSuccessListener(listener::onSuccess)
        .addOnFailureListener(listener::onError);
    }

    // ===== CALLBACK INTERFACES =====

    public interface OnLoyaltyPointsListener {
        void onSuccess(long totalPoints);
        void onError(@NonNull Exception error);
    }

}

