package com.pro.milkteaapp.payment;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.pro.milkteaapp.R;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Payment Gateway cho Momo
 * 
 * LƯU Ý: Để tích hợp thực tế, cần:
 * 1. Đăng ký tài khoản Momo Partner
 * 2. Lấy API Key và Secret Key
 * 3. Tích hợp Momo SDK hoặc sử dụng Payment Gateway API
 * 4. Cấu hình deep link để nhận kết quả thanh toán
 * 
 * Hiện tại implementation này là mock để demo flow
 */
public class MomoPaymentGateway implements PaymentGateway {
    
    private final Context context;
    
    // TODO: Thay thế bằng API key thực tế
    private static final String MOMO_API_KEY = "YOUR_MOMO_API_KEY";
    private static final String MOMO_SECRET_KEY = "YOUR_MOMO_SECRET_KEY";
    private static final String MOMO_PARTNER_CODE = "YOUR_PARTNER_CODE";
    
    public MomoPaymentGateway(Context context) {
        this.context = context.getApplicationContext();
    }
    
    @Override
    public void initiatePayment(@NonNull PaymentRequest request, @NonNull PaymentCallback callback) {
        // TODO: Tích hợp Momo SDK hoặc API thực tế
        // Hiện tại mock flow - sẽ mở browser để demo
        
        try {
            // Tạo transaction ID
            String transactionId = "MOMO_" + System.currentTimeMillis() + "_" + request.getOrderId();
            
            // TODO: Gọi Momo API để tạo payment URL
            // Ví dụ:
            // String paymentUrl = createMomoPaymentUrl(request, transactionId);
            
            // Mock: Tạo payment URL demo (trong thực tế sẽ từ Momo API)
            // URL này sẽ redirect về deep link sau khi thanh toán
            String returnUrl = request.getReturnUrl() != null ? request.getReturnUrl() : "";
            String paymentUrl = "https://sandbox.momo.vn/payment?orderId=" 
                    + request.getOrderId() 
                    + "&amount=" + request.getAmount()
                    + "&description=" + URLEncoder.encode(request.getDescription(), "UTF-8")
                    + "&returnUrl=" + URLEncoder.encode(returnUrl, "UTF-8")
                    + "&transactionId=" + transactionId;
            
            // Trả về payment URL để Activity mở
            // KHÔNG gọi callback.onSuccess() ở đây - sẽ đợi kết quả từ deep link
            callback.onSuccess(transactionId, paymentUrl);
            
        } catch (Exception e) {
            callback.onError(new Exception("Lỗi khởi tạo thanh toán Momo: " + e.getMessage()));
        }
    }
    
    @Override
    public void checkPaymentStatus(@NonNull String transactionId, @NonNull PaymentStatusCallback callback) {
        // TODO: Gọi Momo API để kiểm tra trạng thái
        // Hiện tại mock
        try {
            // Mock: Giả sử đang kiểm tra
            // Trong thực tế sẽ gọi: GET /api/momo/check-status?transactionId=...
            callback.onStatusReceived(PaymentStatus.PENDING, transactionId);
        } catch (Exception e) {
            callback.onError(new Exception("Lỗi kiểm tra trạng thái Momo: " + e.getMessage()));
        }
    }
    
    @Override
    public boolean handlePaymentResult(@NonNull String data) {
        // Xử lý kết quả từ Momo deep link hoặc redirect
        try {
            Uri uri = Uri.parse(data);
            String resultCode = uri.getQueryParameter("resultCode");
            String orderId = uri.getQueryParameter("orderId");
            
            if ("0".equals(resultCode)) {
                // Thanh toán thành công
                Toast.makeText(context, "Thanh toán Momo thành công!", Toast.LENGTH_SHORT).show();
                return true;
            } else {
                // Thanh toán thất bại
                Toast.makeText(context, "Thanh toán Momo thất bại", Toast.LENGTH_SHORT).show();
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    private void openPaymentUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Không thể mở Momo. Vui lòng kiểm tra lại.", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Tạo payment URL từ Momo API
     * TODO: Implement thực tế với Momo API
     */
    private String createMomoPaymentUrl(PaymentRequest request, String transactionId) {
        // TODO: Gọi Momo API để tạo payment URL
        // Sử dụng MOMO_API_KEY, MOMO_SECRET_KEY, MOMO_PARTNER_CODE
        return "";
    }
}

