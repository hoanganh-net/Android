package com.pro.milkteaapp.models;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.PropertyName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Model cho Combo sản phẩm
 * Combo chứa nhiều sản phẩm với giá ưu đãi
 */
public class Combo implements Serializable {
    private String id;
    private String name;
    private String description;
    private Double price; // Giá combo (thường thấp hơn tổng giá các sản phẩm riêng lẻ)
    private String imageUrl;
    private List<String> productIds; // Danh sách ID sản phẩm trong combo
    private String status; // active / inactive
    private Date createdAt;

    // Thông tin bổ sung
    private Double originalPrice; // Tổng giá nếu mua riêng lẻ
    private Integer discountPercent; // % giảm giá

    // No-args constructor required by Firestore
    public Combo() {
        this.productIds = new ArrayList<>();
    }

    public Combo(Double originalPrice, Integer discountPercent) {
        this.originalPrice = originalPrice;
        this.discountPercent = discountPercent;
        this.productIds = new ArrayList<>();
    }

    public Combo(String id, String name, String description, Double price,
                 String imageUrl, List<String> productIds, String status,
                 Double originalPrice, Integer discountPercent) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.imageUrl = imageUrl;
        this.productIds = productIds != null ? new ArrayList<>(productIds) : new ArrayList<>();
        this.status = status;
        this.originalPrice = originalPrice;
        this.discountPercent = discountPercent;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Double getPrice() { return price; }
    public String getImageUrl() { return imageUrl; }
    public List<String> getProductIds() { return new ArrayList<>(productIds); }
    public String getStatus() { return status; }

    public Date getCreatedAt() { return createdAt; }

    public Double getOriginalPrice() { return originalPrice; }
    public Integer getDiscountPercent() { return discountPercent; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }

    public void setPrice(Double price) { this.price = price; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public void setStatus(String status) { this.status = status; }

    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public void setDescription(String description) { this.description = description; }

    public void setProductIds(List<String> productIds) {
        this.productIds = productIds != null ? new ArrayList<>(productIds) : new ArrayList<>();
    }

    public void setOriginalPrice(Double originalPrice) { this.originalPrice = originalPrice; }

    public void setDiscountPercent(Integer discountPercent) { this.discountPercent = discountPercent; }

    /**
     * Parse từ DocumentSnapshot
     */
    public static Combo from(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        
        Combo combo = doc.toObject(Combo.class);
        if (combo == null) return null;
        
        // Đảm bảo ID được set từ docId
        if (combo.getId() == null || combo.getId().isEmpty()) {
            combo.setId(doc.getId());
        }
        
        return combo;
    }

    /**
     * Kiểm tra combo có đang active không
     */
    public boolean isActive() {
        return "active".equalsIgnoreCase(status);
    }

    // Alias cho tương thích
    @PropertyName("title")
    public void setTitle(String v) { this.name = v; }

    @PropertyName("image")
    public void setImage(String v) { this.imageUrl = v; }

}

