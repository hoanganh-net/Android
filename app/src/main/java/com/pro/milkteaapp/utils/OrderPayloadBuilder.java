package com.pro.milkteaapp.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.FieldValue;
import com.pro.milkteaapp.models.Address;
import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.models.CheckoutInfo;
import com.pro.milkteaapp.models.ComboCartItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class để build order payload từ cart items và checkout info.
 * Tách logic build order payload ra khỏi Fragment/Activity.
 */
public class OrderPayloadBuilder {
    
    /**
     * Build payload đơn hàng theo CheckoutInfo (đã có giảm giá/ship/tổng).
     */
    @NonNull
    public static Map<String, Object> buildOrderPayload(
            @NonNull String userId,
            @NonNull List<CartItem> cartItems,
            @NonNull List<ComboCartItem> comboCartItems,
            @NonNull CheckoutInfo info) {
        
        List<Map<String, Object>> itemMaps = new ArrayList<>();
        
        // Thêm CartItem vào items array
        for (CartItem ci : cartItems) {
            itemMaps.add(ci.toOrderItemMap());
        }
        
        // Thêm ComboCartItem vào items array
        for (ComboCartItem comboItem : comboCartItems) {
            itemMaps.add(comboItem.toOrderItemMap());
        }
        
        Map<String, Object> order = new HashMap<>();
        order.put("userId", userId);
        order.put("status", "PENDING");
        order.put("items", itemMaps);
        
        order.put("subtotal", info.subtotal);
        order.put("discount", info.discount);
        order.put("shippingFee", info.shippingFee);
        order.put("finalTotal", info.grandTotal);
        
        final String shippingAddr = info.address.trim();
        order.put("address", shippingAddr);
        order.put("shippingAddress", shippingAddr);
        
        Map<String, Object> addressObj = buildAddressObject(info.addressObj, shippingAddr);
        order.put("addressObj", addressObj);
        
        order.put("paymentMethod", info.paymentMethod);
        order.put("voucherCode", info.voucherCode);
        order.put("shippingLabel", info.shippingLabel);
        
        order.put("createdAt", FieldValue.serverTimestamp());
        return order;
    }
    
    @NonNull
    private static Map<String, Object> buildAddressObject(@Nullable Address address, @NonNull String fallback) {
        Map<String, Object> addressObj = new HashMap<>();
        if (address != null) {
            addressObj.put("fullName", address.getFullName());
            addressObj.put("phone", address.getPhone());
            addressObj.put("line1", address.getLine1());
            addressObj.put("line2", address.getLine2());
            addressObj.put("city", address.getCity());
            addressObj.put("province", address.getProvince());
            addressObj.put("postalCode", address.getPostalCode());
            addressObj.put("display", address.displayLine());
        } else {
            addressObj.put("display", fallback);
        }
        return addressObj;
    }
}

