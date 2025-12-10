package com.pro.milkteaapp.models;

import com.google.firebase.Timestamp;

import java.io.Serializable;

/**
 * Entry cho điểm khách hàng thân thiết có thời gian hết hạn
 * Mỗi entry đại diện cho một lần tích lũy điểm, sẽ hết hạn sau 1 năm
 */
public class LoyaltyPointEntry implements Serializable {
    private String id;
    private String userId;
    private long points; // Số điểm tích lũy
    private Timestamp earnedAt; // Thời gian tích lũy
    private Timestamp expiresAt; // Thời gian hết hạn (earnedAt + 1 năm)
    private String orderId; // ID đơn hàng tạo ra điểm này
    private boolean expired; // Đã hết hạn chưa

    public LoyaltyPointEntry() {}

    public LoyaltyPointEntry(String userId, long points, String orderId) {
        this.userId = userId;
        this.points = points;
        this.orderId = orderId;
        this.earnedAt = Timestamp.now();
        // Hết hạn sau 1 năm
        this.expiresAt = new Timestamp(
            this.earnedAt.getSeconds() + 365L * 24 * 60 * 60,
            this.earnedAt.getNanoseconds()
        );
        this.expired = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public long getPoints() { return points; }
    public void setPoints(long points) { this.points = points; }

    public Timestamp getEarnedAt() { return earnedAt; }
    public void setEarnedAt(Timestamp earnedAt) { this.earnedAt = earnedAt; }

    public Timestamp getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Timestamp expiresAt) { this.expiresAt = expiresAt; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public boolean isExpired() { return expired; }
    public void setExpired(boolean expired) { this.expired = expired; }

    /**
     * Kiểm tra xem điểm có hết hạn chưa
     */
    public boolean checkExpired() {
        if (expiresAt == null) return false;
        Timestamp now = Timestamp.now();
        boolean isExpired = now.compareTo(expiresAt) >= 0;
        this.expired = isExpired;
        return isExpired;
    }
}

