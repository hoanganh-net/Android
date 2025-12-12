package com.pro.milkteaapp.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;
import java.io.Serializable;

public class Category implements Serializable {

    private String id;           // Lấy từ document ID
    private String name;         // Tên danh mục
    private String imageUrl;     // URL ảnh (nhập thủ công)
    private boolean active;      // Trạng thái hiển thị
    private String description;  // Mô tả (tùy chọn)

    private Timestamp createdAt; // Thời điểm tạo (Firestore Timestamp)

    // No-args constructor required by Firestore
    public Category() {
        this.description = "";
    }

    public Category(String description) {
        this.description = description;
    }

    // ✅ Constructor đầy đủ
    public Category(String id, String name, boolean active, String imageUrl, String description,
                    Timestamp createdAt) {
        this.id = id;
        this.name = name;
        this.active = active;
        this.imageUrl = imageUrl;
        this.description = description;
        this.createdAt = createdAt;
        // Thời điểm cập nhật (Firestore Timestamp)
    }

    // ===== Getters =====
    public String getId() { return id; }
    public String getName() { return name; }
    public String getImageUrl() { return imageUrl; }
    public boolean isActive() { return active; }
    public String getDescription() { return description; }

    public Timestamp getCreatedAt() { return createdAt; }

    // ===== Setters =====
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setActive(boolean active) { this.active = active; }

    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public void setDescription(String description) { this.description = description; }

    @PropertyName("image")
    public void setImage(String v) { this.imageUrl = v; }

}
