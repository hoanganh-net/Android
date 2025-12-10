package com.pro.milkteaapp.payment;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Payment Gateway cho VNPAY
 * 
 * LƯU Ý: Để tích hợp thực tế, cần:
 * 1. Đăng ký tài khoản VNPAY Merchant
 * 2. Lấy TmnCode và SecretKey
 * 3. Tích hợp VNPAY SDK hoặc sử dụng Payment Gateway API
 * 4. Cấu hình return URL để nhận kết quả thanh toán
 * 
 * Hiện tại implementation này là mock để demo flow
 */
public class VNPAYPaymentGateway implements PaymentGateway {
    
    private final Context context;
    
    // TODO: Thay thế bằng thông tin thực tế
    private static final String VNPAY_TMN_CODE = "YOUR_TMN_CODE";
    private static final String VNPAY_SECRET_KEY = "YOUR_SECRET_KEY";
    private static final String VNPAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    
    public VNPAYPaymentGateway(Context context) {
        this.context = context.getApplicationContext();
    }
    
    @Override
    public void initiatePayment(@NonNull PaymentRequest request, @NonNull PaymentCallback callback) {
        // TODO: Tích hợp VNPAY SDK hoặc API thực tế
        // Hiện tại mock flow - sẽ mở browser để demo
        
        try {
            // Tạo transaction ID
            String transactionId = "VNPAY_" + System.currentTimeMillis() + "_" + request.getOrderId();
            
            // TODO: Tạo payment URL từ VNPAY API
            // Ví dụ:
            // String paymentUrl = createVNPAYPaymentUrl(request, transactionId);
            
            // Mock: Tạo payment URL demo (trong thực tế sẽ từ VNPAY API với hash)
            // VNPAY dùng đơn vị xu (amount * 100)
            String returnUrl = request.getReturnUrl() != null ? request.getReturnUrl() : "";
            String paymentUrl = VNPAY_URL 
                    + "?vnp_Amount=" + (request.getAmount() * 100)
                    + "&vnp_Command=pay"
                    + "&vnp_CreateDate=" + System.currentTimeMillis()
                    + "&vnp_CurrCode=VND"
                    + "&vnp_IpAddr=127.0.0.1"
                    + "&vnp_Locale=vn"
                    + "&vnp_OrderInfo=" + URLEncoder.encode(request.getDescription(), "UTF-8")
                    + "&vnp_OrderType=other"
                    + "&vnp_ReturnUrl=" + URLEncoder.encode(returnUrl, "UTF-8")
                    + "&vnp_TmnCode=" + VNPAY_TMN_CODE
                    + "&vnp_TxnRef=" + transactionId;
                    // TODO: Thêm vnp_SecureHash theo quy tắc của VNPAY
            
            // Trả về payment URL để Activity mở
            // KHÔNG gọi callback.onSuccess() ở đây - sẽ đợi kết quả từ return URL
            callback.onSuccess(transactionId, paymentUrl);
            
        } catch (Exception e) {
            callback.onError(new Exception("Lỗi khởi tạo thanh toán VNPAY: " + e.getMessage()));
        }
    }
    
    @Override
    public void checkPaymentStatus(@NonNull String transactionId, @NonNull PaymentStatusCallback callback) {
        // TODO: Gọi VNPAY API để kiểm tra trạng thái
        // Hiện tại mock
        try {
            // Mock: Giả sử đang kiểm tra
            // Trong thực tế sẽ gọi: GET /api/vnpay/check-status?transactionId=...
            callback.onStatusReceived(PaymentStatus.PENDING, transactionId);
        } catch (Exception e) {
            callback.onError(new Exception("Lỗi kiểm tra trạng thái VNPAY: " + e.getMessage()));
        }
    }
    
    @Override
    public boolean handlePaymentResult(@NonNull String data) {
        // Xử lý kết quả từ VNPAY return URL
        try {
            Uri uri = Uri.parse(data);
            String vnp_ResponseCode = uri.getQueryParameter("vnp_ResponseCode");
            String vnp_TxnRef = uri.getQueryParameter("vnp_TxnRef");
            
            if ("00".equals(vnp_ResponseCode)) {
                // Thanh toán thành công
                Toast.makeText(context, "Thanh toán VNPAY thành công!", Toast.LENGTH_SHORT).show();
                return true;
            } else {
                // Thanh toán thất bại
                Toast.makeText(context, "Thanh toán VNPAY thất bại", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(context, "Không thể mở VNPAY. Vui lòng kiểm tra lại.", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Tạo payment URL từ VNPAY API
     * TODO: Implement thực tế với VNPAY API
     */
    private String createVNPAYPaymentUrl(PaymentRequest request, String transactionId) {
        // TODO: Tạo payment URL với VNPAY API
        // Sử dụng VNPAY_TMN_CODE, VNPAY_SECRET_KEY
        // Tạo hash theo quy tắc của VNPAY
        return "";
    }
}

