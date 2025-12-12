package com.pro.milkteaapp.models;

import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;

import java.io.Serializable;
import java.util.List;

public class Voucher implements Serializable {
    private String id;
    private String code;
    private String type;
    private long amount;
    private int percent;
    @Nullable private Long maxDiscount;
    private long minOrder;
    @Nullable private Timestamp startAt;
    @Nullable private Timestamp endAt;
    private int perUserLimit;
    private boolean active;
    @Nullable private List<String> allowedChannels;
    @Nullable private String description;
    @Nullable private Long pointsRequired;

    // No-args constructor required by Firestore
    public Voucher() {
        this.amount = 0;
        this.percent = 0;
        this.minOrder = 0;
        this.perUserLimit = 0;
    }

    public Voucher(long amount, int percent, @Nullable Long maxDiscount, long minOrder, @Nullable Timestamp startAt, @Nullable Timestamp endAt, int perUserLimit, @Nullable List<String> allowedChannels, @Nullable String description, @Nullable Long pointsRequired) {
        this.amount = amount;
        this.percent = percent;
        this.maxDiscount = maxDiscount;
        this.minOrder = minOrder;
        this.startAt = startAt;
        this.endAt = endAt;
        this.perUserLimit = perUserLimit;
        this.allowedChannels = allowedChannels;
        this.description = description;
        this.pointsRequired = pointsRequired;
    }

    // ===== getters/setters =====
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCode() { return code; }
    public String getType() { return type; }
    public long getAmount() { return amount; }
    public int getPercent() { return percent; }
    @Nullable public Long getMaxDiscount() { return maxDiscount; }
    public long getMinOrder() { return minOrder; }
    @Nullable public Timestamp getStartAt() { return startAt; }
    @Nullable public Timestamp getEndAt() { return endAt; }
    public int getPerUserLimit() { return perUserLimit; }
    public boolean isActive() { return active; }
    @Nullable public List<String> getAllowedChannels() { return allowedChannels; }
    @Nullable public String getDescription() { return description; }
    @Nullable public Long getPointsRequired() { return pointsRequired; }

    public void setCode(String code) { this.code = code; }
    public void setType(String type) { this.type = type; }

    public void setActive(boolean active) { this.active = active; }

}
