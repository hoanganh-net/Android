package com.pro.milkteaapp.repository;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;
import com.pro.milkteaapp.constants.FirestoreConstants;
import com.pro.milkteaapp.models.Voucher;

import java.util.HashMap;
import java.util.Map;

/**
 * Repository quản lý điểm đổi quà (reward points)
 * Điểm này dùng để đổi voucher giảm giá
 */
public class RewardPointsRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Cộng điểm đổi quà cho user (từ đơn hàng)
     * Tỷ lệ: 10.000đ = 1 điểm (giống điểm khách hàng thân thiết)
     */
    public void addRewardPoints(@NonNull String userId, long points, @NonNull OnRewardPointsListener listener) {
        if (points <= 0) {
            listener.onSuccess(0L);
            return;
        }

        DocumentReference userRef = db.collection(FirestoreConstants.COLLECTION_USERS)
                .document(userId);

        db.runTransaction((Transaction.Function<Long>) transaction -> {
            DocumentSnapshot userDoc = transaction.get(userRef);
            if (!userDoc.exists()) {
                throw new RuntimeException("User not found");
            }

            long currentPoints = userDoc.contains(FirestoreConstants.FIELD_REWARD_POINTS)
                    ? userDoc.getLong(FirestoreConstants.FIELD_REWARD_POINTS) : 0L;
            long newTotal = currentPoints + points;

            Map<String, Object> updates = new HashMap<>();
            updates.put(FirestoreConstants.FIELD_REWARD_POINTS, newTotal);
            updates.put(FirestoreConstants.FIELD_UPDATED_AT, FieldValue.serverTimestamp());
            
            transaction.update(userRef, updates);
            
            return newTotal;
        })
        .addOnSuccessListener(total -> listener.onSuccess(total))
        .addOnFailureListener(listener::onError);
    }

    /**
     * Trừ điểm đổi quà khi user đổi voucher.
     * 
     * Lưu ý: Voucher đổi điểm có thể đổi nhiều lần, miễn là voucher còn active và user có đủ điểm.
     * perUserLimit chỉ áp dụng khi SỬ DỤNG voucher (checkout), không áp dụng khi ĐỔI voucher.
     * 
     * @param userId User ID
     * @param pointsToDeduct Số điểm cần trừ
     * @param voucherId ID của voucher được đổi
     * @param listener Callback listener
     */
    public void redeemRewardPoints(@NonNull String userId, long pointsToDeduct, 
                                   @NonNull String voucherId, @NonNull OnRewardPointsListener listener) {
        if (pointsToDeduct <= 0) {
            listener.onError(new Exception("Invalid points to deduct"));
            return;
        }

        DocumentReference userRef = db.collection(FirestoreConstants.COLLECTION_USERS)
                .document(userId);

        db.runTransaction((Transaction.Function<Long>) transaction -> {
            DocumentSnapshot userDoc = transaction.get(userRef);
            if (!userDoc.exists()) {
                throw new RuntimeException("User not found");
            }

            long currentPoints = userDoc.contains(FirestoreConstants.FIELD_REWARD_POINTS)
                    ? userDoc.getLong(FirestoreConstants.FIELD_REWARD_POINTS) : 0L;
            
            if (currentPoints < pointsToDeduct) {
                throw new RuntimeException("Insufficient reward points");
            }

            long newTotal = currentPoints - pointsToDeduct;

            Map<String, Object> updates = new HashMap<>();
            updates.put(FirestoreConstants.FIELD_REWARD_POINTS, newTotal);
            updates.put(FirestoreConstants.FIELD_UPDATED_AT, FieldValue.serverTimestamp());
            
            transaction.update(userRef, updates);
            
            // Lưu lịch sử đổi voucher
            Map<String, Object> redemption = new HashMap<>();
            redemption.put("voucherId", voucherId);
            redemption.put("pointsUsed", pointsToDeduct);
            redemption.put("redeemedAt", FieldValue.serverTimestamp());
            
            userRef.collection("rewardRedemptions")
                    .add(redemption);
            
            return newTotal;
        })
        .addOnSuccessListener(total -> listener.onSuccess(total))
        .addOnFailureListener(listener::onError);
    }

    /**
     * Lấy số điểm đổi quà hiện tại của user
     */
    public void getRewardPoints(@NonNull String userId, @NonNull OnRewardPointsListener listener) {
        db.collection(FirestoreConstants.COLLECTION_USERS)
                .document(userId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document != null && document.exists()) {
                        long points = document.contains(FirestoreConstants.FIELD_REWARD_POINTS)
                                ? document.getLong(FirestoreConstants.FIELD_REWARD_POINTS) : 0L;
                        listener.onSuccess(points);
                    } else {
                        listener.onError(new Exception("User not found"));
                    }
                })
                .addOnFailureListener(listener::onError);
    }

    // ===== CALLBACK INTERFACES =====

    public interface OnRewardPointsListener {
        void onSuccess(long totalPoints);
        void onError(@NonNull Exception error);
    }
}

