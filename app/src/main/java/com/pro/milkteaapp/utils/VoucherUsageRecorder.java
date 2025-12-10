package com.pro.milkteaapp.utils;

import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.constants.FirestoreConstants;
import com.pro.milkteaapp.repository.VouchersRepository;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Helper class để ghi nhận voucher usage.
 * Tách logic voucher usage ra khỏi Activity/Fragment.
 */
public class VoucherUsageRecorder {
    
    private static final String TAG = "VoucherUsageRecorder";
    private final FirebaseFirestore db;
    
    public VoucherUsageRecorder() {
        this.db = FirebaseFirestore.getInstance();
    }
    
    /**
     * Ghi nhận users/{uid}/voucher_usages/{voucherId}.count += 1 nếu có code.
     * 
     * @param uid User ID
     * @param voucherCode Voucher code (có thể null hoặc empty)
     * @param onDone Callback khi hoàn thành (thành công hoặc không có voucher)
     * @param onError Callback khi lỗi
     */
    public void recordVoucherUsageIfNeeded(
            @NonNull String uid,
            @Nullable String voucherCode,
            @NonNull Runnable onDone,
            @NonNull Consumer<Exception> onError) {
        
        if (voucherCode == null || voucherCode.trim().isEmpty()) {
            onDone.run();
            return;
        }
        
        // Sử dụng VouchersRepository để tìm voucher
        VouchersRepository vouchersRepository = new VouchersRepository();
        vouchersRepository.fetchVoucherByCode(
                voucherCode.trim().toUpperCase(Locale.getDefault()),
                new VouchersRepository.OnVoucherListener() {
                    @Override
                    public void onVoucherReceived(@NonNull com.pro.milkteaapp.models.Voucher voucher) {
                        String voucherId = voucher.getId();
                        if (voucherId == null || voucherId.isEmpty()) {
                            onDone.run();
                            return;
                        }
                        
                        // Update voucher_usages subcollection
                        updateVoucherUsage(uid, voucherId, onDone, onError);
                    }
                    
                    @Override
                    public void onError(@NonNull Exception error) {
                        // Voucher không tồn tại hoặc lỗi -> không ghi nhận usage
                        Log.w(TAG, "Voucher not found or error: " + error.getMessage());
                        onDone.run();
                    }
                });
    }
    
    private void updateVoucherUsage(
            @NonNull String uid,
            @NonNull String voucherId,
            @NonNull Runnable onDone,
            @NonNull Consumer<Exception> onError) {
        
        db.collection(FirestoreConstants.COLLECTION_USERS).document(uid)
                .collection(FirestoreConstants.COLLECTION_VOUCHER_USAGES).document(voucherId)
                .update("count", FieldValue.increment(1))
                .addOnSuccessListener(x -> onDone.run())
                .addOnFailureListener(e -> {
                    // Nếu document chưa tồn tại, tạo mới
                    Map<String, Object> seed = new HashMap<>();
                    seed.put("count", 1L);
                    db.collection(FirestoreConstants.COLLECTION_USERS).document(uid)
                            .collection(FirestoreConstants.COLLECTION_VOUCHER_USAGES).document(voucherId)
                            .set(seed)
                            .addOnSuccessListener(x -> onDone.run())
                            .addOnFailureListener(onError::accept);
                });
    }
}

