package com.pro.milkteaapp.models;

import androidx.annotation.NonNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CartItem cho Combo sản phẩm
 * Combo chứa nhiều sản phẩm, mỗi sản phẩm có thể có size, sugar và toppings riêng
 */
public class ComboCartItem implements Serializable {
    @Serial
    private static final long serialVersionUID = 4L;

    private static final long DELTA_MEDIUM = 3000L;
    private static final long DELTA_LARGE = 6000L;

    private final Combo combo;
    private int quantity;
    private long totalPrice;
    private final List<ComboProductOption> productOptions; // Tùy chọn cho từng sản phẩm trong combo

    public ComboCartItem(@NonNull Combo combo, int quantity, List<ComboProductOption> productOptions) {
        this.combo = Objects.requireNonNull(combo, "combo không được null");
        this.quantity = Math.max(1, quantity);
        this.productOptions = productOptions != null ? new ArrayList<>(productOptions) : new ArrayList<>();
        recalcTotal();
    }

    // ===== Getter/Setter =====
    public Combo getCombo() { return combo; }
    public int getQuantity() { return quantity; }
    public long getTotalPrice() { return totalPrice; }
    public List<ComboProductOption> getProductOptions() {
        return productOptions != null ? new ArrayList<>(productOptions) : new ArrayList<>();
    }

    public void increaseQuantity(int added) {
        if (added > 0) {
            this.quantity += added;
            recalcTotal();
        }
    }

    public void increaseQuantity() {
        increaseQuantity(1);
    }

    public void decreaseQuantity() {
        if (this.quantity > 1) {
            this.quantity--;
            recalcTotal();
        }
    }

    // ===== Giá =====
    public long getBasePrice() {
        Double p = combo.getPrice();
        return p == null ? 0L : p.longValue();
    }

    public double getPrice() {
        Double p = combo.getPrice();
        return p == null ? 0.0 : p;
    }

    /** Giá 1 combo (bao gồm size của từng sản phẩm và topping từ tất cả các sản phẩm trong combo) */
    public long getUnitPrice() {
        long unit = getBasePrice();
        
        // Phụ thu theo size của từng sản phẩm trong combo
        if (productOptions != null) {
            for (ComboProductOption option : productOptions) {
                if (option != null) {
                    String size = option.getSize();
                    String s = normalize(size);
                    if ("vừa".equals(s) || "medium".equals(s)) {
                        unit += DELTA_MEDIUM;
                    } else if ("lớn".equals(s) || "large".equals(s)) {
                        unit += DELTA_LARGE;
                    }
                    
                    // Cộng giá của toppings
                    if (option.getToppings() != null) {
                        for (SelectedTopping topping : option.getToppings()) {
                            if (topping != null && topping.price() > 0) {
                                unit += topping.price();
                            }
                        }
                    }
                }
            }
        }
        
        return unit;
    }

    private static String normalize(String v) {
        return v == null ? "" : v.trim().toLowerCase(java.util.Locale.getDefault());
    }

    private void recalcTotal() {
        this.totalPrice = getUnitPrice() * Math.max(1, this.quantity);
    }

    // ===== Thông tin cầu nối =====
    public String getComboId() { return combo.getId(); }
    public String getName() { return combo.getName(); }
    public String getImageUrl() { return combo.getImageUrl(); }

    /** Map lưu vào order.items */
    public Map<String, Object> toOrderItemMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("comboId", getComboId());
        m.put("name", getName());
        m.put("price", getBasePrice());
        m.put("qty", getQuantity());
        m.put("imageUrl", getImageUrl());
        m.put("type", "combo"); // Đánh dấu là combo
        m.put("unitPrice", getUnitPrice());
        m.put("lineTotal", getTotalPrice());
        
        // Thêm thông tin chi tiết các sản phẩm trong combo
        List<Map<String, Object>> productsList = new ArrayList<>();
        for (ComboProductOption option : getProductOptions()) {
            Map<String, Object> productMap = getStringObjectMap(option);
            productsList.add(productMap);
        }
        m.put("products", productsList);
        
        return m;
    }

    @NonNull
    private Map<String, Object> getStringObjectMap(ComboProductOption option) {
        Map<String, Object> productMap = new HashMap<>();
        productMap.put("productId", option.getProductId());
        productMap.put("productName", option.getProductName());
        productMap.put("size", option.getSize());
        productMap.put("sugar", option.getSugar());

        // Toppings
        List<Map<String, Object>> toppingsList = new ArrayList<>();
        for (SelectedTopping topping : option.getToppings()) {
            Map<String, Object> toppingMap = new HashMap<>();
            toppingMap.put("id", topping.id());
            toppingMap.put("name", topping.name());
            toppingMap.put("price", topping.price());
            toppingsList.add(toppingMap);
        }
        productMap.put("toppings", toppingsList);
        return productMap;
    }

    // Hợp nhất theo comboId (không phân biệt theo size vì mỗi sản phẩm có size riêng)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ComboCartItem that)) return false;
        return Objects.equals(getComboId(), that.getComboId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getComboId());
    }
}

