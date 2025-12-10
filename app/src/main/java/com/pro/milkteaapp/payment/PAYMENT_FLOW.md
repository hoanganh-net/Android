# 📱 Hệ Thống Thanh Toán - Flow Hoạt Động

## 🔄 Tổng Quan Flow

Hệ thống thanh toán hỗ trợ 4 phương thức:
- **COD** (Cash on Delivery) - Thanh toán khi nhận hàng
- **Momo** - Ví điện tử Momo
- **VNPAY** - Cổng thanh toán VNPAY
- **CARD** - Thẻ ngân hàng

---

## 📊 Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│  1. NGƯỜI DÙNG CHỌN SẢN PHẨM VÀO GIỎ HÀNG                   │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  2. NHẤN "THANH TOÁN" → Mở CheckoutBottomSheet              │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  3. CHỌN THÔNG TIN:                                          │
│     - Địa chỉ giao hàng                                     │
│     - Phương thức thanh toán (COD/Momo/VNPAY/Card)          │
│     - Voucher (nếu có)                                       │
│     - Phương thức giao hàng                                  │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  4. NHẤN "XÁC NHẬN" → CheckoutActivity.onCheckoutConfirmed()│
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
        ┌──────────────┴──────────────┐
        │                             │
        ▼                             ▼
┌───────────────┐          ┌──────────────────────────┐
│   COD?        │          │  ONLINE PAYMENT?         │
│   (Không)     │          │  (Momo/VNPAY/Card)        │
└───────┬───────┘          └──────────┬───────────────┘
        │                              │
        │                              │
        ▼                              ▼
┌──────────────────┐      ┌──────────────────────────────┐
│ 5a. TẠO ORDER    │      │ 5b. XỬ LÝ THANH TOÁN ONLINE   │
│    NGAY          │      │                              │
│                  │      │  - Tạo PaymentRequest        │
│  placeOrder()    │      │  - Gọi PaymentService        │
│  với             │      │  - Mở app/web thanh toán    │
│  transactionId   │      │  - Đợi callback              │
│  = null          │      │                              │
└──────────────────┘      └──────────┬───────────────────┘
                                      │
                                      ▼
                           ┌──────────────────────────┐
                           │  THANH TOÁN THÀNH CÔNG?  │
                           └──────┬───────────┬───────┘
                                  │           │
                                  YES         NO
                                  │           │
                                  ▼           ▼
                    ┌──────────────────┐  ┌──────────────┐
                    │ 6. TẠO ORDER     │  │ Hiển thị lỗi │
                    │    với           │  │ hoặc hủy     │
                    │    transactionId │  └──────────────┘
                    └──────────────────┘
```

---

## 🔍 Chi Tiết Từng Bước

### **Bước 1-3: Chuẩn Bị Checkout**

1. Người dùng thêm sản phẩm vào giỏ hàng
2. Nhấn nút "Thanh toán" → Mở `CheckoutBottomSheet`
3. Người dùng chọn:
   - **Địa chỉ**: Từ `AddressPickerSheet`
   - **Phương thức thanh toán**: Từ `PaymentMethodPickerSheet` (COD/Momo/VNPAY/Card)
   - **Voucher**: Từ `VoucherListPickerSheet` (nếu có)
   - **Phương thức giao hàng**: Từ `ShippingPickerSheet`

### **Bước 4: Xác Nhận Checkout**

Khi người dùng nhấn "Xác nhận" trong `CheckoutBottomSheet`:

```java
// CheckoutActivity.java
@Override
public void onCheckoutConfirmed(@NonNull CheckoutInfo info) {
    this.checkoutInfo = info;
    
    PaymentService paymentService = PaymentService.getInstance(this);
    if (paymentService.requiresOnlinePayment(info.getPaymentMethod())) {
        // Thanh toán online (Momo/VNPAY/Card)
        processOnlinePayment(info);
    } else {
        // COD - tạo order ngay
        placeOrder(null);
    }
}
```

### **Bước 5a: COD (Thanh Toán Khi Nhận)**

**Flow:**
```
COD → placeOrder(null) → Tạo order ngay → Hoàn tất
```

**Chi tiết:**
1. Không cần thanh toán online
2. Gọi `placeOrder(null)` ngay lập tức
3. Tạo order với:
   - `paymentMethod = "COD"`
   - `paymentStatus = "PENDING"`
   - `transactionId = null`
4. Order được tạo thành công → Hiển thị thông báo

### **Bước 5b: Thanh Toán Online (Momo/VNPAY/Card)**

**Flow:**
```
Online Payment → processOnlinePayment() → Gateway → Thanh toán → Callback → placeOrder(transactionId)
```

**Chi tiết:**

#### **5b.1: Tạo PaymentRequest**
```java
String tempOrderId = db.collection("orders").document().getId();
long amount = (long) info.getGrandTotal();

