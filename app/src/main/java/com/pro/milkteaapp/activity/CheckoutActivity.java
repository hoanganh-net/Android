package com.pro.milkteaapp.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.fragment.bottomsheet.CheckoutBottomSheet;
import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.models.CheckoutInfo;
import com.pro.milkteaapp.models.ComboCartItem;
import com.pro.milkteaapp.payment.PaymentService;
import com.pro.milkteaapp.utils.OrderCreator;
import com.pro.milkteaapp.utils.VoucherUsageRecorder;

import java.io.Serializable;
import java.util.ArrayList;

public class CheckoutActivity extends AppCompatActivity
        implements CheckoutBottomSheet.OnCheckoutConfirmListener {

    private final ArrayList<CartItem> cartItems = new ArrayList<>();
    private final ArrayList<ComboCartItem> comboCartItems = new ArrayList<>();
    @Nullable
    private CheckoutInfo checkoutInfo;

    private FirebaseAuth auth;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();

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
        if (infoSer != null) {
            try {
                checkoutInfo = (CheckoutInfo) (Object) infoSer;
            } catch (ClassCastException e) {
                // Not a CheckoutInfo instance, leave checkoutInfo as null
            }
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
        if (paymentService.requiresOnlinePayment(info.paymentMethod())) {
            processOnlinePayment(info);
        } else {
            // COD - tạo order ngay
            placeOrder(null);
        }
    }
    
    /**
     * Xử lý thanh toán online (Momo, VNPAY, Card)
     * Hiện tại chỉ là mock - tạo transaction ID và tạo order ngay
     */
    private void processOnlinePayment(@NonNull CheckoutInfo info) {
        // Mock: Tạo transaction ID và tạo order ngay
        // Trong thực tế sẽ cần tích hợp với payment gateway API
        String transactionId = info.paymentMethod().toUpperCase() + "_" + System.currentTimeMillis();
        placeOrder(transactionId);
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
                    String voucherCode = checkoutInfo.voucherCode();
                    String rewardVoucherCode = checkoutInfo.rewardVoucherCode();
                    
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
