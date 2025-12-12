package com.pro.milkteaapp.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.pro.milkteaapp.constants.FirestoreConstants;
import com.pro.milkteaapp.models.Topping;

import java.util.Map;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository để quản lý các thao tác với Toppings trong Firestore.
 */
public class ToppingsRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Lấy tất cả toppings đang active
     */
    public void fetchActiveToppings(@NonNull OnToppingsListener listener) {
        db.collection(FirestoreConstants.COLLECTION_TOPPINGS)
                .whereEqualTo(FirestoreConstants.FIELD_ACTIVE, true)
                .orderBy(FirestoreConstants.FIELD_TOPPING_NAME, Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Topping> toppings = parseToppings(snapshot);
                    listener.onToppingsReceived(toppings);
                })
                .addOnFailureListener(listener::onError);
    }

    public void fetchToppingsByCategory(@NonNull String category, @NonNull OnToppingsListener listener) {
        db.collection(FirestoreConstants.COLLECTION_TOPPINGS)
                .whereEqualTo(FirestoreConstants.FIELD_ACTIVE, true)
                .whereArrayContains("categories", category)
                .orderBy(FirestoreConstants.FIELD_TOPPING_NAME, Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Topping> toppings = parseToppings(snapshot);
                    listener.onToppingsReceived(toppings);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lấy tất cả toppings (dành cho admin)
     */
    public void fetchAllToppings(@NonNull OnToppingsListener listener) {
        db.collection(FirestoreConstants.COLLECTION_TOPPINGS)
                .orderBy(FirestoreConstants.FIELD_TOPPING_NAME, Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Topping> toppings = parseToppings(snapshot);
                    listener.onToppingsReceived(toppings);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lấy một topping theo ID
     */
    public void fetchToppingById(@NonNull String toppingId, @NonNull OnToppingListener listener) {
        db.collection(FirestoreConstants.COLLECTION_TOPPINGS)
                .document(toppingId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document != null && document.exists()) {
                        Topping topping = parseTopping(document);
                        if (topping != null) {
                            listener.onToppingReceived(topping);
                        } else {
                            listener.onError(new Exception("Failed to parse topping"));
                        }
                    } else {
                        listener.onError(new Exception("Topping not found"));
                    }
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Parse QuerySnapshot thành List<Topping>
     */
    @NonNull
    private List<Topping> parseToppings(@Nullable QuerySnapshot snapshot) {
        List<Topping> toppings = new ArrayList<>();
        if (snapshot != null && !snapshot.isEmpty()) {
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                Topping topping = parseTopping(doc);
                if (topping != null) {
                    toppings.add(topping);
                }
            }
        }
        return toppings;
    }

    /**
     * Lắng nghe realtime tất cả toppings (không filter active)
     */
    public ListenerRegistration listenAllToppings(@NonNull OnToppingsListener listener) {
        Query query = db.collection(FirestoreConstants.COLLECTION_TOPPINGS)
                .orderBy(FirestoreConstants.FIELD_TOPPING_NAME, Query.Direction.ASCENDING);

        return query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            List<Topping> toppings = parseToppings(snapshot);
            listener.onToppingsReceived(toppings);
        });
    }

    /**
     * Tạo topping mới
     */
    public void createTopping(@NonNull Map<String, Object> data, @NonNull OnToppingCreateListener listener) {
        db.collection(FirestoreConstants.COLLECTION_TOPPINGS)
                .add(data)
                .addOnSuccessListener(documentReference -> listener.onSuccess(documentReference.getId()))
                .addOnFailureListener(listener::onError);
    }

    /**
     * Cập nhật topping
     */
    public void updateTopping(@NonNull String toppingId, @NonNull Map<String, Object> data, @NonNull OnToppingUpdateListener listener) {
        db.collection(FirestoreConstants.COLLECTION_TOPPINGS)
                .document(toppingId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    public void deleteTopping(@NonNull String toppingId, @NonNull OnToppingUpdateListener listener) {
        db.collection(FirestoreConstants.COLLECTION_TOPPINGS)
                .document(toppingId)
                .delete()
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    /**
     * Parse DocumentSnapshot thành Topping object
     */
    @Nullable
    private Topping parseTopping(@NonNull DocumentSnapshot doc) {
        try {
            Topping topping = doc.toObject(Topping.class);
            if (topping == null) return null;

            // Đảm bảo ID được set
            if (topping.getId() == null || topping.getId().isEmpty()) {
                topping.setId(doc.getId());
            }

            return topping;
        } catch (Exception e) {
            return null;
        }
    }

    // ===== CALLBACK INTERFACES =====

    public interface OnToppingsListener {
        void onToppingsReceived(@NonNull List<Topping> toppings);
        void onError(@NonNull Exception error);
    }

    public interface OnToppingListener {
        void onToppingReceived(@NonNull Topping topping);
        void onError(@NonNull Exception error);
    }

    public interface OnToppingCreateListener {
        void onSuccess(@NonNull String toppingId);
        void onError(@NonNull Exception error);
    }

    public interface OnToppingUpdateListener {
        void onSuccess();
        void onError(@NonNull Exception error);
    }
}

