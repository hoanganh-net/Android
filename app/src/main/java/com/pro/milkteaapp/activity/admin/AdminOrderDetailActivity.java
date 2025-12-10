package com.pro.milkteaapp.activity.admin;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.OrderLinesAdapter;
import com.pro.milkteaapp.databinding.AdminActivityOrderDetailBinding;
import com.pro.milkteaapp.models.OrderLine;
import com.pro.milkteaapp.repository.OrdersRepository;
import com.pro.milkteaapp.repository.UsersRepository;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminOrderDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_ID = "order_id";

    private AdminActivityOrderDetailBinding b;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final OrderLinesAdapter itemsAdapter = new OrderLinesAdapter();
    private final OrdersRepository ordersRepository = new OrdersRepository();
    private final UsersRepository usersRepository = new UsersRepository();

    private String orderId;
    private String userId;
    private String userName;
    private String statusNow;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = AdminActivityOrderDetailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        setSupportActionBar(b.toolbar);
        // Hiển thị nút back
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        b.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        b.rvItems.setLayoutManager(new LinearLayoutManager(this));
        b.rvItems.setAdapter(itemsAdapter);

        orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        if (TextUtils.isEmpty(orderId)) {
            toastLong("Thiếu mã đơn hàng");
            finish();
            return;
        }
        
        // Set title ban đầu
        centerToolbarTitle(b.toolbar, "ĐƠN #" + orderId);

        b.btnAdminConfirm.setOnClickListener(v -> doConfirm());
        b.btnAdminCancel.setOnClickListener(v -> showCancelDialog());

        loadOrder(orderId);
    }

    private void loadOrder(String orderId) {
        setLoading(true);
        // Ẩn và disable các nút action trong lúc đang load để tránh user click nhầm
        lockActionButtons(true);
        setVisible(b.btnAdminConfirm, false);
        setVisible(b.btnAdminCancel, false);
        // Vẫn cần fetch document trực tiếp vì cần items và addressObj không có trong Order model
        // TODO: Có thể tạo OrdersRepository.fetchOrderDocument() sau nếu cần
        ordersRepository.fetchOrderById(orderId, new OrdersRepository.OnOrderListener() {
            @Override
            public void onOrderReceived(@NonNull com.pro.milkteaapp.models.Order order) {
                // Fetch lại document để có đầy đủ thông tin (items, addressObj)
                fetchOrderDocument(orderId);
            }

            @Override
            public void onError(@NonNull Exception error) {
                toastLong("Lỗi tải đơn: " + error.getMessage());
                setLoading(false);
                lockActionButtons(false);
                setVisible(b.btnAdminConfirm, false);
                setVisible(b.btnAdminCancel, false);
                finish();
            }
        });
    }

    private void fetchOrderDocument(String orderId) {
        ordersRepository.fetchOrderDocument(orderId, new OrdersRepository.OnOrderDocumentListener() {
            @Override
            public void onOrderDocumentReceived(@NonNull DocumentSnapshot document) {
                bindOrder(document);
            }

            @Override
            public void onError(@NonNull Exception error) {
                toastLong("Lỗi tải chi tiết đơn: " + error.getMessage());
                setLoading(false);
                lockActionButtons(false);
                setVisible(b.btnAdminConfirm, false);
                setVisible(b.btnAdminCancel, false);
            }
        });
    }

    @SuppressLint("SetTextI18n")
    @SuppressWarnings("unchecked")
    private void bindOrder(DocumentSnapshot d) {
        if (!d.exists()) {
            toast("Đơn không tồn tại");
            finish();
            return;
        }

        String id = d.getId();
        statusNow = s(d.getString("status"));
        userId = s(d.getString("userId"));

        // Người đặt
        b.tvUser.setText("Đang tải người đặt.");
        if (!TextUtils.isEmpty(userId)) {
            usersRepository.fetchUserById(userId, new UsersRepository.OnUserListener() {
                @Override
                public void onUserReceived(@NonNull com.pro.milkteaapp.models.User user) {
                    String name = user.getFullName() != null ? user.getFullName() : user.getDisplayName();
                    String email = user.getEmail();
                    userName = name; // Lưu userName để dùng cho chat
                    b.tvUser.setText(!TextUtils.isEmpty(name) || !TextUtils.isEmpty(email)
                            ? join(name, email)
                            : "(Không có thông tin người dùng)");
                }

                @Override
                public void onError(@NonNull Exception error) {
                    b.tvUser.setText("Không thể tải người dùng");
                }
            });
        } else {
            b.tvUser.setText("(Không có userId)");
        }

        // tiền
        double subtotal    = nz(d.getDouble("subtotal"));
        double discount    = nz(d.getDouble("discount"));
        double shippingFee = nz(d.getDouble("shippingFee"));
        double finalTotal  = nz(d.getDouble("finalTotal"));
        if (finalTotal == 0) finalTotal = nz(d.getDouble("total"));

        Timestamp createdAt   = d.getTimestamp("createdAt");
        Timestamp confirmedAt = d.getTimestamp("confirmedAt");
        Timestamp finishedAt  = d.getTimestamp("finishedAt");

        String confirmedBy = s(d.getString("confirmedBy"));

        // huỷ (nhận cả 2 kiểu khoá)
        String cancelReason = firstNonEmpty(
                s(d.getString("cancelReason")),
                s(d.getString("cancelledReason"))
        );
        Timestamp canceledAt = d.getTimestamp("canceledAt");
        if (canceledAt == null) canceledAt = d.getTimestamp("cancelledAt");
        String cancelledBy = s(d.getString("cancelledBy"));

        // UI - Title sẽ được set trong centerToolbarTitle
        centerToolbarTitle(b.toolbar, "ĐƠN #" + id);
        b.tvOrderId.setText("#" + id);
        b.tvStatus.setText(statusNow);

        b.tvSubtotal.setText(MoneyUtils.formatVnd(subtotal));
        b.tvDiscount.setText(MoneyUtils.formatVnd(discount));
        b.tvShippingFee.setText(MoneyUtils.formatVnd(shippingFee));
        b.tvFinalTotal.setText(MoneyUtils.formatVnd(finalTotal));

        b.tvCreatedAt.setText(createdAt != null ? formatTs(createdAt) : "—");

        // Thông tin thanh toán
        String paymentMethod = s(d.getString("paymentMethod"));
        if (TextUtils.isEmpty(paymentMethod)) paymentMethod = "COD";
        b.tvPaymentMethod.setText(formatPaymentMethod(paymentMethod));

        String paymentStatus = s(d.getString("paymentStatus"));
        if (!TextUtils.isEmpty(paymentStatus)) {
            setVisible(b.rowPaymentStatus, true);
            b.tvPaymentStatus.setText(formatPaymentStatus(paymentStatus));
        } else {
            setVisible(b.rowPaymentStatus, false);
        }

        String transactionId = s(d.getString("transactionId"));
        if (!TextUtils.isEmpty(transactionId)) {
            setVisible(b.rowTransactionId, true);
            b.tvTransactionId.setText(transactionId);
        } else {
            setVisible(b.rowTransactionId, false);
        }

        String voucherCode = s(d.getString("voucherCode"));
        if (!TextUtils.isEmpty(voucherCode)) {
            setVisible(b.rowVoucher, true);
            b.tvVoucherCode.setText(voucherCode);
        } else {
            setVisible(b.rowVoucher, false);
        }

        String shippingLabel = s(d.getString("shippingLabel"));
        if (!TextUtils.isEmpty(shippingLabel)) {
            setVisible(b.rowShipping, true);
            b.tvShippingLabel.setText(shippingLabel);
        } else {
            setVisible(b.rowShipping, false);
        }

        setVisible(b.tvConfirmedAt, confirmedAt != null);
        if (confirmedAt != null) b.tvConfirmedAt.setText("Xác nhận: " + formatTs(confirmedAt));

        setVisible(b.tvFinishedAt, finishedAt != null);
        if (finishedAt != null) b.tvFinishedAt.setText("Hoàn tất: " + formatTs(finishedAt));

        setVisible(b.tvConfirmedBy, !TextUtils.isEmpty(confirmedBy));
        if (!TextUtils.isEmpty(confirmedBy)) b.tvConfirmedBy.setText("Người xác nhận: " + confirmedBy);

        // địa chỉ
        String addressStr = s(d.getString("address"));
        Map<String, Object> addressObj = null;
        Object addressAny = d.get("addressObj");
        if (addressAny instanceof Map) {
            addressObj = (Map<String, Object>) addressAny;
        }
        if (addressObj != null && !addressObj.isEmpty()) {
            String name = s(addressObj.get("fullName"));
            String phone = s(addressObj.get("phone"));
            String display = s(addressObj.get("display"));
            b.tvReceiver.setText(join(name, phone));
            b.tvAddressLines.setText(!TextUtils.isEmpty(display) ? display : "—");
        } else {
            b.tvReceiver.setText("—");
            b.tvAddressLines.setText(!TextUtils.isEmpty(addressStr) ? addressStr : "Chưa có địa chỉ");
        }

        // items — GIỮ API CŨ
        itemsAdapter.submit(mapOrderLines(d.get("items")));

        // block huỷ
        renderCancelBlock(statusNow, cancelReason, canceledAt, cancelledBy);

        // nút theo trạng thái
        updateButtonsByStatus();
        // Enable các nút sau khi đã xác định trạng thái
        lockActionButtons(false);
        
        // Tắt loading sau khi bind xong (thông tin user sẽ load async và cập nhật sau)
        setLoading(false);
    }

    private void updateButtonsByStatus() {
        boolean isPending = "PENDING".equalsIgnoreCase(statusNow);
        setVisible(b.btnAdminConfirm, isPending);
        setVisible(b.btnAdminCancel,  isPending);
    }

    /** Pending → FINISHED, sau đó mới cộng loyaltyPoints + totalSpent */
    @SuppressLint("SetTextI18n")
    private void doConfirm() {
        if (TextUtils.isEmpty(orderId)) return;

        lockActionButtons(true);
        setLoading(true);

        final DocumentReference orderRef = db.collection("orders").document(orderId);
        db.runTransaction((Transaction.Function<Void>) tr -> {
            DocumentSnapshot snap = tr.get(orderRef);
            String cur = s(snap.getString("status"));
            if (!"PENDING".equalsIgnoreCase(cur)) {
                throw new IllegalStateException("Chỉ xác nhận đơn từ trạng thái PENDING.");
            }
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "FINISHED");
            updates.put("confirmedAt", FieldValue.serverTimestamp());
            updates.put("finishedAt", FieldValue.serverTimestamp());
            updates.put("confirmedBy", getAdminEmail());
            tr.update(orderRef, updates);
            return null;
        }).addOnSuccessListener(v -> {
            toast("✅ Đã xác nhận đơn");
            statusNow = "FINISHED";
            b.tvStatus.setText("FINISHED");
            updateButtonsByStatus();
            writeInboxForUser(
                    userId,
                    "order_done",
                    "Đơn hàng " + orderId + " đã hoàn thành",
                    orderId,
                    null
            );

            // === CHỈ TẠI ĐÂY mới cộng điểm / tổng chi tiêu ===
            awardPointsForFinished(orderId, userId);

        }).addOnFailureListener(e -> toastLong("Lỗi xác nhận: " + e.getMessage())).addOnCompleteListener(t -> {
            lockActionButtons(false);
            setLoading(false);
        });
    }

    private void showCancelDialog() {
        if (TextUtils.isEmpty(orderId)) return;

        View view = LayoutInflater.from(this).inflate(R.layout._dialog_cancel_reason, null, false);
        TextInputEditText edt = view.findViewById(R.id.edtReason);
        if (edt != null) {
            edt.setHint("Nhập lý do huỷ đơn");
            edt.setText("");
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Huỷ đơn hàng")
                .setMessage("Bạn chắc chắn muốn huỷ đơn này?")
                .setView(view)
                .setNegativeButton("Đóng", null)
                .setPositiveButton("Huỷ đơn", (d, w) -> {
                    String reason = edt != null ? String.valueOf(edt.getText()).trim() : "";
                    doCancel(reason);
                })
                .show();
    }

    @SuppressLint("SetTextI18n")
    private void doCancel(String reason) {
        if (TextUtils.isEmpty(orderId)) return;

        lockActionButtons(true);
        setLoading(true);

        final DocumentReference orderRef = db.collection("orders").document(orderId);
        db.runTransaction((Transaction.Function<Void>) tr -> {
            DocumentSnapshot snap = tr.get(orderRef);
            String cur = s(snap.getString("status"));
            if (!"PENDING".equalsIgnoreCase(cur)) {
                throw new IllegalStateException("Chỉ huỷ đơn từ trạng thái PENDING.");
            }
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "CANCELLED");
            // Ghi cả 2 kiểu khoá để tương thích
            updates.put("cancelReason", reason);
            updates.put("canceledAt", FieldValue.serverTimestamp());
            updates.put("cancelledReason", reason);
            updates.put("cancelledAt", FieldValue.serverTimestamp());
            updates.put("cancelledBy", getAdminEmail());
            tr.update(orderRef, updates);
            return null;
        }).addOnSuccessListener(v -> {
            toast("🚫 Đã huỷ đơn");
            statusNow = "CANCELLED";
            b.tvStatus.setText("CANCELLED");
            renderCancelBlock(
                    statusNow,
                    reason,
                    null,
                    getAdminEmail()
            );
            updateButtonsByStatus();
            writeInboxForUser(
                    userId,
                    "order_cancelled",
                    "Đơn hàng " + orderId + " đã bị huỷ" + (TextUtils.isEmpty(reason) ? "" : (": " + reason)),
                    orderId,
                    reason
            );
        }).addOnFailureListener(e -> toastLong("Lỗi huỷ: " + e.getMessage())).addOnCompleteListener(t -> {
            lockActionButtons(false);
            setLoading(false);
        });
    }

    /** Đọc total/finalTotal rồi cộng cả loyaltyPoints (có hết hạn) và rewardPoints (đổi quà) */
    private void awardPointsForFinished(@Nullable String orderId, @Nullable String userId) {
        if (TextUtils.isEmpty(orderId) || TextUtils.isEmpty(userId)) return;

        ordersRepository.fetchOrderById(orderId, new OrdersRepository.OnOrderListener() {
            @Override
            public void onOrderReceived(@NonNull com.pro.milkteaapp.models.Order order) {
                long finalTotal = 0L;
                // getFinalTotal() và getTotal() trả về double (primitive), không thể null
                if (order.getFinalTotal() > 0) {
                    finalTotal = (long) order.getFinalTotal();
                } else if (order.getTotal() > 0) {
                    finalTotal = (long) order.getTotal();
                }
                if (finalTotal <= 0) return;

                // Tích lũy cả 2 loại điểm: loyalty points (có hết hạn) và reward points (đổi quà)
                addLoyaltyPointsForTier(userId, finalTotal, orderId);
                addRewardPoints(userId, finalTotal);
                updateTotalSpent(userId, finalTotal);
            }

            @Override
            public void onError(@NonNull Exception error) {
                // log nhẹ, không chặn UI
            }
        });
    }

    private void writeInboxForUser(String userId, String type, String message, String orderId, @Nullable String reason) {
        if (TextUtils.isEmpty(userId)) return;
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", type);
        msg.put("orderId", orderId);
        msg.put("message", message);
        if (!TextUtils.isEmpty(reason)) msg.put("reason", reason);
        msg.put("createdAt", FieldValue.serverTimestamp());
        msg.put("read", false);

        db.collection("users").document(userId)
                .collection("inbox")
                .add(msg);
    }

    // ===== Helpers =====
    @SuppressLint("SetTextI18n")
    private void renderCancelBlock(String status, String cancelReason, @Nullable Timestamp canceledAt, String cancelledBy) {
        boolean show = "CANCELLED".equalsIgnoreCase(status)
                || !TextUtils.isEmpty(cancelReason)
                || canceledAt != null;

        setVisible(b.cardCancelInfo, show);

        if (!show) return;

        b.tvCancelReason.setText(!TextUtils.isEmpty(cancelReason) ? cancelReason : "—");

        if (canceledAt != null) {
            b.tvCanceledAt.setText("Thời gian huỷ: " + formatTs(canceledAt));
            setVisible(b.tvCanceledAt, true);
        } else {
            setVisible(b.tvCanceledAt, false);
        }

        if (!TextUtils.isEmpty(cancelledBy)) {
            b.tvCanceledBy.setText("Huỷ bởi: " + cancelledBy);
            setVisible(b.tvCanceledBy, true);
        } else {
            setVisible(b.tvCanceledBy, false);
        }
    }

    private static String firstNonEmpty(String... vals) {
        if (vals == null) return "";
        for (String v : vals) {
            if (!TextUtils.isEmpty(v)) return v;
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private List<OrderLine> mapOrderLines(Object itemsObj) {
        List<OrderLine> lines = new ArrayList<>();
        if (itemsObj instanceof List<?>) {
            for (Object o : (List<?>) itemsObj) {
                if (o instanceof Map<?, ?>) {
                    Map<String, Object> m = (Map<String, Object>) o;
                    lines.add(OrderLine.fromMap(m));
                }
            }
        }
        return lines;
    }

    private void lockActionButtons(boolean lock) {
        b.btnAdminConfirm.setEnabled(!lock);
        b.btnAdminCancel.setEnabled(!lock);
    }

    private void setVisible(View v, boolean visible) {
        v.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setLoading(boolean loading) {
        b.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private String getAdminEmail() {
        return FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getEmail()
                : null;
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
    private void toastLong(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

    private static String s(Object o) { return o == null ? "" : String.valueOf(o); }
    private static String join(String a, String b) {
        boolean ea = !TextUtils.isEmpty(a), eb = !TextUtils.isEmpty(b);
        if (ea && eb) return a + " • " + b;
        return ea ? a : (eb ? b : "");
    }
    private static double nz(Double d) { return d == null ? 0d : d; }
    private static String formatTs(Timestamp ts) {
        if (ts == null) return "";
        return DateFormat.format("HH:mm dd/MM/yyyy", ts.toDate()).toString();
    }

    /* ==== Cộng loyaltyPoints (có hết hạn) sử dụng LoyaltyPointsRepository ==== */
    private void addLoyaltyPointsForTier(String userId, long totalAmount, String orderId) {
        if (TextUtils.isEmpty(userId) || totalAmount <= 0 || TextUtils.isEmpty(orderId)) return;

        final long pointsToAdd = totalAmount / 10_000L; // 10k = 1 điểm
        if (pointsToAdd == 0) return;

        // Sử dụng LoyaltyPointsRepository để cộng điểm có thời gian hết hạn
        com.pro.milkteaapp.repository.LoyaltyPointsRepository loyaltyRepo = 
                new com.pro.milkteaapp.repository.LoyaltyPointsRepository();
        
        loyaltyRepo.addLoyaltyPoints(userId, pointsToAdd, orderId, 
                new com.pro.milkteaapp.repository.LoyaltyPointsRepository.OnLoyaltyPointsListener() {
            @Override
            public void onSuccess(long totalPoints) {
                // Điểm đã được cộng thành công
            }

            @Override
            public void onError(@NonNull Exception error) {
                // Log error silently, không chặn UI
                android.util.Log.e("AdminOrderDetail", "Lỗi cộng loyalty points: " + error.getMessage());
            }
        });
    }

    /* ==== Cộng rewardPoints (đổi quà) ==== */
    private void addRewardPoints(String userId, long totalAmount) {
        if (TextUtils.isEmpty(userId) || totalAmount <= 0) return;

        final long pointsToAdd = totalAmount / 10_000L; // 10k = 1 điểm
        if (pointsToAdd == 0) return;

        // Sử dụng RewardPointsRepository để cộng điểm đổi quà
        com.pro.milkteaapp.repository.RewardPointsRepository rewardRepo = 
                new com.pro.milkteaapp.repository.RewardPointsRepository();
        
        rewardRepo.addRewardPoints(userId, pointsToAdd, 
                new com.pro.milkteaapp.repository.RewardPointsRepository.OnRewardPointsListener() {
            @Override
            public void onSuccess(long totalPoints) {
                // Điểm đổi quà đã được cộng thành công
            }

            @Override
            public void onError(@NonNull Exception error) {
                // Log error silently, không chặn UI
                android.util.Log.e("AdminOrderDetail", "Lỗi cộng reward points: " + error.getMessage());
            }
        });
    }

    private void updateTotalSpent(String userId, long paidAmount) {
        if (TextUtils.isEmpty(userId) || paidAmount <= 0) return;
        usersRepository.incrementTotalSpent(userId, paidAmount, new UsersRepository.OnUserUpdateListener() {
            @Override
            public void onSuccess() {
                // Success
            }

            @Override
            public void onError(@NonNull Exception error) {
                // Log error silently
            }
        });
    }

    private String formatPaymentMethod(String method) {
        if (TextUtils.isEmpty(method)) return "COD";
        return switch (method.toUpperCase()) {
            case "COD" -> "Thanh toán khi nhận";
            case "MOMO" -> "Ví Momo";
            case "VNPAY" -> "VNPAY";
            case "CARD" -> "Thẻ ngân hàng";
            default -> method;
        };
    }

    private String formatPaymentStatus(String status) {
        if (TextUtils.isEmpty(status)) return "—";
        return switch (status.toUpperCase()) {
            case "SUCCESS" -> "Đã thanh toán";
            case "PENDING" -> "Chờ thanh toán";
            case "FAILED" -> "Thanh toán thất bại";
            case "CANCELLED" -> "Đã hủy";
            default -> status;
        };
    }
    
    private android.widget.TextView customTitleView;
    
    private void centerToolbarTitle(com.google.android.material.appbar.MaterialToolbar toolbar, String title) {
        customTitleView = com.pro.milkteaapp.utils.ToolbarUtils.centerToolbarTitle(
                toolbar, title, customTitleView, getSupportActionBar());
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_admin_order_detail, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_chat) {
            openChat();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void openChat() {
        if (TextUtils.isEmpty(userId)) {
            toastLong("Không có thông tin người dùng");
            return;
        }
        
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("userId", userId);
        intent.putExtra("userName", userName != null ? userName : "Người dùng");
        startActivity(intent);
    }
}
