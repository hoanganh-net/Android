package com.pro.milkteaapp.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.pro.milkteaapp.constants.FirestoreConstants;
import com.pro.milkteaapp.models.Combo;
import com.pro.milkteaapp.models.Product;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Repository để quản lý các thao tác với Combos trong Firestore.
 */
public class ComboRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Lắng nghe realtime tất cả combos, sắp xếp theo name
     */
    public ListenerRegistration listenAllCombos(@NonNull OnCombosListener listener) {
        Query query = db.collection(FirestoreConstants.COLLECTION_COMBOS)
                .orderBy(FirestoreConstants.FIELD_COMBO_NAME, Query.Direction.ASCENDING);

        return query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            List<Combo> combos = parseCombos(snapshot);
            listener.onCombosReceived(combos);
        });
    }

    /**
     * Lấy tất cả combos một lần (không realtime)
     */
    public void fetchAllCombos(@NonNull OnCombosListener listener) {
        db.collection(FirestoreConstants.COLLECTION_COMBOS)
                .orderBy(FirestoreConstants.FIELD_COMBO_NAME, Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Combo> combos = parseCombos(snapshot);
                    listener.onCombosReceived(combos);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lấy combos theo status (active/inactive)
     */
    public void fetchCombosByStatus(@NonNull String status, @NonNull OnCombosListener listener) {
        db.collection(FirestoreConstants.COLLECTION_COMBOS)
                .whereEqualTo(FirestoreConstants.FIELD_COMBO_STATUS, status)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Combo> combos = parseCombos(snapshot);
                    // Sắp xếp theo tên ở client-side để tránh cần composite index
                    combos.sort((c1, c2) -> {
                        String n1 = c1.getName() != null ? c1.getName() : "";
                        String n2 = c2.getName() != null ? c2.getName() : "";
                        return n1.compareToIgnoreCase(n2);
                    });
                    listener.onCombosReceived(combos);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("ComboRepository", "Error fetching combos by status: " + status, e);
                    listener.onError(e);
                });
    }

    /**
     * Lấy các combo đang active
     */
    public void fetchActiveCombos(@NonNull OnCombosListener listener) {
        fetchCombosByStatus(FirestoreConstants.STATUS_ACTIVE, listener);
    }

    /**
     * Lấy một combo theo ID
     */
    public void fetchComboById(@NonNull String comboId, @NonNull OnComboListener listener) {
        db.collection(FirestoreConstants.COLLECTION_COMBOS)
                .document(comboId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document != null && document.exists()) {
                        Combo combo = parseCombo(document);
                        if (combo != null) {
                            listener.onComboReceived(combo);
                        } else {
                            listener.onError(new Exception("Failed to parse combo"));
                        }
                    } else {
                        listener.onError(new Exception("Combo not found"));
                    }
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Parse QuerySnapshot thành List<Combo>
     */
    @NonNull
    private List<Combo> parseCombos(@Nullable QuerySnapshot snapshot) {
        List<Combo> combos = new ArrayList<>();
        if (snapshot != null && !snapshot.isEmpty()) {
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                Combo combo = parseCombo(doc);
                if (combo != null) {
                    combos.add(combo);
                }
            }
        }
        return combos;
    }

    /**
     * Tạo combo mới
     */
    public void createCombo(@NonNull String comboId, @NonNull Map<String, Object> data, @NonNull OnComboCreateListener listener) {
        data.put(FirestoreConstants.FIELD_UPDATED_AT, FieldValue.serverTimestamp());
        if (!data.containsKey(FirestoreConstants.FIELD_CREATED_AT)) {
            data.put(FirestoreConstants.FIELD_CREATED_AT, FieldValue.serverTimestamp());
        }
        
        db.collection(FirestoreConstants.COLLECTION_COMBOS)
                .document(comboId)
                .set(data)
                .addOnSuccessListener(aVoid -> listener.onSuccess(comboId))
                .addOnFailureListener(listener::onError);
    }

    /**
     * Cập nhật combo
     */
    public void updateCombo(@NonNull String comboId, @NonNull Map<String, Object> data, @NonNull OnComboUpdateListener listener) {
        data.put(FirestoreConstants.FIELD_UPDATED_AT, FieldValue.serverTimestamp());
        
        db.collection(FirestoreConstants.COLLECTION_COMBOS)
                .document(comboId)
                .update(data)
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    /**
     * Xóa combo
     */
    public void deleteCombo(@NonNull String comboId, @NonNull OnComboUpdateListener listener) {
        db.collection(FirestoreConstants.COLLECTION_COMBOS)
                .document(comboId)
                .delete()
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    /**
     * Tìm tất cả combo chứa productId cụ thể
     */
    public void findCombosContainingProduct(@NonNull String productId, @NonNull OnCombosListener listener) {
        db.collection(FirestoreConstants.COLLECTION_COMBOS)
                .whereArrayContains(FirestoreConstants.FIELD_COMBO_PRODUCT_IDS, productId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Combo> combos = parseCombos(snapshot);
                    listener.onCombosReceived(combos);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Tính lại và cập nhật giá combo dựa trên giá mới của các sản phẩm
     */
    public void recalculateComboPrice(@NonNull String comboId, @NonNull ProductRepository productRepository, @NonNull OnComboUpdateListener listener) {
        // Lấy combo
        fetchComboById(comboId, new OnComboListener() {
            @Override
            public void onComboReceived(@NonNull Combo combo) {
                List<String> productIds = combo.getProductIds();
                if (productIds == null || productIds.isEmpty()) {
                    listener.onSuccess();
                    return;
                }

                // Lấy giá mới của tất cả sản phẩm
                final int[] loadedCount = {0};
                final double[] totalPrice = {0.0};
                final int totalProducts = productIds.size();

                for (String productId : productIds) {
                    productRepository.fetchProductById(productId, new ProductRepository.OnSingleProductListener() {
                        @Override
                        public void onProductReceived(@NonNull Product product) {
                            Double price = product.getPrice();
                            if (price != null) {
                                totalPrice[0] += price;
                            }
                            loadedCount[0]++;
                            if (loadedCount[0] == totalProducts) {
                                // Tính lại giá combo
                                Integer discountPercent = combo.getDiscountPercent();
                                if (discountPercent == null) {
                                    discountPercent = 10; // Mặc định 10%
                                }
                                
                                double discountAmount = totalPrice[0] * discountPercent / 100.0;
                                double newComboPrice = totalPrice[0] - discountAmount;

                                // Cập nhật combo
                                Map<String, Object> updateData = new java.util.HashMap<>();
                                updateData.put(FirestoreConstants.FIELD_COMBO_PRICE, newComboPrice);
                                updateData.put(FirestoreConstants.FIELD_COMBO_ORIGINAL_PRICE, totalPrice[0]);
                                updateData.put(FirestoreConstants.FIELD_UPDATED_AT, FieldValue.serverTimestamp());

                                updateCombo(comboId, updateData, listener);
                            }
                        }

                        @Override
                        public void onError(@NonNull Exception error) {
                            loadedCount[0]++;
                            if (loadedCount[0] == totalProducts) {
                                // Nếu có lỗi load sản phẩm, vẫn cập nhật với giá hiện tại
                                listener.onError(new Exception("Failed to load some products for combo: " + error.getMessage()));
                            }
                        }
                    });
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                listener.onError(error);
            }
        });
    }

    /**
     * Parse DocumentSnapshot thành Combo object
     */
    @Nullable
    private Combo parseCombo(@NonNull DocumentSnapshot doc) {
        try {
            Combo combo = Combo.from(doc);
            if (combo == null) {
                // Fallback: parse thủ công
                combo = doc.toObject(Combo.class);
                if (combo == null) return null;
                
                if (combo.getId() == null || combo.getId().isEmpty()) {
                    combo.setId(doc.getId());
                }
            }

            // Fallback cho các field legacy
            if (combo.getImageUrl() == null) {
                String legacyImage = doc.getString("image");
                if (legacyImage == null) {
                    legacyImage = doc.getString("imgUrl");
                }
                if (legacyImage != null) {
                    combo.setImageUrl(legacyImage);
                }
            }

            return combo;
        } catch (Exception e) {
            return null;
        }
    }

    // ===== CALLBACK INTERFACES =====

    public interface OnCombosListener {
        void onCombosReceived(@NonNull List<Combo> combos);
        void onError(@NonNull Exception error);
    }

    public interface OnComboListener {
        void onComboReceived(@NonNull Combo combo);
        void onError(@NonNull Exception error);
    }

    public interface OnComboCreateListener {
        void onSuccess(@NonNull String comboId);
        void onError(@NonNull Exception error);
    }

    public interface OnComboUpdateListener {
        void onSuccess();
        void onError(@NonNull Exception error);
    }
}

