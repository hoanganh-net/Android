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
    private String searchableName;
    private String createdByUid;
    private Date createdAt;
    private Date updatedAt;
    
    // Thông tin bổ sung
    private Double originalPrice; // Tổng giá nếu mua riêng lẻ
    private Integer discountPercent; // % giảm giá

    public Combo() {
        this.productIds = new ArrayList<>();
    }

    public Combo(String id, String name, String description, Double price,
                 String imageUrl, List<String> productIds, String status,
                 String searchableName, String createdByUid) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.imageUrl = imageUrl;
        this.productIds = productIds != null ? new ArrayList<>(productIds) : new ArrayList<>();
        this.status = status;
        this.searchableName = searchableName;
        this.createdByUid = createdByUid;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Double getPrice() { return price; }
    public String getImageUrl() { return imageUrl; }
    public List<String> getProductIds() { return productIds != null ? new ArrayList<>(productIds) : new ArrayList<>(); }
    public String getStatus() { return status; }
    public String getSearchableName() { return searchableName; }
    public String getCreatedByUid() { return createdByUid; }
    public Date getCreatedAt() { return createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public Double getOriginalPrice() { return originalPrice; }
    public Integer getDiscountPercent() { return discountPercent; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setPrice(Double price) { this.price = price; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setProductIds(List<String> productIds) { 
        this.productIds = productIds != null ? new ArrayList<>(productIds) : new ArrayList<>(); 
    }
    public void setStatus(String status) { this.status = status; }
    public void setSearchableName(String searchableName) { this.searchableName = searchableName; }
    public void setCreatedByUid(String createdByUid) { this.createdByUid = createdByUid; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
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
     * Tính số lượng sản phẩm trong combo
     */
    public int getProductCount() {
        return productIds != null ? productIds.size() : 0;
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

    @PropertyName("imgUrl")
    public void setImgUrl(String v) { this.imageUrl = v; }

    @PropertyName("image")
    public void setImage(String v) { this.imageUrl = v; }
}

