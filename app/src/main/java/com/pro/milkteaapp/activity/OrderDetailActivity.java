package com.pro.milkteaapp.activity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.OrderLinesAdapter;
import com.pro.milkteaapp.databinding.UserActivityOrderDetailBinding;
import com.pro.milkteaapp.models.Order;
import com.pro.milkteaapp.models.OrderLine;
import com.pro.milkteaapp.repository.OrdersRepository;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class OrderDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_ID = "extra_order_id";

    private UserActivityOrderDetailBinding b;
    private OrdersRepository ordersRepository;
    private final OrderLinesAdapter itemsAdapter = new OrderLinesAdapter();

    private String orderId;
    private String status; // lưu trạng thái hiện tại để quyết định có cho hủy hay không

    @SuppressLint("PrivateResource")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = UserActivityOrderDetailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        // Toolbar + Back
        setSupportActionBar(b.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle("CHI TIẾT ĐƠN HÀNG");
        b.toolbar.setTitleTextAppearance(this, R.style.ToolbarTitleStyle);
        b.toolbar.setNavigationIcon(com.google.android.material.R.drawable.ic_arrow_back_black_24);
        b.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        centerToolbarTitle(b.toolbar);

        // Items list
        b.rvItems.setLayoutManager(new LinearLayoutManager(this));
        b.rvItems.setAdapter(itemsAdapter);

        ordersRepository = new OrdersRepository();

        orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        if (TextUtils.isEmpty(orderId)) {
            Toast.makeText(this, "Thiếu mã đơn hàng", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        b.btnCancelOrder.setOnClickListener(v -> showCancelDialog());

        loadOrder(orderId);
    }
    
    private android.widget.TextView customTitleView;
    
    private void centerToolbarTitle(MaterialToolbar toolbar) {
        customTitleView = com.pro.milkteaapp.utils.ToolbarUtils.centerToolbarTitle(
                toolbar, "CHI TIẾT ĐƠN HÀNG", customTitleView, getSupportActionBar());
    }

    private void loadOrder(String orderId) {
        b.progress.setVisibility(View.VISIBLE);
        // Vẫn cần fetch document trực tiếp vì cần items và addressObj không có trong Order model
        // Có thể tạo OrdersRepository.fetchOrderDocument() sau nếu cần
        ordersRepository.fetchOrderById(orderId, new OrdersRepository.OnOrderListener() {
            @Override
            public void onOrderReceived(@NonNull Order order) {
                // Fetch lại document để có đầy đủ thông tin (items, addressObj)
                fetchOrderDocument(orderId);
            }

            @Override
            public void onError(@NonNull Exception error) {
                Toast.makeText(OrderDetailActivity.this, "Lỗi tải đơn: " + error.getMessage(), Toast.LENGTH_LONG).show();
                b.progress.setVisibility(View.GONE);
                finish();
            }
        });
    }

    private void fetchOrderDocument(String orderId) {
        ordersRepository.fetchOrderDocument(orderId, new OrdersRepository.OnOrderDocumentListener() {
            @Override
            public void onOrderDocumentReceived(@NonNull com.google.firebase.firestore.DocumentSnapshot document) {
                bindOrder(document);
            }

            @Override
            public void onError(@NonNull Exception error) {
                Toast.makeText(OrderDetailActivity.this, "Lỗi tải chi tiết đơn: " + error.getMessage(), Toast.LENGTH_LONG).show();
                b.progress.setVisibility(View.GONE);
            }
        });
    }

    @SuppressLint("SetTextI18n")
    @SuppressWarnings("unchecked")
    private void bindOrder(DocumentSnapshot d) {
        if (!d.exists()) { Toast.makeText(this, "Đơn không tồn tại", Toast.LENGTH_SHORT).show(); finish(); return; }

        b.tvOrderId.setText("#" + d.getId());
        status = s(d.getString("status"));
        b.tvStatus.setText(status);

        // Thông tin đơn hàng
        Timestamp createdAt = d.getTimestamp("createdAt");
        if (createdAt != null) {
            String dateStr = DateFormat.getMediumDateFormat(this).format(createdAt.toDate())
                    + " " + DateFormat.getTimeFormat(this).format(createdAt.toDate());
            b.tvCreatedAt.setText(dateStr);
        } else {
            b.tvCreatedAt.setText("—");
        }

        // Phương thức thanh toán
        String paymentMethod = s(d.getString("paymentMethod"));
        if (TextUtils.isEmpty(paymentMethod)) paymentMethod = "COD";
        b.tvPaymentMethod.setText(formatPaymentMethod(paymentMethod));

        // Trạng thái thanh toán
        String paymentStatus = s(d.getString("paymentStatus"));
        if (!TextUtils.isEmpty(paymentStatus)) {
            b.rowPaymentStatus.setVisibility(View.VISIBLE);
            b.tvPaymentStatus.setText(formatPaymentStatus(paymentStatus));
        } else {
            b.rowPaymentStatus.setVisibility(View.GONE);
        }

        // Transaction ID
        String transactionId = s(d.getString("transactionId"));
        if (!TextUtils.isEmpty(transactionId)) {
            b.rowTransactionId.setVisibility(View.VISIBLE);
            b.tvTransactionId.setText(transactionId);
        } else {
            b.rowTransactionId.setVisibility(View.GONE);
        }

        // Voucher code
        String voucherCode = s(d.getString("voucherCode"));
        if (!TextUtils.isEmpty(voucherCode)) {
            b.rowVoucher.setVisibility(View.VISIBLE);
            b.tvVoucherCode.setText(voucherCode);
        } else {
            b.rowVoucher.setVisibility(View.GONE);
        }

        // Shipping label
        String shippingLabel = s(d.getString("shippingLabel"));
        if (!TextUtils.isEmpty(shippingLabel)) {
            b.rowShipping.setVisibility(View.VISIBLE);
            b.tvShippingLabel.setText(shippingLabel);
        } else {
            b.rowShipping.setVisibility(View.GONE);
        }

        b.tvSubtotal.setText(MoneyUtils.formatVnd(nz(d.getDouble("subtotal"))));
        b.tvDiscount.setText(MoneyUtils.formatVnd(nz(d.getDouble("discount"))));
        b.tvShippingFee.setText(MoneyUtils.formatVnd(nz(d.getDouble("shippingFee"))));
        b.tvFinalTotal.setText(MoneyUtils.formatVnd(nz(d.getDouble("finalTotal"))));

        // Địa chỉ
        String addressStr = d.getString("address");
        Map<String, Object> addressObj = (Map<String, Object>) d.get("addressObj");
        if (addressObj != null && !addressObj.isEmpty()) {
            String name = s(addressObj.get("fullName"));
            String phone = s(addressObj.get("phone"));
            String line1 = s(addressObj.get("line1"));
            String line2 = s(addressObj.get("line2"));
            String city = s(addressObj.get("city"));
            String province = s(addressObj.get("province"));
            String postal = s(addressObj.get("postalCode"));
            String display = s(addressObj.get("display"));

            b.tvReceiver.setText(join(" • ", name, phone));
            b.tvAddressLines.setText(!TextUtils.isEmpty(display)
                    ? display
                    : join(", ", line1, line2, city, province, postal));
        } else {
            b.tvReceiver.setText("—");
            b.tvAddressLines.setText(!TextUtils.isEmpty(addressStr) ? addressStr : "Chưa có địa chỉ");
        }

        // Items
        List<OrderLine> lines = new ArrayList<>();
        Object itemsObj = d.get("items");
        android.util.Log.d("OrderDetail", "Items object type: " + (itemsObj != null ? itemsObj.getClass().getSimpleName() : "null"));
        if (itemsObj instanceof List<?> itemsList) {
            android.util.Log.d("OrderDetail", "Items list size: " + itemsList.size());
            for (int i = 0; i < itemsList.size(); i++) {
                Object o = itemsList.get(i);
                android.util.Log.d("OrderDetail", "Item " + i + " type: " + (o != null ? o.getClass().getSimpleName() : "null"));
                if (o instanceof Map) {
                    Map<String, Object> itemMap = (Map<String, Object>) o;
                    android.util.Log.d("OrderDetail", "Item " + i + " keys: " + itemMap.keySet());
                    android.util.Log.d("OrderDetail", "Item " + i + " type field: " + itemMap.get("type"));
                    OrderLine line = OrderLine.fromMap(itemMap);
                    lines.add(line);
                    android.util.Log.d("OrderDetail", "Item " + i + " parsed - name: " + line.getName() + ", isCombo: " + line.isCombo());
                }
            }
        }
        android.util.Log.d("OrderDetail", "Total lines parsed: " + lines.size());
        itemsAdapter.submit(lines);

        // Thông tin hủy (nếu có)
        String cancelReason = d.getString("cancelReason");
        Timestamp canceledAt = d.getTimestamp("canceledAt");
        if (!TextUtils.isEmpty(cancelReason) || canceledAt != null || Order.STATUS_CANCELLED.equals(status)) {
            b.cardCancelInfo.setVisibility(View.VISIBLE);
            b.tvCancelReason.setText(!TextUtils.isEmpty(cancelReason) ? cancelReason : "—");
            if (canceledAt != null) {
                String timeStr = DateFormat.getMediumDateFormat(this).format(canceledAt.toDate())
                        + " " + DateFormat.getTimeFormat(this).format(canceledAt.toDate());
                b.tvCanceledAt.setText(String.format(Locale.getDefault(), "Thời gian hủy: %s", timeStr));
            } else {
                b.tvCanceledAt.setText("");
            }
        } else {
            b.cardCancelInfo.setVisibility(View.GONE);
        }

        // Quy tắc hiển thị nút HỦY:
        // Cho hủy nếu đơn PENDING hoặc CONFIRMED (tùy business của bạn)
        boolean cancelable =
                Order.STATUS_PENDING.equals(status);
        // Ẩn nếu đã hủy/hoàn tất/đang giao/đang xử lý
        b.btnCancelOrder.setVisibility(cancelable ? View.VISIBLE : View.GONE);
    }

    @SuppressLint({"CutPasteId", "MissingInflatedId"})
    private void showCancelDialog() {
        // Inflate view nhập lý do
        View view = LayoutInflater.from(this).inflate(R.layout._dialog_cancel_reason, null, false);
        // Tận dụng 1 input trong layout có sẵn; hoặc tự tạo layout riêng. Ở đây gán id edtLine1 để nhập lý do.
        TextInputEditText edt;
        edt = view.findViewById(R.id.edtLine1);

        if (edt != null) {
            edt.setHint("Nhập lý do hủy đơn");
            edt.setText("");
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Hủy đơn hàng")
                .setMessage("Bạn chắc chắn muốn hủy đơn này?")
                .setView(view)
                .setPositiveButton("Hủy đơn", (dialog, which) -> {
                    String reason = edt != null ? String.valueOf(edt.getText()).trim() : "";
                    performCancelOrder(reason);
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void performCancelOrder(String reason) {
        if (TextUtils.isEmpty(orderId)) return;

        b.progress.setVisibility(View.VISIBLE);
        ordersRepository.cancelOrder(orderId, reason, new OrdersRepository.OnOrderUpdateListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(OrderDetailActivity.this, "Đã hủy đơn hàng", Toast.LENGTH_SHORT).show();
                // Cập nhật UI tại chỗ (không phải reload mạng)
                status = Order.STATUS_CANCELLED;
                b.tvStatus.setText(status);
                b.btnCancelOrder.setVisibility(View.GONE);
                b.cardCancelInfo.setVisibility(View.VISIBLE);
                b.tvCancelReason.setText(reason);
                // Thời gian hủy sẽ hiển thị sau lần reload
                fetchOrderDocument(orderId);
            }

            @Override
            public void onError(@NonNull Exception error) {
                Toast.makeText(OrderDetailActivity.this, "Hủy đơn thất bại: " + error.getMessage(), Toast.LENGTH_LONG).show();
                b.progress.setVisibility(View.GONE);
            }
        });
    }

    private void bindCancelBlockOnly(DocumentSnapshot d) {
        if (d == null) return;
        String reason = d.getString("cancelReason");
        Timestamp canceledAt = d.getTimestamp("canceledAt");
        b.cardCancelInfo.setVisibility(View.VISIBLE);
        b.tvCancelReason.setText(!TextUtils.isEmpty(reason) ? reason : "—");
        if (canceledAt != null) {
            String timeStr = DateFormat.getMediumDateFormat(this).format(canceledAt.toDate())
                    + " " + DateFormat.getTimeFormat(this).format(canceledAt.toDate());
            b.tvCanceledAt.setText(String.format(Locale.getDefault(), "Thời gian hủy: %s", timeStr));
        } else {
            b.tvCanceledAt.setText("");
        }
    }

    private static String s(Object o) { return o == null ? "" : String.valueOf(o).trim(); }
    private static double nz(Double d) { return d == null ? 0 : d; }

    private static String join(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (TextUtils.isEmpty(p)) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p);
        }
        return sb.toString();
    }

    private String formatPaymentMethod(String method) {
        if (TextUtils.isEmpty(method)) return "COD";
        return switch (method.toUpperCase(Locale.getDefault())) {
            case "COD" -> "Thanh toán khi nhận";
            case "MOMO" -> "Ví Momo";
            case "VNPAY" -> "VNPAY";
            case "CARD" -> "Thẻ ngân hàng";
            default -> method;
        };
    }

    private String formatPaymentStatus(String status) {
        if (TextUtils.isEmpty(status)) return "—";
        return switch (status.toUpperCase(Locale.getDefault())) {
            case "SUCCESS" -> "Đã thanh toán";
            case "PENDING" -> "Chờ thanh toán";
            case "FAILED" -> "Thanh toán thất bại";
            case "CANCELLED" -> "Đã hủy";
            default -> status;
        };
    }
}
