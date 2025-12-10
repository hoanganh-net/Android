package com.pro.milkteaapp.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.pro.milkteaapp.constants.FirestoreConstants;
import com.pro.milkteaapp.models.Review;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository để quản lý các thao tác với Reviews trong Firestore.
 */
public class ReviewsRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Lấy tất cả reviews của một product
     */
    public void fetchReviewsByProductId(@NonNull String productId, @NonNull OnReviewsListener listener) {
        db.collection(FirestoreConstants.COLLECTION_REVIEWS)
                .whereEqualTo(FirestoreConstants.FIELD_PRODUCT_ID_REF, productId)
                .orderBy(FirestoreConstants.FIELD_RATING, Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Review> reviews = parseReviews(snapshot);
                    listener.onReviewsReceived(reviews);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lắng nghe realtime reviews của một product
     */
    public ListenerRegistration listenReviewsByProductId(@NonNull String productId, @NonNull OnReviewsListener listener) {
        Query query = db.collection(FirestoreConstants.COLLECTION_REVIEWS)
                .whereEqualTo(FirestoreConstants.FIELD_PRODUCT_ID_REF, productId)
                .orderBy(FirestoreConstants.FIELD_RATING, Query.Direction.DESCENDING);

        return query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            List<Review> reviews = parseReviews(snapshot);
            listener.onReviewsReceived(reviews);
        });
    }

    /**
     * Lấy reviews của một order
     */
    public void fetchReviewsByOrderId(@NonNull String orderId, @NonNull OnReviewsListener listener) {
        db.collection(FirestoreConstants.COLLECTION_REVIEWS)
                .whereEqualTo(FirestoreConstants.FIELD_ORDER_ID_REF, orderId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Review> reviews = parseReviews(snapshot);
                    listener.onReviewsReceived(reviews);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lấy top reviews (có rating cao)
     */
    public void fetchTopReviews(int limit, @NonNull OnReviewsListener listener) {
        db.collection(FirestoreConstants.COLLECTION_REVIEWS)
                .orderBy(FirestoreConstants.FIELD_RATING, Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Review> reviews = parseReviews(snapshot);
                    listener.onReviewsReceived(reviews);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Parse QuerySnapshot thành List<Review>
     */
    @NonNull
    private List<Review> parseReviews(@Nullable QuerySnapshot snapshot) {
        List<Review> reviews = new ArrayList<>();
        if (snapshot != null && !snapshot.isEmpty()) {
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                Review review = parseReview(doc);
                if (review != null) {
                    reviews.add(review);
                }
            }
        }
        return reviews;
    }

    /**
     * Parse DocumentSnapshot thành Review object
     */
    @Nullable
    private Review parseReview(@NonNull DocumentSnapshot doc) {
        try {
            Review review = doc.toObject(Review.class);
            if (review == null) return null;

            // Có thể enrich thêm thông tin từ users collection nếu cần
            // String userId = doc.getString("userId");
            // if (userId != null) {
            //     // Fetch user info và set name, avatarUrl
            // }

            return review;
        } catch (Exception e) {
            return null;
        }
    }

    // ===== CALLBACK INTERFACES =====

    public interface OnReviewsListener {
        void onReviewsReceived(@NonNull List<Review> reviews);
        void onError(@NonNull Exception error);
    }
}

