package com.pro.milkteaapp.utils;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.constants.FirestoreConstants;
import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.models.CheckoutInfo;
import com.pro.milkteaapp.models.ComboCartItem;
import com.pro.milkteaapp.models.Product;
import com.pro.milkteaapp.models.SelectedTopping;
import com.pro.milkteaapp.payment.PaymentService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Helper class để tạo order trong Firestore.
 * Tách logic tạo order ra khỏi Activity để giảm độ phức tạp.
 */
public class OrderCreator {
    
    private static final String TAG = "OrderCreator";
    
    private final Context context;
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    
    public OrderCreator(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }
    
    /**
     * Tạo order trong Firestore.
     * 
     * @param cartItems Danh sách cart items
     * @param comboCartItems Danh sách combo cart items
     * @param checkoutInfo Thông tin checkout
     * @param transactionId Transaction ID nếu có (cho online payment)
     * @param onSuccess Callback khi thành công
     * @param onError Callback khi lỗi
     */
    public void createOrder(
            @NonNull List<CartItem> cartItems,
            @NonNull List<ComboCartItem> comboCartItems,
            @NonNull CheckoutInfo checkoutInfo,
            @Nullable String transactionId,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<Exception> onError) {
        
        if (cartItems.isEmpty() && comboCartItems.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.empty_cart_message), Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (auth.getCurrentUser() == null) {
            Toast.makeText(context, context.getString(R.string.not_logged_in), Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Lấy userId hiệu lực
        String effectiveUid = getEffectiveUserId();
        if (TextUtils.isEmpty(effectiveUid)) {
            onError.accept(new IllegalStateException("Cannot get user ID"));
            return;
        }
        
        // Validate address
        String address = checkoutInfo.getAddress();
        if (TextUtils.isEmpty(address)) {
            Toast.makeText(context, context.getString(R.string.please_select_address), Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Build order payload
        Map<String, Object> order = buildOrderPayload(
                effectiveUid,
                cartItems,
                comboCartItems,
                checkoutInfo,
                transactionId
        );
        
        // Create order document
        String orderId = db.collection(FirestoreConstants.COLLECTION_ORDERS).document().getId();
        WriteBatch batch = db.batch();
        batch.set(db.collection(FirestoreConstants.COLLECTION_ORDERS).document(orderId), order);
        
        batch.commit()
                .addOnSuccessListener(v -> {
                    Log.d(TAG, "Order created successfully: " + orderId);
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create order", e);
                    onError.accept(e);
                });
    }
    
    @NonNull
    private String getEffectiveUserId() {
        String customUid = null;
        try {
            customUid = new SessionManager(context).getUid();
        } catch (Throwable e) {
            Log.w(TAG, "Failed to get UID from SessionManager: " + e.getMessage());
        }
        if (TextUtils.isEmpty(customUid) && auth.getCurrentUser() != null) {
            return auth.getCurrentUser().getUid();
        }
        return customUid != null ? customUid : "";
    }
    
    @NonNull
    private Map<String, Object> buildOrderPayload(
            @NonNull String userId,
            @NonNull List<CartItem> cartItems,
            @NonNull List<ComboCartItem> comboCartItems,
            @NonNull CheckoutInfo checkoutInfo,
            @Nullable String transactionId) {
        
        Map<String, Object> order = new HashMap<>();
        String orderId = db.collection(FirestoreConstants.COLLECTION_ORDERS).document().getId();
        
        order.put("orderId", orderId);
        order.put("userId", userId);
        order.put("name", null);
        order.put("phone", null);
        order.put("address", checkoutInfo.getAddress());
        order.put("subtotal", (long) checkoutInfo.getSubtotal());
        order.put("discount", (long) checkoutInfo.getDiscount());
        order.put("shippingFee", (long) checkoutInfo.getShippingFee());
        order.put("finalTotal", (long) checkoutInfo.getGrandTotal());
        
        PaymentService paymentService = PaymentService.getInstance(context);
        order.put("paymentMethod", checkoutInfo.getPaymentMethod());
        order.put("paymentStatus", paymentService.requiresOnlinePayment(checkoutInfo.getPaymentMethod())
                ? (transactionId != null ? "SUCCESS" : "PENDING")
                : "PENDING");
        
        if (transactionId != null) {
            order.put("transactionId", transactionId);
        }
        
        order.put("shippingLabel", checkoutInfo.getShippingLabel());
        order.put("voucherCode", TextUtils.isEmpty(checkoutInfo.getVoucherCode()) ? null : checkoutInfo.getVoucherCode());
        order.put("rewardVoucherCode", TextUtils.isEmpty(checkoutInfo.getRewardVoucherCode()) ? null : checkoutInfo.getRewardVoucherCode());
        order.put("status", "PENDING");
        order.put("createdAt", FieldValue.serverTimestamp());
        
        // Build items list
        List<Map<String, Object>> itemsList = buildItemsList(cartItems, comboCartItems);
        order.put("items", itemsList);
        
        return order;
    }
    
    @NonNull
    private List<Map<String, Object>> buildItemsList(
            @NonNull List<CartItem> cartItems,
            @NonNull List<ComboCartItem> comboCartItems) {
        
        List<Map<String, Object>> itemsList = new ArrayList<>();
        
        // Add CartItems
        for (CartItem item : cartItems) {
            Product p = item.getMilkTea();
            Map<String, Object> row = new HashMap<>();
            row.put("productId", p != null ? p.getId() : null);
            row.put("name", p != null ? p.getName() : null);
            row.put("price", p != null && p.getPrice() != null ? p.getPrice().longValue() : 0L);
            row.put("quantity", item.getQuantity());
            row.put("size", item.getSize());
            row.put("unitPrice", item.getUnitPrice());
            row.put("lineTotal", item.getTotalPrice());
            row.put("sugar", item.getSugar());
            row.put("ice", item.getIce());
            row.put("note", item.getNote());
            
            // Toppings
            List<Map<String, Object>> tops = new ArrayList<>();
            if (item.getToppings() != null) {
                for (SelectedTopping st : item.getToppings()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", st.getId());
                    m.put("name", st.getName());
                    m.put("price", st.getPrice());
                    tops.add(m);
                }
            }
            row.put("toppings", tops);
            itemsList.add(row);
        }
        
        // Add ComboCartItems
        for (ComboCartItem comboItem : comboCartItems) {
            itemsList.add(comboItem.toOrderItemMap());
        }
        
        return itemsList;
    }
}

