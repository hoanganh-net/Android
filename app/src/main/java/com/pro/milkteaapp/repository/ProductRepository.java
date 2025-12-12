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
import com.pro.milkteaapp.models.Product;

import java.util.Map;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository để quản lý các thao tác với Product trong Firestore.
 * Tập trung hóa logic truy cập dữ liệu, dễ test và bảo trì.
 */
public class ProductRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Lắng nghe realtime tất cả products, sắp xếp theo category và name
     */
    public ListenerRegistration listenAllProduct(@NonNull OnProductListener listener) {
        Query query = db.collection(FirestoreConstants.COLLECTION_PRODUCTS)
                .orderBy(FirestoreConstants.FIELD_CATEGORY, Query.Direction.ASCENDING)
                .orderBy(FirestoreConstants.FIELD_NAME, Query.Direction.ASCENDING);

        return query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            List<Product> products = parseProducts(snapshot);
            listener.onProductReceived(products);
        });
    }

    /**
     * Lấy tất cả products một lần (không realtime)
     */
    public void fetchAllProduct(@NonNull OnProductListener listener) {
        db.collection(FirestoreConstants.COLLECTION_PRODUCTS)
                .orderBy(FirestoreConstants.FIELD_CATEGORY, Query.Direction.ASCENDING)
                .orderBy(FirestoreConstants.FIELD_NAME, Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Product> products = parseProducts(snapshot);
                    listener.onProductReceived(products);
                })
                .addOnFailureListener(listener::onError);
    }

    public void fetchProductByStatus(@NonNull String status, @NonNull OnProductListener listener) {
        db.collection(FirestoreConstants.COLLECTION_PRODUCTS)
                .whereEqualTo(FirestoreConstants.FIELD_STATUS, status)
                .orderBy(FirestoreConstants.FIELD_NAME, Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Product> products = parseProducts(snapshot);
                    listener.onProductReceived(products);
                })
                .addOnFailureListener(listener::onError);
    }

    public void fetchProductById(@NonNull String productId, @NonNull OnSingleProductListener listener) {
        db.collection(FirestoreConstants.COLLECTION_PRODUCTS)
                .document(productId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document != null && document.exists()) {
                        Product product = parseProduct(document);
                        assert product != null;
                        listener.onProductReceived(product);
                    } else {
                        listener.onError(new Exception("Product not found"));
                    }
                })
                .addOnFailureListener(listener::onError);
    }

    public void fetchFeaturedProduct(int limit, @NonNull OnProductListener listener) {
        db.collection(FirestoreConstants.COLLECTION_PRODUCTS)
                .whereEqualTo(FirestoreConstants.FIELD_STATUS, FirestoreConstants.STATUS_ACTIVE)
                .whereEqualTo(FirestoreConstants.FIELD_FEATURED, true)
                .orderBy(FirestoreConstants.FIELD_NAME, Query.Direction.ASCENDING)
                .limit(limit)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Product> products = parseProducts(snapshot);
                    if (products.isEmpty()) {
                        fetchAllActiveAndFilterFeatured(limit, listener);
                    } else {
                        listener.onProductReceived(products);
                    }
                })
                .addOnFailureListener(error -> {
                    // Nếu query thất bại (có thể do thiếu composite index), fallback sang fetch all rồi filter
                    android.util.Log.w("ProductRepository", "Query featured products failed, using fallback: " + error.getMessage());
                    fetchAllActiveAndFilterFeatured(limit, listener);
                });
    }
    
    /**
     * Fallback: Fetch tất cả active products rồi filter featured ở client
     */
    private void fetchAllActiveAndFilterFeatured(int limit, @NonNull OnProductListener listener) {
        db.collection(FirestoreConstants.COLLECTION_PRODUCTS)
                .whereEqualTo(FirestoreConstants.FIELD_STATUS, FirestoreConstants.STATUS_ACTIVE)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Product> allProducts = parseProducts(snapshot);
                    // Filter featured products ở client
                    List<Product> featuredProducts = new ArrayList<>();
                    for (Product p : allProducts) {
                        if (p.getFeatured() != null && p.getFeatured()) {
                            featuredProducts.add(p);
                        }
                    }
                    // Sort và limit
                    featuredProducts.sort((a, b) -> {
                        String nameA = a.getName() != null ? a.getName() : "";
                        String nameB = b.getName() != null ? b.getName() : "";
                        return nameA.compareToIgnoreCase(nameB);
                    });
                    if (featuredProducts.size() > limit) {
                        featuredProducts = featuredProducts.subList(0, limit);
                    }
                    listener.onProductReceived(featuredProducts);
                })
                .addOnFailureListener(listener::onError);
    }

    public void toggleFeatured(@NonNull String productId, boolean featured, @NonNull OnProductUpdateListener listener) {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put(FirestoreConstants.FIELD_FEATURED, featured);
        data.put(FirestoreConstants.FIELD_UPDATED_AT, FieldValue.serverTimestamp());
        
        db.collection(FirestoreConstants.COLLECTION_PRODUCTS)
                .document(productId)
                .update(data)
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    /**
     * Parse QuerySnapshot thành List<Product>
     */
    @NonNull
    private List<Product> parseProducts(@Nullable QuerySnapshot snapshot) {
        List<Product> products = new ArrayList<>();
        if (snapshot != null && !snapshot.isEmpty()) {
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                Product product = parseProduct(doc);
                if (product != null) {
                    products.add(product);
                }
            }
        }
        return products;
    }

    /**
     * Tạo product mới
     */
    public void createProduct(@NonNull String productId, @NonNull Map<String, Object> data, @NonNull OnProductCreateListener listener) {
        data.put(FirestoreConstants.FIELD_UPDATED_AT, FieldValue.serverTimestamp());
        if (!data.containsKey(FirestoreConstants.FIELD_CREATED_AT)) {
            data.put(FirestoreConstants.FIELD_CREATED_AT, FieldValue.serverTimestamp());
        }
        
        db.collection(FirestoreConstants.COLLECTION_PRODUCTS)
                .document(productId)
                .set(data)
                .addOnSuccessListener(aVoid -> listener.onSuccess(productId))
                .addOnFailureListener(listener::onError);
    }

    /**
     * Cập nhật product
     */
    public void updateProduct(@NonNull String productId, @NonNull Map<String, Object> data, @NonNull OnProductUpdateListener listener) {
        data.put(FirestoreConstants.FIELD_UPDATED_AT, FieldValue.serverTimestamp());
        
        db.collection(FirestoreConstants.COLLECTION_PRODUCTS)
                .document(productId)
                .update(data)
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    /**
     * Xóa product
     */
    public void deleteProduct(@NonNull String productId, @NonNull OnProductUpdateListener listener) {
        db.collection(FirestoreConstants.COLLECTION_PRODUCTS)
                .document(productId)
                .delete()
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    /**
     * Parse DocumentSnapshot thành Product object
     */
    @Nullable
    private Product parseProduct(@NonNull DocumentSnapshot doc) {
        try {
            Product product = doc.toObject(Product.class);
            if (product == null) return null;

            // Đảm bảo ID được set
            if (product.getId() == null || product.getId().isEmpty()) {
                product.setId(doc.getId());
            }

            // Fallback cho các field legacy
            if (product.getImageUrl() == null) {
                String legacyImage = doc.getString("image");
                if (legacyImage == null) {
                    legacyImage = doc.getString("imgUrl");
                }
                if (legacyImage != null) {
                    product.setImageUrl(legacyImage);
                }
            }

            return product;
        } catch (Exception e) {
            return null;
        }
    }

    // ===== CALLBACK INTERFACES =====

    /**
     * Interface cho các operations trả về List<Product>
     */
    public interface OnProductListener {
        void onProductReceived(@NonNull List<Product> products);
        void onError(@NonNull Exception error);
    }

    /**
     * Interface cho các operations trả về một Product đơn lẻ
     */
    public interface OnSingleProductListener {
        void onProductReceived(@NonNull Product product);
        void onError(@NonNull Exception error);
    }

    public interface OnProductCreateListener {
        void onSuccess(@NonNull String productId);
        void onError(@NonNull Exception error);
    }

    public interface OnProductUpdateListener {
        void onSuccess();
        void onError(@NonNull Exception error);
    }
}

