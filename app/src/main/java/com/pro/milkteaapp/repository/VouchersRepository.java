package com.pro.milkteaapp.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.pro.milkteaapp.constants.FirestoreConstants;
import com.pro.milkteaapp.models.Voucher;

import java.util.Map;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository để quản lý các thao tác với Vouchers trong Firestore.
 */
public class VouchersRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Lấy tất cả vouchers đang active
     */
    public void fetchActiveVouchers(@NonNull OnVouchersListener listener) {
        Timestamp now = Timestamp.now();
        db.collection(FirestoreConstants.COLLECTION_VOUCHERS)
                .whereEqualTo("active", true)
                .whereLessThanOrEqualTo(FirestoreConstants.FIELD_START_AT, now)
                .whereGreaterThanOrEqualTo(FirestoreConstants.FIELD_END_AT, now)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Voucher> vouchers = parseVouchers(snapshot);
                    listener.onVouchersReceived(vouchers);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lắng nghe realtime vouchers đang active
     */
    public ListenerRegistration listenActiveVouchers(@NonNull OnVouchersListener listener) {
        Timestamp now = Timestamp.now();
        Query query = db.collection(FirestoreConstants.COLLECTION_VOUCHERS)
                .whereEqualTo("active", true)
                .whereLessThanOrEqualTo(FirestoreConstants.FIELD_START_AT, now)
                .whereGreaterThanOrEqualTo(FirestoreConstants.FIELD_END_AT, now);

        return query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            List<Voucher> vouchers = parseVouchers(snapshot);
            listener.onVouchersReceived(vouchers);
        });
    }

    public void fetchVoucherByCode(@NonNull String code, @NonNull OnVoucherListener listener) {
        db.collection(FirestoreConstants.COLLECTION_VOUCHERS)
                .whereEqualTo(FirestoreConstants.FIELD_VOUCHER_CODE, code.toUpperCase())
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null && !snapshot.isEmpty()) {
                        Voucher voucher = parseVoucher(snapshot.getDocuments().get(0));
                        if (voucher != null) {
                            listener.onVoucherReceived(voucher);
                        } else {
                            listener.onError(new Exception("Failed to parse voucher"));
                        }
                    } else {
                        listener.onError(new Exception("Voucher not found"));
                    }
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lấy một voucher theo ID
     */
    public void fetchVoucherById(@NonNull String voucherId, @NonNull OnVoucherListener listener) {
        db.collection(FirestoreConstants.COLLECTION_VOUCHERS)
                .document(voucherId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document != null && document.exists()) {
                        Voucher voucher = parseVoucher(document);
                        if (voucher != null) {
                            listener.onVoucherReceived(voucher);
                        } else {
                            listener.onError(new Exception("Failed to parse voucher"));
                        }
                    } else {
                        listener.onError(new Exception("Voucher not found"));
                    }
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Parse QuerySnapshot thành List<Voucher>
     */
    @NonNull
    private List<Voucher> parseVouchers(@Nullable QuerySnapshot snapshot) {
        List<Voucher> vouchers = new ArrayList<>();
        if (snapshot != null && !snapshot.isEmpty()) {
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                Voucher voucher = parseVoucher(doc);
                if (voucher != null) {
                    vouchers.add(voucher);
                }
            }
        }
        return vouchers;
    }

    /**
     * Lắng nghe realtime tất cả vouchers (không filter active)
     */
    public ListenerRegistration listenAllVouchers(@NonNull OnVouchersListener listener) {
        Query query = db.collection(FirestoreConstants.COLLECTION_VOUCHERS)
                .orderBy(FirestoreConstants.FIELD_VOUCHER_CODE, Query.Direction.ASCENDING);

        return query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            List<Voucher> vouchers = parseVouchers(snapshot);
            listener.onVouchersReceived(vouchers);
        });
    }

    /**
     * Tạo voucher mới
     */
    public void createVoucher(@NonNull Map<String, Object> data, @NonNull OnVoucherCreateListener listener) {
        db.collection(FirestoreConstants.COLLECTION_VOUCHERS)
                .add(data)
                .addOnSuccessListener(documentReference -> listener.onSuccess(documentReference.getId()))
                .addOnFailureListener(listener::onError);
    }

    /**
     * Cập nhật voucher
     */
    public void updateVoucher(@NonNull String voucherId, @NonNull Map<String, Object> data, @NonNull OnVoucherUpdateListener listener) {
        db.collection(FirestoreConstants.COLLECTION_VOUCHERS)
                .document(voucherId)
                .update(data)
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    /**
     * Cập nhật field active của voucher
     */
    public void updateVoucherActive(@NonNull String voucherId, boolean active, @NonNull OnVoucherUpdateListener listener) {
        db.collection(FirestoreConstants.COLLECTION_VOUCHERS)
                .document(voucherId)
                .update("active", active)
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    /**
     * Xóa voucher
     */
    public void deleteVoucher(@NonNull String voucherId, @NonNull OnVoucherUpdateListener listener) {
        db.collection(FirestoreConstants.COLLECTION_VOUCHERS)
                .document(voucherId)
                .delete()
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    /**
     * Parse DocumentSnapshot thành Voucher object
     */
    @Nullable
    private Voucher parseVoucher(@NonNull DocumentSnapshot doc) {
        try {
            Voucher voucher = doc.toObject(Voucher.class);
            if (voucher == null) return null;

            // Đảm bảo ID được set
            if (voucher.getId() == null || voucher.getId().isEmpty()) {
                voucher.setId(doc.getId());
            }

            return voucher;
        } catch (Exception e) {
            return null;
        }
    }

    // ===== CALLBACK INTERFACES =====

    public interface OnVouchersListener {
        void onVouchersReceived(@NonNull List<Voucher> vouchers);
        void onError(@NonNull Exception error);
    }

    public interface OnVoucherListener {
        void onVoucherReceived(@NonNull Voucher voucher);
        void onError(@NonNull Exception error);
    }

    public interface OnVoucherCreateListener {
        void onSuccess(@NonNull String voucherId);
        void onError(@NonNull Exception error);
    }

    public interface OnVoucherUpdateListener {
        void onSuccess();
        void onError(@NonNull Exception error);
    }
}

