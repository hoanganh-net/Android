package com.pro.milkteaapp.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.pro.milkteaapp.constants.FirestoreConstants;
import com.pro.milkteaapp.models.Order;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Repository để quản lý các thao tác với Orders trong Firestore.
 */
public class OrdersRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Lấy tất cả orders của một user
     */
    public void fetchOrdersByUserId(@NonNull String userId, @NonNull OnOrdersListener listener) {
        db.collection(FirestoreConstants.COLLECTION_ORDERS)
                .whereEqualTo(FirestoreConstants.FIELD_USER_ID, userId)
                .orderBy(FirestoreConstants.FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Order> orders = parseOrders(snapshot);
                    listener.onOrdersReceived(orders);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lắng nghe realtime orders của một user
     */
    public ListenerRegistration listenOrdersByUserId(@NonNull String userId, @NonNull OnOrdersListener listener) {
        Query query = db.collection(FirestoreConstants.COLLECTION_ORDERS)
                .whereEqualTo(FirestoreConstants.FIELD_USER_ID, userId)
                .orderBy(FirestoreConstants.FIELD_CREATED_AT, Query.Direction.DESCENDING);

        return query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            List<Order> orders = parseOrders(snapshot);
            listener.onOrdersReceived(orders);
        });
    }

    /**
     * Lấy orders theo status
     */
    public void fetchOrdersByStatus(@NonNull String status, @NonNull OnOrdersListener listener) {
        db.collection(FirestoreConstants.COLLECTION_ORDERS)
                .whereEqualTo(FirestoreConstants.FIELD_ORDER_STATUS, status)
                .orderBy(FirestoreConstants.FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Order> orders = parseOrders(snapshot);
                    listener.onOrdersReceived(orders);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lấy orders của user theo status
     */
    public void fetchUserOrdersByStatus(@NonNull String userId, @NonNull String status, @NonNull OnOrdersListener listener) {
        db.collection(FirestoreConstants.COLLECTION_ORDERS)
                .whereEqualTo(FirestoreConstants.FIELD_USER_ID, userId)
                .whereEqualTo(FirestoreConstants.FIELD_ORDER_STATUS, status)
                .orderBy(FirestoreConstants.FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Order> orders = parseOrders(snapshot);
                    listener.onOrdersReceived(orders);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lấy một order theo ID
     */
    public void fetchOrderById(@NonNull String orderId, @NonNull OnOrderListener listener) {
        db.collection(FirestoreConstants.COLLECTION_ORDERS)
                .document(orderId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document != null && document.exists()) {
                        Order order = parseOrder(document);
                        if (order != null) {
                            listener.onOrderReceived(order);
                        } else {
                            listener.onError(new Exception("Failed to parse order"));
                        }
                    } else {
                        listener.onError(new Exception("Order not found"));
                    }
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lấy tất cả orders (dành cho admin)
     */
    public void fetchAllOrders(@NonNull OnOrdersListener listener) {
        db.collection(FirestoreConstants.COLLECTION_ORDERS)
                .orderBy(FirestoreConstants.FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Order> orders = parseOrders(snapshot);
                    listener.onOrdersReceived(orders);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lắng nghe realtime tất cả orders (dành cho admin)
     */
    public ListenerRegistration listenAllOrders(@NonNull OnOrdersListener listener) {
        Query query = db.collection(FirestoreConstants.COLLECTION_ORDERS)
                .orderBy(FirestoreConstants.FIELD_CREATED_AT, Query.Direction.DESCENDING);

        return query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            List<Order> orders = parseOrders(snapshot);
            listener.onOrdersReceived(orders);
        });
    }

    /**
     * Lấy một lần orders có rating (dành cho admin reviews)
     * Note: Firestore yêu cầu composite index cho multiple orderBy, nên chỉ orderBy ratedAt
     */
    public void fetchOrdersWithRating(int limit, @NonNull OnOrdersListener listener) {
        Query query = db.collection(FirestoreConstants.COLLECTION_ORDERS)
                .whereGreaterThan("rating", 0)
                .orderBy("ratedAt", Query.Direction.DESCENDING)
                .limit(limit);

        query.get()
                .addOnSuccessListener(snapshot -> {
                    List<Order> orders = parseOrders(snapshot);
                    listener.onOrdersReceived(orders);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Lắng nghe realtime orders có rating (dành cho admin reviews)
     * Note: Firestore yêu cầu composite index cho multiple orderBy, nên chỉ orderBy ratedAt
     */
    public ListenerRegistration listenOrdersWithRating(int limit, @NonNull OnOrdersListener listener) {
        Query query = db.collection(FirestoreConstants.COLLECTION_ORDERS)
                .whereGreaterThan("rating", 0)
                .orderBy("ratedAt", Query.Direction.DESCENDING)
                .limit(limit);

        return query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            List<Order> orders = parseOrders(snapshot);
            listener.onOrdersReceived(orders);
        });
    }

    /**
     * Lấy orders trong khoảng thời gian (dành cho statistics)
     * @param startDate Ngày bắt đầu
     * @param endDate Ngày kết thúc
     * @param userId User ID để filter (null hoặc "ALL" để lấy tất cả)
     */
    public void fetchOrdersByDateRange(@NonNull Date startDate, @NonNull Date endDate, @Nullable String userId, @NonNull OnOrdersListener listener) {
        Timestamp startTs = new Timestamp(startDate);
        Timestamp endTs = new Timestamp(endDate);

        Query query = db.collection(FirestoreConstants.COLLECTION_ORDERS)
                .whereGreaterThanOrEqualTo(FirestoreConstants.FIELD_CREATED_AT, startTs)
                .whereLessThanOrEqualTo(FirestoreConstants.FIELD_CREATED_AT, endTs);

        if (userId != null && !userId.isEmpty() && !"ALL".equals(userId)) {
            query = query.whereEqualTo(FirestoreConstants.FIELD_USER_ID, userId);
        }

        query.get()
                .addOnSuccessListener(snapshot -> {
                    List<Order> orders = parseOrders(snapshot);
                    listener.onOrdersReceived(orders);
                })
                .addOnFailureListener(listener::onError);
    }

    /**
     * Parse QuerySnapshot thành List<Order>
     */
    @NonNull
    private List<Order> parseOrders(@Nullable QuerySnapshot snapshot) {
        List<Order> orders = new ArrayList<>();
        if (snapshot != null && !snapshot.isEmpty()) {
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                Order order = parseOrder(doc);
                if (order != null) {
                    orders.add(order);
                }
            }
        }
        return orders;
    }

    /**
     * Cập nhật order với các field bất kỳ
     */
    public void updateOrder(@NonNull String orderId, @NonNull Map<String, Object> updates, @NonNull OnOrderUpdateListener listener) {
        db.collection(FirestoreConstants.COLLECTION_ORDERS)
                .document(orderId)
                .update(updates)
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    /**
     * Cập nhật status của order
     */
    public void updateOrderStatus(@NonNull String orderId, @NonNull String status, @NonNull OnOrderUpdateListener listener) {
        db.collection(FirestoreConstants.COLLECTION_ORDERS)
                .document(orderId)
                .update(FirestoreConstants.FIELD_ORDER_STATUS, status)
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    /**
     * Hủy order (cập nhật status, cancelReason, canceledAt)
     */
    public void cancelOrder(@NonNull String orderId, @Nullable String cancelReason, @NonNull OnOrderUpdateListener listener) {
        db.collection(FirestoreConstants.COLLECTION_ORDERS)
                .document(orderId)
                .update(
                        FirestoreConstants.FIELD_ORDER_STATUS, Order.STATUS_CANCELLED,
                        "cancelReason", cancelReason != null ? cancelReason : "",
                        FirestoreConstants.FIELD_CANCELLED_AT, FieldValue.serverTimestamp()
                )
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    /**
     * Parse DocumentSnapshot thành Order object
     */
    @Nullable
    private Order parseOrder(@NonNull DocumentSnapshot doc) {
        try {
            return Order.from(doc);
        } catch (Exception e) {
            return null;
        }
    }

    // ===== CALLBACK INTERFACES =====

    public interface OnOrdersListener {
        void onOrdersReceived(@NonNull List<Order> orders);
        void onError(@NonNull Exception error);
    }

    public interface OnOrderListener {
        void onOrderReceived(@NonNull Order order);
        void onError(@NonNull Exception error);
    }

    public interface OnOrderUpdateListener {
        void onSuccess();
        void onError(@NonNull Exception error);
    }
}

