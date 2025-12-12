package com.pro.milkteaapp.models;

import java.io.Serializable;
public class LoyaltyPointEntry implements Serializable {
    private String id;
    private String userId;
    private long points; // Số điểm tích lũy
    private String orderId; // ID đơn hàng tạo ra điểm này

    // No-args constructor required by Firestore
    public LoyaltyPointEntry() {
    }

    public LoyaltyPointEntry(String userId, long points, String orderId) {
        this.userId = userId;
        this.points = points;
        this.orderId = orderId;

    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public long getPoints() { return points; }
    public void setPoints(long points) { this.points = points; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

}

