package com.pro.milkteaapp.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.io.Serializable;
import java.util.Map;

public class Order implements Serializable {

    public static final String STATUS_PENDING   = "PENDING";
    public static final String STATUS_FINISHED  = "FINISHED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private String id;
    private String userId;
    private String status;
    private double finalTotal;
    private double subtotal;
    private double discount;
    private double shippingFee;
    private String address;
    private String paymentMethod;
    private String paymentStatus;  // PENDING, SUCCESS, FAILED, CANCELLED
    private String transactionId;  // ID giao dịch từ payment gateway
    private Timestamp createdAt;
    private Timestamp finishedAt;
    private Timestamp cancelledAt; // dạng Anh
    private Timestamp canceledAt;  // dạng Mỹ (để tương thích)

    // Thông tin người nhận
    private String receiverName;
    private String receiverPhone;
    private String addressDisplay;

    // Thông tin đánh giá (rating/review)
    private Double rating;
    private String review;
    private String userName;      // denormalized từ User
    private String userAvatar;   // denormalized từ User
    private String ratedBy;      // userId của người đánh giá
    private Timestamp ratedAt;
    private String photoUrl;     // URL ảnh đánh giá (nếu có)

    public Order() {
    }

    @NonNull
    @SuppressWarnings("unchecked")
    public static Order from(@NonNull DocumentSnapshot d) {
        Order o = new Order();
        o.id = d.getId();
        o.userId = d.getString("userId");
        o.status = d.getString("status");

        Double ft = d.getDouble("finalTotal");
        o.finalTotal = ft == null ? 0 : ft;

        Double st = d.getDouble("subtotal");
        o.subtotal = st == null ? 0 : st;

        Double dc = d.getDouble("discount");
        o.discount = dc == null ? 0 : dc;

        Double sf = d.getDouble("shippingFee");
        o.shippingFee = sf == null ? 0 : sf;

        o.address = d.getString("address");
        o.paymentMethod = d.getString("paymentMethod");
        o.paymentStatus = d.getString("paymentStatus");
        o.transactionId = d.getString("transactionId");

        o.createdAt   = d.getTimestamp("createdAt");
        o.finishedAt  = d.getTimestamp("finishedAt");
        o.cancelledAt = d.getTimestamp("cancelledAt");
        if (o.cancelledAt == null) {
            o.cancelledAt = d.getTimestamp("canceledAt"); // fallback cho dữ liệu cũ
        }

        Object addrObj = d.get("addressObj");
        if (addrObj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) addrObj;
            Object nm = map.get("fullName");
            Object ph = map.get("phone");
            Object disp = map.get("display");
            o.receiverName   = nm   == null ? "" : String.valueOf(nm);
            o.receiverPhone  = ph   == null ? "" : String.valueOf(ph);
            o.addressDisplay = disp == null ? "" : String.valueOf(disp);
        }

        // Parse rating/review fields
        o.rating = d.getDouble("rating");
        o.review = d.getString("review");
        o.userName = d.getString("userName");
        o.userAvatar = d.getString("userAvatar");
        o.ratedBy = d.getString("ratedBy");
        o.ratedAt = d.getTimestamp("ratedAt");
        o.photoUrl = d.getString("photoUrl");

        return o;
    }

    // ===== getters =====
    @NonNull public String getId() { return id == null ? "" : id; }
    @Nullable public String getUserId() { return userId; }
    @Nullable public String getStatus() { return status; }
    public double getFinalTotal() { return finalTotal; }
    public double getTotal() { return finalTotal; }
    public double getSubtotal() { return subtotal; }

    @Nullable public String getAddress() { return address; }

    @Nullable public Timestamp getCreatedAt() { return createdAt; }
    @Nullable public Timestamp getFinishedAt() { return finishedAt; }
    @Nullable public Timestamp getCancelledAt() {
        return (cancelledAt != null) ? cancelledAt : canceledAt;
    }

    @NonNull public String getReceiverName()  { return receiverName == null ? "" : receiverName; }
    @NonNull public String getReceiverPhone() { return receiverPhone == null ? "" : receiverPhone; }
    @NonNull public String getAddressDisplay() {
        if (addressDisplay != null && !addressDisplay.isEmpty()) return addressDisplay;
        return address == null ? "" : address;
    }

    // Rating/Review getters
    @Nullable public Double getRating() { return rating; }
    @Nullable public String getReview() { return review; }
    @Nullable public String getUserName() { return userName; }
    @Nullable public String getUserAvatar() { return userAvatar; }
    @Nullable public String getRatedBy() { return ratedBy; }
    @Nullable public Timestamp getRatedAt() { return ratedAt; }

    // ===== setters =====
    public void setId(@NonNull String id) { this.id = id; }
    public void setStatus(@Nullable String status) { this.status = status; }

    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }

    public void setAddress(@Nullable String address) { this.address = address; }

    public void setCreatedAt(@Nullable Timestamp createdAt) { this.createdAt = createdAt; }

}