PaymentRequest request = new PaymentRequest(
    tempOrderId,           // Order ID tạm
    effectiveUid,          // User ID
    amount,                // Số tiền (VND)
    "Thanh toán đơn hàng #" + tempOrderId,  // Mô tả
    returnUrl,             // URL trả về sau khi thanh toán
    cancelUrl              // URL hủy thanh toán
);
```

#### **5b.2: Gọi PaymentService**
```java
PaymentService paymentService = PaymentService.getInstance(this);
paymentService.processPayment(
    info.getPaymentMethod(),  // "Momo", "VNPAY", hoặc "CARD"
    request,
    new PaymentCallback() {
        @Override
        public void onSuccess(String transactionId, String paymentUrl) {
            // Thanh toán thành công → Tạo order
            placeOrder(transactionId);
        }
        
        @Override
        public void onError(Exception error) {
            // Hiển thị lỗi
            Toast.makeText(...).show();
        }
        
        @Override
        public void onCancel() {
            // Người dùng hủy
            Toast.makeText(...).show();
        }
    }
);
```

#### **5b.3: Gateway Xử Lý**

**Momo:**
1. `MomoPaymentGateway.initiatePayment()` được gọi
2. Tạo transaction ID: `"MOMO_" + timestamp + "_" + orderId`
3. Tạo payment URL (trong thực tế sẽ gọi Momo API)
4. Mở Momo app hoặc web để thanh toán
5. Đợi callback từ Momo (deep link hoặc webhook)

**VNPAY:**
1. `VNPAYPaymentGateway.initiatePayment()` được gọi
2. Tạo transaction ID: `"VNPAY_" + timestamp + "_" + orderId`
3. Tạo payment URL với các tham số VNPAY
4. Mở VNPAY để thanh toán
5. Đợi callback từ VNPAY (return URL)

**Card:**
1. `CardPaymentGateway.initiatePayment()` được gọi
2. Tạo transaction ID: `"CARD_" + timestamp + "_" + orderId`
3. Mở form nhập thông tin thẻ (SDK)
4. Xử lý thanh toán qua payment gateway
5. Đợi callback từ SDK

#### **5b.4: Callback và Tạo Order**

Khi thanh toán thành công:
```java
@Override
public void onSuccess(@NonNull String transactionId, @Nullable String paymentUrl) {
    // transactionId: "MOMO_1234567890_ORD123" hoặc tương tự
    placeOrder(transactionId);
}
```

### **Bước 6: Tạo Order**

**Method: `placeOrder(@Nullable String transactionId)`**

**Với COD (transactionId = null):**
```java
order.put("paymentMethod", "COD");
order.put("paymentStatus", "PENDING");
order.put("transactionId", null);
```

**Với Online Payment (transactionId != null):**
```java
order.put("paymentMethod", "Momo");  // hoặc "VNPAY", "CARD"
order.put("paymentStatus", "SUCCESS");  // Vì đã thanh toán thành công
order.put("transactionId", "MOMO_1234567890_ORD123");
```

**Order được lưu vào Firestore:**
```javascript
{
  orderId: "ORD123",
  userId: "USR001",
  paymentMethod: "Momo",
  paymentStatus: "SUCCESS",
  transactionId: "MOMO_1234567890_ORD123",
  finalTotal: 150000,
  status: "PENDING",
  createdAt: Timestamp,
  // ... các field khác
}
```

---

## 🔐 Xử Lý Deep Link / Return URL

### **Deep Link cho Momo:**
```
milkteaapp://payment/return?orderId=ORD123&resultCode=0
```

### **Return URL cho VNPAY:**
```
https://yourapp.com/payment/return?vnp_ResponseCode=00&vnp_TxnRef=VNPAY_123...
```

### **Xử Lý trong Activity:**
```java
@Override
protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    
    Uri uri = intent.getData();
    if (uri != null && "milkteaapp".equals(uri.getScheme())) {
        String path = uri.getHost();
        if ("payment".equals(path)) {
            // Xử lý kết quả thanh toán
            PaymentService paymentService = PaymentService.getInstance(this);
            paymentService.handlePaymentResult(paymentMethod, intent);
        }
    }
}
```

---

## 📝 Ví Dụ Cụ Thể

### **Ví dụ 1: Thanh Toán COD**

```
1. User chọn COD
2. onCheckoutConfirmed() → requiresOnlinePayment("COD") = false
3. placeOrder(null) được gọi ngay
4. Order tạo với:
   - paymentMethod: "COD"
   - paymentStatus: "PENDING"
   - transactionId: null
5. Thông báo: "Đặt hàng thành công!"
```

### **Ví dụ 2: Thanh Toán Momo**

```
1. User chọn Momo
2. onCheckoutConfirmed() → requiresOnlinePayment("Momo") = true
3. processOnlinePayment() được gọi
4. Tạo PaymentRequest với orderId = "ORD123", amount = 150000
5. MomoPaymentGateway.initiatePayment()
6. Tạo transactionId = "MOMO_1234567890_ORD123"
7. Mở Momo app để thanh toán
8. User thanh toán trong Momo app
9. Momo gọi callback: onSuccess("MOMO_1234567890_ORD123", paymentUrl)
10. placeOrder("MOMO_1234567890_ORD123") được gọi
11. Order tạo với:
    - paymentMethod: "Momo"
    - paymentStatus: "SUCCESS"
    - transactionId: "MOMO_1234567890_ORD123"
12. Thông báo: "Đặt hàng thành công!"
```

---

## ⚠️ Lưu Ý Quan Trọng

1. **Hiện tại là Mock Implementation:**
   - Các gateway (Momo, VNPAY, Card) chưa tích hợp SDK thực tế
   - Cần thay thế các phần TODO trong code

2. **Deep Link cần cấu hình:**
   - Thêm intent-filter trong AndroidManifest.xml
   - Xử lý onNewIntent() trong Activity

3. **Webhook (Khuyến nghị):**
   - Nên có backend server để nhận webhook từ payment gateway
   - Đảm bảo tính bảo mật và xác thực

4. **Error Handling:**
   - Xử lý timeout
   - Xử lý mạng lỗi
   - Xử lý người dùng hủy thanh toán

---

## 🚀 Bước Tiếp Theo

1. Tích hợp SDK thực tế cho Momo/VNPAY/Card
2. Cấu hình deep link trong AndroidManifest
3. Thêm webhook handler (backend)
4. Thêm UI loading khi đang xử lý thanh toán
5. Thêm màn hình xác nhận thanh toán
6. Test với sandbox/test environment

