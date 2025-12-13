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
import com.pro.milkteaapp.models.Category;

import java.util.Map;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository để quản lý các thao tác với Categories trong Firestore.
 */
public class CategoriesRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Lấy tất cả categories đang active
     */
    public void fetchActiveCategories(@NonNull OnCategoriesListener listener) {
        db.collection(FirestoreConstants.COLLECTION_CATEGORIES)
                .whereEqualTo(FirestoreConstants.FIELD_ACTIVE, true)
                .orderBy(FirestoreConstants.FIELD_NAME, Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Category> categories = parseCategories(snapshot);
                    listener.onCategoriesReceived(categories);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lấy một category theo ID
     */
    public void fetchCategoryById(@NonNull String categoryId, @NonNull OnCategoryListener listener) {
        db.collection(FirestoreConstants.COLLECTION_CATEGORIES)
                .document(categoryId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document != null && document.exists()) {
                        Category category = parseCategory(document);
                        if (category != null) {
                            listener.onCategoryReceived(category);
                        } else {
                            listener.onError(new Exception("Failed to parse category"));
                        }
                    } else {
                        listener.onError(new Exception("Category not found"));
                    }
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Parse QuerySnapshot thành List<Category>
     */
    @NonNull
    private List<Category> parseCategories(@Nullable QuerySnapshot snapshot) {
        List<Category> categories = new ArrayList<>();
        if (snapshot != null && !snapshot.isEmpty()) {
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                Category category = parseCategory(doc);
                if (category != null) {
                    categories.add(category);
                }
            }
        }
        return categories;
    }

    /**
     * Lắng nghe realtime tất cả categories (không filter active)
     */
    public ListenerRegistration listenAllCategories(@NonNull OnCategoriesListener listener) {
        Query query = db.collection(FirestoreConstants.COLLECTION_CATEGORIES)
                .orderBy(FirestoreConstants.FIELD_CREATED_AT, Query.Direction.DESCENDING);

        return query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            List<Category> categories = parseCategories(snapshot);
            listener.onCategoriesReceived(categories);
        });
    }

    /**
     * Tạo category mới
     */
    public void createCategory(@NonNull Map<String, Object> data, @NonNull OnCategoryCreateListener listener) {
        data.put(FirestoreConstants.FIELD_CREATED_AT, FieldValue.serverTimestamp());
        data.put(FirestoreConstants.FIELD_UPDATED_AT, FieldValue.serverTimestamp());
        
        db.collection(FirestoreConstants.COLLECTION_CATEGORIES)
                .add(data)
                .addOnSuccessListener(documentReference -> listener.onSuccess(documentReference.getId()))
                .addOnFailureListener(listener::onError);
    }

    /**
     * Cập nhật category
     */
    public void updateCategory(@NonNull String categoryId, @NonNull Map<String, Object> data, @NonNull OnCategoryUpdateListener listener) {
        data.put(FirestoreConstants.FIELD_UPDATED_AT, FieldValue.serverTimestamp());
        
        db.collection(FirestoreConstants.COLLECTION_CATEGORIES)
                .document(categoryId)
                .update(data)
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    /**
     * Xóa category và tất cả sản phẩm trong danh mục đó
     * Đồng thời xóa category khỏi danh sách categories của các topping liên quan
     */
    public void deleteCategory(@NonNull String categoryId, @NonNull String categoryName, @NonNull OnCategoryUpdateListener listener) {
        // Lấy tất cả sản phẩm thuộc category này
        db.collection(FirestoreConstants.COLLECTION_PRODUCTS)
                .whereEqualTo(FirestoreConstants.FIELD_CATEGORY, categoryName)
                .get()
                .addOnSuccessListener(productSnapshot -> {
                    // Lấy tất cả toppings có category này trong danh sách categories
                    db.collection(FirestoreConstants.COLLECTION_TOPPINGS)
                            .whereArrayContains("categories", categoryName)
                            .get()
                            .addOnSuccessListener(toppingSnapshot -> {
                                // Tạo batch để xóa sản phẩm, cập nhật toppings và xóa category
                                com.google.firebase.firestore.WriteBatch batch = db.batch();
                                
                                // Xóa tất cả sản phẩm
                                for (DocumentSnapshot productDoc : productSnapshot.getDocuments()) {
                                    batch.delete(productDoc.getReference());
                                }
                                
                                // Cập nhật tất cả toppings: xóa category khỏi danh sách categories
                                for (DocumentSnapshot toppingDoc : toppingSnapshot.getDocuments()) {
                                    List<String> categories = (List<String>) toppingDoc.get("categories");
                                    if (categories != null && categories.contains(categoryName)) {
                                        List<String> updatedCategories = new ArrayList<>(categories);
                                        updatedCategories.remove(categoryName);
                                        batch.update(toppingDoc.getReference(), "categories", updatedCategories);
                                    }
                                }
                                
                                // Xóa category
                                batch.delete(db.collection(FirestoreConstants.COLLECTION_CATEGORIES).document(categoryId));
                                
                                // Commit batch
                                batch.commit()
                                        .addOnSuccessListener(aVoid -> listener.onSuccess())
                                        .addOnFailureListener(listener::onError);
                            })
                            .addOnFailureListener(listener::onError);
                })
                .addOnFailureListener(listener::onError);
    }
    
    /**
     * Xóa category (không xóa sản phẩm) - giữ lại để tương thích
     */
    public void deleteCategoryOnly(@NonNull String categoryId, @NonNull OnCategoryUpdateListener listener) {
        db.collection(FirestoreConstants.COLLECTION_CATEGORIES)
                .document(categoryId)
                .delete()
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    /**
     * Parse DocumentSnapshot thành Category object
     */
    @Nullable
    private Category parseCategory(@NonNull DocumentSnapshot doc) {
        try {
            Category category = doc.toObject(Category.class);
            if (category == null) return null;

            // Đảm bảo ID được set
            if (category.getId() == null || category.getId().isEmpty()) {
                category.setId(doc.getId());
            }

            // Fallback cho các field legacy
            if (category.getImageUrl() == null) {
                String legacyImage = doc.getString("image");
                if (legacyImage == null) {
                    legacyImage = doc.getString("imgUrl");
                }
                if (legacyImage != null) {
                    category.setImageUrl(legacyImage);
                }
            }

            return category;
        } catch (Exception e) {
            return null;
        }
    }

    // ===== CALLBACK INTERFACES =====

    public interface OnCategoriesListener {
        void onCategoriesReceived(@NonNull List<Category> categories);
        void onError(@NonNull Exception error);
    }

    public interface OnCategoryListener {
        void onCategoryReceived(@NonNull Category category);
        void onError(@NonNull Exception error);
    }

    public interface OnCategoryCreateListener {
        void onSuccess(@NonNull String categoryId);
        void onError(@NonNull Exception error);
    }

    public interface OnCategoryUpdateListener {
        void onSuccess();
        void onError(@NonNull Exception error);
    }
}

