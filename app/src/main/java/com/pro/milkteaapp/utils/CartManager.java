package com.pro.milkteaapp.utils;

import androidx.annotation.NonNull;

import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.models.ComboCartItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Quản lý giỏ hàng toàn cục.
 * Singleton pattern để tránh memory leak từ static collections.
 * Có thể clear khi cần (ví dụ: khi logout).
 */
public class CartManager {
    private static CartManager instance;
    
    private final List<CartItem> cartItems = new ArrayList<>();
    private final List<ComboCartItem> comboCartItems = new ArrayList<>();
    
    private CartManager() {
        // Private constructor for singleton
    }
    
    @NonNull
    public static synchronized CartManager getInstance() {
        if (instance == null) {
            instance = new CartManager();
        }
        return instance;
    }
    
    // ===== Cart Items =====
    
    @NonNull
    public List<CartItem> getCartItems() {
        return cartItems;
    }
    
    public void addCartItem(@NonNull CartItem item) {
        cartItems.add(item);
    }
    
    public void removeCartItem(int position) {
        if (position >= 0 && position < cartItems.size()) {
            cartItems.remove(position);
        }
    }
    
    public void clearCartItems() {
        cartItems.clear();
    }
    
    public int getCartItemsCount() {
        return cartItems.size();
    }
    
    // ===== Combo Cart Items =====
    
    @NonNull
    public List<ComboCartItem> getComboCartItems() {
        return comboCartItems;
    }
    
    public void addComboCartItem(@NonNull ComboCartItem item) {
        comboCartItems.add(item);
    }
    
    public void removeComboCartItem(int position) {
        if (position >= 0 && position < comboCartItems.size()) {
            comboCartItems.remove(position);
        }
    }
    
    public void clearComboCartItems() {
        comboCartItems.clear();
    }
    
    public int getComboCartItemsCount() {
        return comboCartItems.size();
    }
    
    // ===== Combined Operations =====
    
    public boolean isEmpty() {
        return cartItems.isEmpty() && comboCartItems.isEmpty();
    }
    
    public void clearAll() {
        cartItems.clear();
        comboCartItems.clear();
    }
    
    /**
     * Tìm cart item đã tồn tại (theo equals)
     * @return index nếu tìm thấy, -1 nếu không
     */
    public int findCartItem(@NonNull CartItem item) {
        for (int i = 0; i < cartItems.size(); i++) {
            if (item.equals(cartItems.get(i))) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Tìm combo cart item đã tồn tại (theo equals)
     * @return index nếu tìm thấy, -1 nếu không
     */
    public int findComboCartItem(@NonNull ComboCartItem item) {
        for (int i = 0; i < comboCartItems.size(); i++) {
            if (item.equals(comboCartItems.get(i))) {
                return i;
            }
        }
        return -1;
    }
}

