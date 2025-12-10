package com.pro.milkteaapp.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.pro.milkteaapp.payment.PaymentCallback;
import com.pro.milkteaapp.payment.PaymentRequest;
import com.pro.milkteaapp.payment.PaymentService;

/**
 * Activity xử lý thanh toán online
 * Mở browser/app để thanh toán và đợi kết quả
 */
public class PaymentActivity extends AppCompatActivity {
    
    private static final String EXTRA_PAYMENT_METHOD = "payment_method";
    private static final String EXTRA_PAYMENT_REQUEST = "payment_request";
    private static final String EXTRA_ORDER_ID = "order_id";
    
    private String paymentMethod;
    private PaymentRequest paymentRequest;
    private String orderId;
    private PaymentService paymentService;
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Lấy thông tin từ Intent
        paymentMethod = getIntent().getStringExtra(EXTRA_PAYMENT_METHOD);
        paymentRequest = (PaymentRequest) getIntent().getSerializableExtra(EXTRA_PAYMENT_REQUEST);
        orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        
        if (paymentMethod == null || paymentRequest == null) {
            Toast.makeText(this, "Thông tin thanh toán không hợp lệ", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        paymentService = PaymentService.getInstance(this);
        
        // Bắt đầu thanh toán
        startPayment();
    }
    
    private void startPayment() {
        paymentService.processPayment(
                paymentMethod,
                paymentRequest,
                new PaymentCallback() {
                    @Override
                    public void onSuccess(@NonNull String transactionId, @Nullable String paymentUrl) {
                        // Mở URL thanh toán nếu có
                        if (paymentUrl != null && !paymentUrl.isEmpty()) {
                            openPaymentUrl(paymentUrl);
                        } else {
                            // Nếu không có URL, có thể là đã thanh toán thành công
                            // (ví dụ: COD hoặc payment đã hoàn tất)
                            returnPaymentResult(true, transactionId, "Thanh toán thành công");
                        }
                    }
                    
                    @Override
                    public void onError(@NonNull Exception error) {
                        Toast.makeText(PaymentActivity.this, 
                                "Lỗi thanh toán: " + error.getMessage(), 
                                Toast.LENGTH_LONG).show();
                        returnPaymentResult(false, null, error.getMessage());
                    }
                    
                    @Override
                    public void onCancel() {
                        Toast.makeText(PaymentActivity.this, 
                                "Bạn đã hủy thanh toán", 
                                Toast.LENGTH_SHORT).show();
                        returnPaymentResult(false, null, "Người dùng hủy thanh toán");
                    }
                }
        );
    }
    
    private void openPaymentUrl(String url) {
        try {
            // Sử dụng CustomTabs hoặc Intent để mở browser
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Kiểm tra xem có app nào có thể xử lý URL không
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
                // Activity sẽ đợi kết quả từ deep link hoặc onNewIntent
            } else {
                Toast.makeText(this, "Không thể mở thanh toán. Vui lòng kiểm tra lại.", Toast.LENGTH_SHORT).show();
                returnPaymentResult(false, null, "Không thể mở thanh toán");
            }
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi mở thanh toán: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            returnPaymentResult(false, null, e.getMessage());
        }
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        
        // Xử lý kết quả từ deep link
        Uri uri = intent.getData();
        if (uri != null) {
            handlePaymentResult(uri);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Kiểm tra nếu có deep link trong Intent hiện tại
        Intent intent = getIntent();
        Uri uri = intent.getData();
        if (uri != null && uri.getScheme() != null && uri.getScheme().startsWith("milkteaapp")) {
            handlePaymentResult(uri);
        }
    }
    
    private void handlePaymentResult(Uri uri) {
        if (paymentService == null || paymentMethod == null) {
            return;
        }
        
        // Xử lý kết quả thanh toán
        boolean success = paymentService.handlePaymentResult(paymentMethod, new Intent(Intent.ACTION_VIEW, uri));
        
        if (success) {
            // Lấy transaction ID từ URI nếu có
            String transactionId = uri.getQueryParameter("transactionId");
            if (transactionId == null) {
                transactionId = uri.getQueryParameter("vnp_TxnRef"); // VNPAY
            }
            if (transactionId == null) {
                transactionId = "PAID_" + System.currentTimeMillis();
            }
            
            returnPaymentResult(true, transactionId, "Thanh toán thành công");
        } else {
            returnPaymentResult(false, null, "Thanh toán thất bại");
        }
    }
    
    private void returnPaymentResult(boolean success, @Nullable String transactionId, @Nullable String message) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("success", success);
        resultIntent.putExtra("transactionId", transactionId);
        resultIntent.putExtra("orderId", orderId);
        resultIntent.putExtra("message", message);
        
        setResult(success ? RESULT_OK : RESULT_CANCELED, resultIntent);
        finish();
    }
    
    /**
     * Tạo Intent để mở PaymentActivity
     */
    public static Intent createIntent(@NonNull android.content.Context context,
                                     @NonNull String paymentMethod,
                                     @NonNull PaymentRequest request,
                                     @NonNull String orderId) {
        Intent intent = new Intent(context, PaymentActivity.class);
        intent.putExtra(EXTRA_PAYMENT_METHOD, paymentMethod);
        intent.putExtra(EXTRA_PAYMENT_REQUEST, request);
        intent.putExtra(EXTRA_ORDER_ID, orderId);
        return intent;
    }
}

