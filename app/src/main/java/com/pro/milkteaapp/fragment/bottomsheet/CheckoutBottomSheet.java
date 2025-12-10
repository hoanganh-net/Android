package com.pro.milkteaapp.fragment.bottomsheet;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.adapter.CheckoutItemAdapter;
import com.pro.milkteaapp.databinding.BottomsheetCheckoutBinding;
import com.pro.milkteaapp.fragment.pickersheet.AddressPickerSheet;
import com.pro.milkteaapp.fragment.pickersheet.PaymentMethodPickerSheet;
import com.pro.milkteaapp.fragment.pickersheet.ShippingPickerSheet;
import com.pro.milkteaapp.fragment.pickersheet.VoucherListPickerSheet;
import com.pro.milkteaapp.models.Address;
import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.models.CheckoutInfo;
import com.pro.milkteaapp.models.User;
import com.pro.milkteaapp.models.Voucher;
import com.pro.milkteaapp.repository.AddressRepository;
import com.pro.milkteaapp.utils.LoyaltyPolicy;
import com.pro.milkteaapp.utils.MoneyUtils;
import com.pro.milkteaapp.utils.VoucherUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CheckoutBottomSheet extends BottomSheetDialogFragment {

    public interface OnCheckoutConfirmListener {
        void onCheckoutConfirmed(@NonNull CheckoutInfo info);
    }

    private static final String ARG_ITEMS = "arg_items";
    private static final String ARG_COMBO_ITEMS = "arg_combo_items";

    public static CheckoutBottomSheet newInstance(@NonNull ArrayList<CartItem> items, 
                                                   @NonNull ArrayList<com.pro.milkteaapp.models.ComboCartItem> comboItems) {
        CheckoutBottomSheet f = new CheckoutBottomSheet();
        Bundle b = new Bundle();
        b.putSerializable(ARG_ITEMS, items);
        b.putSerializable(ARG_COMBO_ITEMS, comboItems);
        f.setArguments(b);
        return f;
    }

    private BottomsheetCheckoutBinding b;
    private OnCheckoutConfirmListener listener;
    private final List<CartItem> items = new ArrayList<>();
    private final List<com.pro.milkteaapp.models.ComboCartItem> comboItems = new ArrayList<>();

    // State
    @Nullable private Address selectedAddress;
    private String paymentMethod = "COD";
    @Nullable private Voucher selectedVoucher = null;
    private String voucherCode = "";
    private String shippingLabel = "Tiêu chuẩn (15.000đ)";
    private long shippingFee = 15_000L;

    @Nullable private User currentUser;

    // ====== Prefs lưu địa chỉ đã chọn gần nhất ======
    private static final String PREFS = "checkout_prefs";
    private static final String KEY_LAST_ADDR_ID = "last_checkout_address_id";

    public void setOnCheckoutConfirmListener(OnCheckoutConfirmListener l) {
        this.listener = l;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = BottomsheetCheckoutBinding.inflate(inf, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);

        // Nhận danh sách item
        if (getArguments() != null) {
            Serializable ser = getArguments().getSerializable(ARG_ITEMS);
            if (ser instanceof ArrayList<?>) {
                for (Object o : (ArrayList<?>) ser) {
                    if (o instanceof CartItem) items.add((CartItem) o);
                }
            }
            Serializable comboSer = getArguments().getSerializable(ARG_COMBO_ITEMS);
            if (comboSer != null && comboSer instanceof ArrayList<?>) {
                for (Object o : (ArrayList<?>) comboSer) {
                    if (o instanceof com.pro.milkteaapp.models.ComboCartItem) {
                        comboItems.add((com.pro.milkteaapp.models.ComboCartItem) o);
                    }
                }
            }
        }

        // List sản phẩm (sử dụng CheckoutItemAdapter với cả CartItem và ComboCartItem)
        b.rvItems.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rvItems.setAdapter(new CheckoutItemAdapter(requireContext(), items, comboItems));

        // 1) Tải địa chỉ đã chọn gần nhất (nếu có), nếu không -> 2) lấy địa chỉ mặc định
        loadInitialAddressThenRender();

        // Tải thông tin User để tính giảm giá
        loadUserAndRender();

        // Render ban đầu
        updateSummaryUI();

        // Chọn địa chỉ
        b.rowAddress.setOnClickListener(v1 -> {
            AddressPickerSheet sheet = new AddressPickerSheet();
            sheet.setListener(addr -> {
                selectedAddress = addr;
                // Lưu lại ID địa chỉ đã chọn để lần sau mở Checkout sẽ ưu tiên hiển thị
                if (addr != null && addr.getId() != null) {
                    saveLastAddressId(addr.getId());
                }
                setText(b.tvAddressValue, addr != null ? addr.displayLine() : "Chọn địa chỉ");
                updateSummaryUI();
            });
            sheet.show(getParentFragmentManager(), "AddressPicker");
        });

        // Chọn phương thức thanh toán
        b.rowPayment.setOnClickListener(v12 -> {
            PaymentMethodPickerSheet sheet = new PaymentMethodPickerSheet();
            sheet.setListener(pm -> {
                paymentMethod = pm;
                setText(b.tvPaymentValue, pm);
                updateSummaryUI();
            });
            sheet.show(getParentFragmentManager(), "PaymentPicker");
        });

        // Chọn voucher từ danh sách hợp lệ
        b.rowVoucher.setOnClickListener(v13 -> {
            Bundle args = new Bundle();
            args.putLong("subtotal", calcSubtotal());
            args.putString("payment", paymentMethod);
            // ✅ Truyền custom userId (USRxxxxx) cho sheet lọc voucher theo user
            String uid = getEffectiveUserId(requireContext());
            args.putString("userId", uid);

            VoucherListPickerSheet sheet = new VoucherListPickerSheet();
            sheet.setArguments(args);
            sheet.setListener(vSelected -> {
                if (vSelected != null) {
                    // Validate voucher trước khi áp dụng
                    long currentSubtotal = calcSubtotal();
                    String validationError = validateVoucher(vSelected, currentSubtotal, paymentMethod);
                    if (validationError != null) {
                        Toast.makeText(requireContext(), validationError, Toast.LENGTH_LONG).show();
                        return;
                    }
                }
                selectedVoucher = vSelected;
                voucherCode = (vSelected != null ? safe(vSelected.getCode()) : "");
                setText(b.tvVoucherValue, voucherCode.isEmpty() ? "Không áp dụng" : voucherCode);
                updateSummaryUI();
            });
            sheet.show(getParentFragmentManager(), "VoucherPickerList");
        });

        // Chọn phương thức giao hàng
        b.rowShipping.setOnClickListener(v14 -> {
            ShippingPickerSheet sheet = new ShippingPickerSheet();
            sheet.setListener((label, fee) -> {
                shippingLabel = label;
                shippingFee = (long) fee;
                setText(b.tvShippingValue, label);
                updateSummaryUI();
            });
            sheet.show(getParentFragmentManager(), "ShippingPicker");
        });

        // Xác nhận
        b.btnConfirm.setOnClickListener(v15 -> {
            if (selectedAddress == null) {
                setText(b.tvAddressValue, "Vui lòng chọn địa chỉ");
                return;
            }
            if (listener != null) listener.onCheckoutConfirmed(buildInfo());
            dismiss();
        });
    }

    // ====== Default Address logic ======

    private void loadInitialAddressThenRender() {
        String uid = getEffectiveUserId(requireContext());
        if (uid == null) {
            // Chưa login -> để trống
            setText(b.tvAddressValue, "Chọn địa chỉ");
            return;
        }

        String lastId = loadLastAddressId();
        AddressRepository addressRepository = new AddressRepository(requireContext());

        if (lastId != null && !lastId.trim().isEmpty()) {
            // Ưu tiên địa chỉ đã chọn lần trước
            addressRepository.fetchById(lastId, new AddressRepository.OnAddressFetched() {
                @Override
                public void onResult(@Nullable Address a) {
                    if (a != null) {
                        selectedAddress = a;
                        setText(b.tvAddressValue, a.displayLine());
                        updateSummaryUI();
                    } else {
                        // Không còn tồn tại -> fallback sang địa chỉ mặc định
                        fetchDefaultAddress(addressRepository);
                    }
                }
            }, e -> fetchDefaultAddress(addressRepository));
        } else {
            // Chưa có lựa chọn trước đó -> lấy mặc định
            fetchDefaultAddress(addressRepository);
        }
    }

    private void fetchDefaultAddress(@NonNull AddressRepository addressRepository) {
        addressRepository.fetchDefault(new AddressRepository.OnAddressFetched() {
            @Override
            public void onResult(@Nullable Address a) {
                selectedAddress = a;
                setText(b.tvAddressValue, a != null ? a.displayLine() : "Chọn địa chỉ");
                updateSummaryUI();
            }
        }, e -> setText(b.tvAddressValue, "Chọn địa chỉ"));
    }

    private void saveLastAddressId(@NonNull String id) {
        SharedPreferences sp = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_LAST_ADDR_ID, id).apply();
    }

    @Nullable
    private String loadLastAddressId() {
        SharedPreferences sp = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getString(KEY_LAST_ADDR_ID, null);
    }

    // ====== Tính tiền và render ======

    private void setText(@Nullable TextView tv, @NonNull String txt) {
        if (tv != null) tv.setText(txt);
    }

    private long calcSubtotal() {
        long subtotal = 0L;
        for (CartItem it : items) {
            subtotal += it.getUnitPrice() * it.getQuantity();
        }
        // Thêm combo items
        for (com.pro.milkteaapp.models.ComboCartItem comboItem : comboItems) {
            subtotal += comboItem.getUnitPrice() * comboItem.getQuantity();
        }
        return subtotal;
    }

    private void loadUserAndRender() {
        String uid = getEffectiveUserId(requireContext());
        if (uid == null) {
            updateSummaryUI();
            return;
        }

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        currentUser = doc.toObject(User.class);
                    }
                    updateSummaryUI();
                })
                .addOnFailureListener(e -> updateSummaryUI());
    }

    private void updateSummaryUI() {
        long subtotal = calcSubtotal();

        // Voucher
        long orderDisc = 0L, shipDisc = 0L;
        if (selectedVoucher != null) {
            VoucherUtils.DiscountResult r = VoucherUtils.calc(selectedVoucher, subtotal, shippingFee);
            orderDisc = r.orderDiscount;
            shipDisc = r.shippingDiscount;
        }
        long voucherDiscount = orderDisc + shipDisc;

        // Loyalty theo LoyaltyPolicy
        double loyaltyPercent = 0.0;
        if (currentUser != null) {
            String t = currentUser.getLoyaltyTier();
            if (t == null || t.isEmpty()) {
                long pts = currentUser.getLoyaltyPoints();
                t = LoyaltyPolicy.tierForPoints(pts);
            }
            loyaltyPercent = LoyaltyPolicy.discountPercent(t);
        }
        long loyaltyDiscount = (long) (subtotal * loyaltyPercent);

        long totalDiscount = voucherDiscount + loyaltyDiscount;
        long total = Math.max(0L, subtotal - totalDiscount + shippingFee);

        b.tvSubtotal.setText(MoneyUtils.formatVnd(subtotal));
        b.tvDiscount.setText(String.format(Locale.getDefault(), "- %s", MoneyUtils.formatVnd(voucherDiscount)));
        b.tvLoyaltyDiscount.setText(String.format(Locale.getDefault(), "- %s", MoneyUtils.formatVnd(loyaltyDiscount)));
        b.tvShipping.setText(MoneyUtils.formatVnd(shippingFee));
        b.tvFinal.setText(MoneyUtils.formatVnd(total));

        if (selectedAddress != null) setText(b.tvAddressValue, selectedAddress.displayLine());
        setText(b.tvVoucherValue, voucherCode.isEmpty() ? "Không áp dụng" : voucherCode);
        setText(b.tvShippingValue, shippingLabel);
        setText(b.tvPaymentValue, paymentMethod);
    }

    private CheckoutInfo buildInfo() {
        long subtotal = calcSubtotal();

        long orderDisc = 0L, shipDisc = 0L;
        if (selectedVoucher != null) {
            VoucherUtils.DiscountResult r = VoucherUtils.calc(selectedVoucher, subtotal, shippingFee);
            orderDisc = r.orderDiscount;
            shipDisc = r.shippingDiscount;
        }
        long voucherDiscount = orderDisc + shipDisc;

        // Loyalty theo LoyaltyPolicy
        double loyaltyPercent = 0.0;
        if (currentUser != null) {
            String t = currentUser.getLoyaltyTier();
            if (t == null || t.isEmpty()) {
                long pts = currentUser.getLoyaltyPoints();
                t = LoyaltyPolicy.tierForPoints(pts);
            }
            loyaltyPercent = LoyaltyPolicy.discountPercent(t);
        }
        long loyaltyDiscount = (long) (subtotal * loyaltyPercent);

        long totalDiscount = voucherDiscount + loyaltyDiscount;
        long total = Math.max(0L, subtotal - totalDiscount + shippingFee);

        String addressString = selectedAddress != null ? selectedAddress.displayLine() : "";

        return new CheckoutInfo(
                addressString,
                selectedAddress,
                paymentMethod,
                voucherCode,
                shippingLabel,
                subtotal,
                totalDiscount,
                shippingFee,
                total
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    /**
     * Validate voucher trước khi áp dụng
     * @return null nếu hợp lệ, error message nếu không hợp lệ
     */
    @Nullable
    private String validateVoucher(@NonNull Voucher voucher, long subtotal, @Nullable String paymentMethod) {
        // Kiểm tra thời hạn
        if (!VoucherUtils.isInRangeNow(voucher)) {
            return "Voucher đã hết hạn";
        }

        // Kiểm tra minOrder
        if (voucher.getMinOrder() > 0 && subtotal < voucher.getMinOrder()) {
            return String.format(Locale.getDefault(), "Đơn hàng tối thiểu %s để sử dụng voucher này", 
                    MoneyUtils.formatVnd(voucher.getMinOrder()));
        }

        // Kiểm tra kênh thanh toán
        if (paymentMethod != null && voucher.getAllowedChannels() != null && !voucher.getAllowedChannels().isEmpty()) {
            boolean allowed = false;
            for (String channel : voucher.getAllowedChannels()) {
                if (paymentMethod.equalsIgnoreCase(channel)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                return "Voucher không áp dụng cho phương thức thanh toán này";
            }
        }

        // Kiểm tra active
        if (!voucher.isActive()) {
            return "Voucher không còn hoạt động";
        }

        return null; // Hợp lệ
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** Lấy userId thống nhất: ưu tiên USRxxxxx từ SessionManager, fallback Firebase UID */
    @Nullable
    private String getEffectiveUserId(@NonNull Context ctx) {
        try {
            String custom = new SessionManager(ctx.getApplicationContext()).getUid();
            if (custom != null && !custom.isEmpty()) return custom;
        } catch (Throwable ignored) {}
        return (FirebaseAuth.getInstance().getCurrentUser() != null)
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
    }
}
