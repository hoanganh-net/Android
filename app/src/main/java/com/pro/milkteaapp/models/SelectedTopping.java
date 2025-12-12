package com.pro.milkteaapp.models;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Topping đã chọn (đưa vào giỏ/lưu order)
 */
public record SelectedTopping(String id, String name, long price) implements Serializable {

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("price", price);
        return m;
    }

}
