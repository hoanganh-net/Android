package com.pro.milkteaapp.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.fragment.bottomsheet.CheckoutBottomSheet;
import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.models.CheckoutInfo;
import com.pro.milkteaapp.models.ComboCartItem;
import com.pro.milkteaapp.models.Product;
import com.pro.milkteaapp.models.SelectedTopping;
import com.pro.milkteaapp.payment.PaymentRequest;
import com.pro.milkteaapp.payment.PaymentService;
import com.pro.milkteaapp.constants.FirestoreConstants;
import com.pro.milkteaapp.utils.MoneyUtils;
import com.pro.milkteaapp.utils.OrderCreator;
import com.pro.milkteaapp.utils.VoucherUsageRecorder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CheckoutActivity extends AppCompatActivity
        implements CheckoutBottomSheet.OnCheckoutConfirmListener {

    private final ArrayList<CartItem> cartItems = new ArrayList<>();
    private final ArrayList<ComboCartItem> comboCartItems = new ArrayList<>();
    @Nullable
    private CheckoutInfo checkoutInfo;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        restoreFromIntent();
        openCheckoutBottomSheet();
    }

    private void restoreFromIntent() {
        Serializable extra = getIntent().getSerializableExtra("cart_items");
        if (extra instanceof ArrayList<?> raw) {
            for (Object o : raw) {
                if (o instanceof CartItem) {
                    cartItems.add((CartItem) o);
                }
            }
        }

        Serializable comboExtra = getIntent().getSerializableExtra("combo_cart_items");
        if (comboExtra instanceof ArrayList<?> raw) {
            for (Object o : raw) {
                if (o instanceof ComboCartItem) {
                    comboCartItems.add((ComboCartItem) o);
                }
            }
        }

        Serializable infoSer = getIntent().getSerializableExtra("checkout_info");
        if (infoSer instanceof CheckoutInfo) {
            checkoutInfo = (CheckoutInfo) infoSer;
        }
    }

    private void openCheckoutBottomSheet() {
        CheckoutBottomSheet sheet = CheckoutBottomSheet.newInstance(cartItems, comboCartItems);
        sheet.setOnCheckoutConfirmListener(this);
        sheet.show(getSupportFragmentManager(), "CheckoutBottomSheet");
    }

    @Override
    public void onCheckoutConfirmed(@NonNull CheckoutInfo info) {
        this.checkoutInfo = info;
        
        // Kiểm tra nếu cần thanh toán online
        PaymentService paymentService = PaymentService.getInstance(this);
        if (paymentService.requiresOnlinePayment(info.getPaymentMethod())) {
            processOnlinePayment(info);
        } else {
            // COD - tạo order ngay
            placeOrder(null);
        }
    }
    
    /**
     * Xử lý thanh toán online (Momo, VNPAY, Card)
     */
    private void processOnlinePayment(@NonNull CheckoutInfo info) {
        // Lấy userId
        String effectiveUid = null;
        try {
            effectiveUid = new SessionManager(getApplicationContext()).getUid();
        } catch (Throwable e) {
            android.util.Log.w("Checkout", "Failed to get UID from SessionManager: " + e.getMessage());
        }
        if (TextUtils.isEmpty(effectiveUid)) {
            effectiveUid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
        }
        
        // Tạo order ID tạm để thanh toán
        String tempOrderId = db.collection(FirestoreConstants.COLLECTION_ORDERS).document().getId();
        long amount = (long) info.getGrandTotal();
        
        // Tạo return URL (deep link để nhận kết quả)
        String returnUrl = "milkteaapp://payment/return?orderId=" + tempOrderId + "&method=" + info.getPaymentMethod();
        String cancelUrl = "milkteaapp://payment/cancel?orderId=" + tempOrderId;
        
        PaymentRequest request = new PaymentRequest(
                tempOrderId,
                effectiveUid,
                amount,
                getString(R.string.payment_order_title, tempOrderId),
                returnUrl,
                cancelUrl
        );
        
        // Mở PaymentActivity để xử lý thanh toán
        Intent paymentIntent = PaymentActivity.createIntent(
                this,
                info.getPaymentMethod(),
                request,
                tempOrderId
        );
        
        startActivityForResult(paymentIntent, REQUEST_CODE_PAYMENT);
    }
    
    private static final int REQUEST_CODE_PAYMENT = 1001;
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_CODE_PAYMENT) {
            if (resultCode == RESULT_OK && data != null) {
                boolean success = data.getBooleanExtra("success", false);
                String transactionId = data.getStringExtra("transactionId");
                String orderId = data.getStringExtra("orderId");
                String message = data.getStringExtra("message");
                
                if (success && transactionId != null) {
                    // Thanh toán thành công - tạo order
                    placeOrder(transactionId);
                } else {
                    // Thanh toán thất bại hoặc hủy
                    if (message != null) {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    }
                }
            } else {
                // Người dùng quay lại mà không thanh toán
                Toast.makeText(this, getString(R.string.payment_cancelled), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private long computeSubtotalFromCart() {
        long sum = 0;
        for (CartItem i : cartItems) {
            sum += i.getTotalPrice();
        }
        for (ComboCartItem comboItem : comboCartItems) {
            sum += comboItem.getTotalPrice();
        }
        return sum;
    }

    private long getSubtotal() {
        return checkoutInfo != null
                ? (long) checkoutInfo.getSubtotal()
                : computeSubtotalFromCart();
    }

    private long getDiscount() {
        return checkoutInfo != null
                ? (long) checkoutInfo.getDiscount()
                : 0L;
    }

    private long getShippingFee() {
        return checkoutInfo != null
                ? (long) checkoutInfo.getShippingFee()
                : 15_000L;
    }

    private long getGrandTotal() {
        return checkoutInfo != null
                ? (long) checkoutInfo.getGrandTotal()
                : Math.max(0L, getSubtotal() - getDiscount() + getShippingFee());
    }

    private void placeOrder(@Nullable String transactionId) {
        if (checkoutInfo == null) {
            Toast.makeText(this, getString(R.string.invalid_checkout_info), Toast.LENGTH_SHORT).show();
            return;
        }
        
        OrderCreator orderCreator = new OrderCreator(this);
        VoucherUsageRecorder voucherRecorder = new VoucherUsageRecorder();
        
        orderCreator.createOrder(
                cartItems,
                comboCartItems,
                checkoutInfo,
                transactionId,
                () -> {
                    // Order created successfully, record voucher usage
                    String effectiveUid = getEffectiveUserId();
                    String voucherCode = checkoutInfo.getVoucherCode();
                    String rewardVoucherCode = checkoutInfo.getRewardVoucherCode();
                    
                    // Record voucher giảm giá usage
                    voucherRecorder.recordVoucherUsageIfNeeded(
                            effectiveUid,
                            voucherCode,
                            () -> {
                                // Sau khi record voucher giảm giá, record voucher đổi quà
                                voucherRecorder.recordVoucherUsageIfNeeded(
                                        effectiveUid,
                                        rewardVoucherCode,
                                        () -> {
                                            Toast.makeText(this, "Đặt hàng thành công!", Toast.LENGTH_SHORT).show();
                                            finish();
                                        },
                                        e -> {
                                            Toast.makeText(this, "Đơn đã tạo nhưng cập nhật lượt voucher đổi quà lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                            finish();
                                        });
                            },
                            e -> {
                                Toast.makeText(this, "Đơn đã tạo nhưng cập nhật lượt voucher giảm giá lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                finish();
                            });
                },
                e -> Toast.makeText(this, getString(R.string.create_order_error, e.getMessage()), Toast.LENGTH_LONG).show()
        );
    }
    
    @NonNull
    private String getEffectiveUserId() {
        String customUid = null;
        try {
            customUid = new SessionManager(getApplicationContext()).getUid();
        } catch (Throwable e) {
            android.util.Log.w("Checkout", "Failed to get UID from SessionManager: " + e.getMessage());
        }
        if (TextUtils.isEmpty(customUid) && auth.getCurrentUser() != null) {
            return auth.getCurrentUser().getUid();
        }
        return customUid != null ? customUid : "";
    }


    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}
