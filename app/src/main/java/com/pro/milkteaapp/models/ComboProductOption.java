package com.pro.milkteaapp.models;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Tùy chọn cho từng sản phẩm trong combo
 * Mỗi sản phẩm có thể có size, sugar và toppings riêng
 * Đá được phục vụ riêng mặc định, không cần chọn
 */
public class ComboProductOption implements Serializable {
    private static final long serialVersionUID = 2L;
    
    private String productId;
    private String productName;
    private String size; // Small / Medium / Large (hoặc Nhỏ / Vừa / Lớn)
    private String sugar;
    private List<SelectedTopping> toppings;
    
    public ComboProductOption() {
        this.size = "Nhỏ"; // Mặc định Nhỏ
        this.toppings = new ArrayList<>();
    }
    
    public ComboProductOption(@NonNull String productId, @NonNull String productName) {
        this.productId = productId;
        this.productName = productName;
        this.size = "Nhỏ"; // Mặc định Nhỏ
        this.sugar = "";
        this.toppings = new ArrayList<>();
    }
    
    public ComboProductOption(@NonNull String productId, @NonNull String productName,
                             String size, String sugar, List<SelectedTopping> toppings) {
        this.productId = productId;
        this.productName = productName;
        this.size = size != null ? size : "Nhỏ";
        this.sugar = sugar != null ? sugar : "";
        this.toppings = toppings != null ? new ArrayList<>(toppings) : new ArrayList<>();
    }
    
    // Getters
    public String getProductId() { return productId != null ? productId : ""; }
    public String getProductName() { return productName != null ? productName : ""; }
    public String getSize() { return size != null ? size : "Nhỏ"; }
    public String getSugar() { return sugar != null ? sugar : ""; }
    public List<SelectedTopping> getToppings() {
        return toppings != null ? new ArrayList<>(toppings) : new ArrayList<>();
    }
    
    // Setters
    public void setProductId(String productId) { this.productId = productId; }
    public void setProductName(String productName) { this.productName = productName; }
    public void setSize(String size) { this.size = size != null ? size : "Nhỏ"; }
    public void setSugar(String sugar) { this.sugar = sugar != null ? sugar : ""; }
    public void setToppings(List<SelectedTopping> toppings) {
        this.toppings = toppings != null ? new ArrayList<>(toppings) : new ArrayList<>();
    }
}

