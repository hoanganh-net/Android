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
import com.pro.milkteaapp.models.Products;

import java.util.Map;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository để quản lý các thao tác với Products trong Firestore.
 * Tập trung hóa logic truy cập dữ liệu, dễ test và bảo trì.
 */
public class ProductsRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Lắng nghe realtime tất cả products, sắp xếp theo category và name
     */
    public ListenerRegistration listenAllProducts(@NonNull OnProductsListener listener) {
        Query query = db.collection(FirestoreConstants.COLLECTION_PRODUCTS)
                .orderBy(FirestoreConstants.FIELD_CATEGORY, Query.Direction.ASCENDING)
                .orderBy(FirestoreConstants.FIELD_NAME, Query.Direction.ASCENDING);

        return query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            List<Products> products = parseProducts(snapshot);
            listener.onProductsReceived(products);
        });
    }

    /**
     * Lấy tất cả products một lần (không realtime)
     */
    public void fetchAllProducts(@NonNull OnProductsListener listener) {
        db.collection(FirestoreConstants.COLLECTION_PRODUCTS)
                .orderBy(FirestoreConstants.FIELD_CATEGORY, Query.Direction.ASCENDING)
                .orderBy(FirestoreConstants.FIELD_NAME, Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Products> products = parseProducts(snapshot);
                    listener.onProductsReceived(products);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lấy products theo category
     */
    public void fetchProductsByCategory(@NonNull String category, @NonNull OnProductsListener listener) {
        db.collection(FirestoreConstants.COLLECTION_PRODUCTS)
                .whereEqualTo(FirestoreConstants.FIELD_CATEGORY, category)
                .orderBy(FirestoreConstants.FIELD_NAME, Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Products> products = parseProducts(snapshot);
                    listener.onProductsReceived(products);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lấy products theo status
     */
    public void fetchProductsByStatus(@NonNull String status, @NonNull OnProductsListener listener) {
        db.collection(FirestoreConstants.COLLECTION_PRODUCTS)
                .whereEqualTo(FirestoreConstants.FIELD_STATUS, status)
                .orderBy(FirestoreConstants.FIELD_NAME, Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Products> products = parseProducts(snapshot);
                    listener.onProductsReceived(products);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lấy một product theo ID
     */
    public void fetchProductById(@NonNull String productId, @NonNull OnProductListener listener) {
        db.collection(FirestoreConstants.COLLECTION_PRODUCTS)
                .document(productId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document != null && document.exists()) {
                        Products product = parseProduct(document);
                        listener.onProductReceived(product);
                    } else {
                        listener.onError(new Exception("Product not found"));
                    }
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Tìm kiếm products theo tên (searchableName)
     */
    public void searchProducts(@NonNull String query, @NonNull OnProductsListener listener) {
        String searchQuery = query.toLowerCase().trim();
        db.collection(FirestoreConstants.COLLECTION_PRODUCTS)
                .whereGreaterThanOrEqualTo(FirestoreConstants.FIELD_SEARCHABLE_NAME, searchQuery)
                .whereLessThanOrEqualTo(FirestoreConstants.FIELD_SEARCHABLE_NAME, searchQuery + "\uf8ff")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Products> products = parseProducts(snapshot);
                    listener.onProductsReceived(products);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lấy top products (có thể mở rộng với logic sorting)
     */
    public void fetchTopProducts(int limit, @NonNull OnProductsListener listener) {
        db.collection(FirestoreConstants.COLLECTION_PRODUCTS)
                .whereEqualTo(FirestoreConstants.FIELD_STATUS, FirestoreConstants.STATUS_ACTIVE)
                .limit(limit)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Products> products = parseProducts(snapshot);
                    listener.onProductsReceived(products);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lấy sản phẩm nổi bật (featured)
     * Nếu query với whereEqualTo featured = true thất bại (do thiếu index), sẽ fetch all active rồi filter ở client
     */
    public void fetchFeaturedProducts(int limit, @NonNull OnProductsListener listener) {
        // Thử query trực tiếp trước
        db.collection(FirestoreConstants.COLLECTION_PRODUCTS)
                .whereEqualTo(FirestoreConstants.FIELD_STATUS, FirestoreConstants.STATUS_ACTIVE)
                .whereEqualTo(FirestoreConstants.FIELD_FEATURED, true)
                .orderBy(FirestoreConstants.FIELD_NAME, Query.Direction.ASCENDING)
                .limit(limit)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Products> products = parseProducts(snapshot);
                    if (products.isEmpty()) {
                        // Nếu không có kết quả, thử fetch all active rồi filter
                        fetchAllActiveAndFilterFeatured(limit, listener);
                    } else {
                        listener.onProductsReceived(products);
                    }
                })
                .addOnFailureListener(error -> {
                    // Nếu query thất bại (có thể do thiếu composite index), fallback sang fetch all rồi filter
                    android.util.Log.w("ProductsRepository", "Query featured products failed, using fallback: " + error.getMessage());
                    fetchAllActiveAndFilterFeatured(limit, listener);
                });
    }
    
    /**
     * Fallback: Fetch tất cả active products rồi filter featured ở client
     */
    private void fetchAllActiveAndFilterFeatured(int limit, @NonNull OnProductsListener listener) {
        db.collection(FirestoreConstants.COLLECTION_PRODUCTS)
                .whereEqualTo(FirestoreConstants.FIELD_STATUS, FirestoreConstants.STATUS_ACTIVE)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Products> allProducts = parseProducts(snapshot);
                    // Filter featured products ở client
                    List<Products> featuredProducts = new ArrayList<>();
                    for (Products p : allProducts) {
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
                    listener.onProductsReceived(featuredProducts);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lắng nghe realtime sản phẩm nổi bật
     */
    public ListenerRegistration listenFeaturedProducts(int limit, @NonNull OnProductsListener listener) {
        Query query = db.collection(FirestoreConstants.COLLECTION_PRODUCTS)
                .whereEqualTo(FirestoreConstants.FIELD_STATUS, FirestoreConstants.STATUS_ACTIVE)
                .whereEqualTo(FirestoreConstants.FIELD_FEATURED, true)
                .orderBy(FirestoreConstants.FIELD_NAME, Query.Direction.ASCENDING)
                .limit(limit);

        return query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            List<Products> products = parseProducts(snapshot);
            listener.onProductsReceived(products);
        });
    }

    /**
     * Toggle trạng thái featured của sản phẩm
     */
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
     * Parse QuerySnapshot thành List<Products>
     */
    @NonNull
    private List<Products> parseProducts(@Nullable QuerySnapshot snapshot) {
        List<Products> products = new ArrayList<>();
        if (snapshot != null && !snapshot.isEmpty()) {
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                Products product = parseProduct(doc);
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
     * Parse DocumentSnapshot thành Products object
     */
    @Nullable
    private Products parseProduct(@NonNull DocumentSnapshot doc) {
        try {
            Products product = doc.toObject(Products.class);
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

    public interface OnProductsListener {
        void onProductsReceived(@NonNull List<Products> products);
        void onError(@NonNull Exception error);
    }

    public interface OnProductListener {
        void onProductReceived(@NonNull Products product);
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

