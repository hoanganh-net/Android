package com.pro.milkteaapp.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import com.google.android.material.navigation.NavigationView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.activity.MainActivity;
import com.pro.milkteaapp.adapter.CartAdapter;
import com.pro.milkteaapp.adapter.ComboCartAdapter;
import com.pro.milkteaapp.generator.OrdersIDGenerator;
import com.pro.milkteaapp.databinding.FragmentCartBinding;
import com.pro.milkteaapp.fragment.bottomsheet.CheckoutBottomSheet;
import com.pro.milkteaapp.models.Address;
import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.models.CheckoutInfo;
import com.pro.milkteaapp.repository.VouchersRepository;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CartFragment extends Fragment
        implements CartAdapter.OnItemActionClickListener,
        CheckoutBottomSheet.OnCheckoutConfirmListener {

    private static final String TAG = "CartFragment";

    private FragmentCartBinding binding;
    private CartAdapter adapter;
    private ComboCartAdapter comboAdapter;
    public static List<CartItem> cartItems = new ArrayList<>();
    public static List<com.pro.milkteaapp.models.ComboCartItem> comboCartItems = new ArrayList<>();

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    // ---- Helpers cho lifecycle/UI ----
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean isUiSafe() {
        return isAdded()
                && getView() != null
                && getViewLifecycleOwner().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
    }

    private void runOnUiSafe(Runnable r) {
        if (!isUiSafe()) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            mainHandler.post(() -> {
                if (isUiSafe()) r.run();
            });
        }
    }
    // -----------------------------------

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable android.view.ViewGroup container, @Nullable Bundle s) {
        binding = FragmentCartBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        setupRecyclerView();
        setupClickListeners();
        setupBackPressedHandler();
        setupToolbar();
        setupDrawer();
        updateTotal();
    }
    
    private void setupToolbar() {
        if (getActivity() == null || binding == null) return;
        androidx.appcompat.app.AppCompatActivity activity = (androidx.appcompat.app.AppCompatActivity) getActivity();
        activity.setSupportActionBar(binding.toolbar);
        
        // Setup drawer button click
        View btnDrawer = binding.getRoot().findViewById(R.id.btnDrawer);
        if (btnDrawer != null) {
            btnDrawer.setOnClickListener(v -> {
                if (binding.drawerLayout != null) {
                    binding.drawerLayout.openDrawer(GravityCompat.END);
                }
            });
        }
    }
    
    private String selectedFilter = "Tất cả";
    
    private void setupDrawer() {
        if (binding == null) return;
        
        // Setup drawer menu click
        if (binding.navigationView != null) {
            binding.navigationView.setNavigationItemSelectedListener(item -> {
                int itemId = item.getItemId();
                String filter = "Tất cả";
                
                if (itemId == R.id.menu_all) {
                    filter = "Tất cả";
                } else if (itemId == R.id.menu_products) {
                    filter = "Sản phẩm";
                } else if (itemId == R.id.menu_combo) {
                    filter = "Combo";
                }
                
                selectedFilter = filter;
                filterCartItems();
                
                if (binding.drawerLayout != null) {
                    binding.drawerLayout.closeDrawer(GravityCompat.END);
                }
                return true;
            });
        }
    }
    
    private void filterCartItems() {
        updateRecyclerViewAdapter();
    }
    
    private void updateRecyclerViewAdapter() {
        if (binding == null || adapter == null || comboAdapter == null) return;
        
        androidx.recyclerview.widget.ConcatAdapter concatAdapter;
        
        if ("Sản phẩm".equals(selectedFilter)) {
            // Chỉ hiển thị sản phẩm thường
            concatAdapter = new androidx.recyclerview.widget.ConcatAdapter(adapter);
        } else if ("Combo".equals(selectedFilter)) {
            // Chỉ hiển thị combo
            concatAdapter = new androidx.recyclerview.widget.ConcatAdapter(comboAdapter);
        } else {
            // Hiển thị tất cả
            concatAdapter = new androidx.recyclerview.widget.ConcatAdapter(adapter, comboAdapter);
        }
        
        binding.cartRecyclerView.setAdapter(concatAdapter);
        updateTotal();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void setupRecyclerView() {
        if (getContext() == null) return;
        binding.cartRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        // Tạo adapter cho sản phẩm thường
        adapter = new CartAdapter(getContext(), cartItems, new CartAdapter.OnItemActionClickListener() {
            @Override
            public void onItemRemoved(int position) {
                CartFragment.this.onItemRemoved(position);
            }

            @Override
            public void onQuantityIncreased(int position) {
                CartFragment.this.onQuantityIncreased(position);
            }

            @Override
            public void onQuantityDecreased(int position) {
                CartFragment.this.onQuantityDecreased(position);
            }
        });
        
        // Tạo adapter cho combo
        comboAdapter = new ComboCartAdapter(getContext(), comboCartItems, new ComboCartAdapter.OnItemActionClickListener() {
            @Override
            public void onItemRemoved(int position) {
                CartFragment.this.onComboItemRemoved(position);
            }

            @Override
            public void onQuantityIncreased(int position) {
                CartFragment.this.onComboQuantityIncreased(position);
            }

            @Override
            public void onQuantityDecreased(int position) {
                CartFragment.this.onComboQuantityDecreased(position);
            }
        });
        
        // Sử dụng ConcatAdapter để hiển thị cả hai loại
        updateRecyclerViewAdapter();

        // Giảm nháy: tắt change animations & cố định kích thước
        RecyclerView rv = binding.cartRecyclerView;
        RecyclerView.ItemAnimator ia = rv.getItemAnimator();
        if (ia instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) ia).setSupportsChangeAnimations(false);
        }
        rv.setHasFixedSize(true);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void setupClickListeners() {
        binding.checkoutButton.setOnClickListener(v -> {
            if (!binding.checkoutButton.isEnabled()) return;
            if (getContext() == null) return;

            if (cartItems.isEmpty() && comboCartItems.isEmpty()) {
                Toast.makeText(getContext(), R.string.empty_cart, Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                Toast.makeText(getContext(), "Vui lòng đăng nhập trước khi đặt hàng", Toast.LENGTH_SHORT).show();
                return;
            }

            // Tạo danh sách kết hợp cho checkout
            java.util.ArrayList<com.pro.milkteaapp.models.CartItem> allItems = new java.util.ArrayList<>(cartItems);
            CheckoutBottomSheet sheet = CheckoutBottomSheet.newInstance(allItems, new java.util.ArrayList<>(comboCartItems));
            sheet.setOnCheckoutConfirmListener(this);
            sheet.show(getParentFragmentManager(), "CheckoutSheet");
        });

        // Xóa nút back - không cần navigation icon
    }

    private void setupBackPressedHandler() {
        OnBackPressedCallback backCallback = new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { handleBack(); }
        };
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), backCallback);
    }

    private void handleBack() {
        if (getActivity() == null) return;
        if (getParentFragmentManager().popBackStackImmediate()) return;
        if (getActivity() instanceof MainActivity) {
            try { ((MainActivity) getActivity()).selectBottomNav(R.id.navigation_home); return; }
            catch (Throwable ignored) {}
        }
        getActivity().finish();
    }

    private void updateTotal() {
        if (binding == null) return;
        double total = 0;
        for (CartItem item : cartItems) total += item.getTotalPrice();
        for (com.pro.milkteaapp.models.ComboCartItem comboItem : comboCartItems) total += comboItem.getTotalPrice();
        binding.totalTextView.setText(MoneyUtils.formatVnd(total));
        binding.checkoutButton.setEnabled(!cartItems.isEmpty() || !comboCartItems.isEmpty());
    }

    @Override public void onResume() {
        super.onResume();
        // KHÔNG gọi notifyDataSetChanged() để tránh chớp toàn list
        // Cập nhật adapter khi resume (có thể có combo mới được thêm)
        if (adapter != null) adapter.notifyDataSetChanged();
        if (comboAdapter != null) comboAdapter.notifyDataSetChanged();
        updateTotal();
    }

    @Override
    public void onItemRemoved(int position) {
        if (getActivity() == null || isDetached()) return;
        if (position < 0 || position >= cartItems.size()) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Xác nhận xóa")
                .setMessage("Bạn có chắc chắn muốn xóa sản phẩm này khỏi giỏ hàng không?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    cartItems.remove(position);
                    if (adapter != null) adapter.notifyItemRemoved(position);
                    updateTotal();
                    Toast.makeText(getContext(), "Đã xóa sản phẩm", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override public void onQuantityIncreased(int position) {
        if (position >= 0 && position < cartItems.size()) {
            cartItems.get(position).increaseQuantity();
            if (adapter != null) adapter.notifyItemChanged(position, "QTY_TOTAL");
            updateTotal();
        }
    }

    @Override public void onQuantityDecreased(int position) {
        if (position >= 0 && position < cartItems.size()) {
            CartItem item = cartItems.get(position);
            if (item.getQuantity() > 1) {
                item.decreaseQuantity();
                if (adapter != null) adapter.notifyItemChanged(position, "QTY_TOTAL");
                updateTotal();
            } else onItemRemoved(position);
        }
    }

    // ===== Combo Cart Actions =====
    public void onComboItemRemoved(int position) {
        if (getActivity() == null || isDetached()) return;
        if (position < 0 || position >= comboCartItems.size()) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Xác nhận xóa")
                .setMessage("Bạn có chắc chắn muốn xóa combo này khỏi giỏ hàng không?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    comboCartItems.remove(position);
                    if (comboAdapter != null) comboAdapter.notifyItemRemoved(position);
                    updateTotal();
                    Toast.makeText(getContext(), "Đã xóa combo", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    public void onComboQuantityIncreased(int position) {
        if (position >= 0 && position < comboCartItems.size()) {
            comboCartItems.get(position).increaseQuantity();
            if (comboAdapter != null) comboAdapter.notifyItemChanged(position, "QTY_TOTAL");
            updateTotal();
        }
    }

    public void onComboQuantityDecreased(int position) {
        if (position >= 0 && position < comboCartItems.size()) {
            com.pro.milkteaapp.models.ComboCartItem item = comboCartItems.get(position);
            if (item.getQuantity() > 1) {
                item.decreaseQuantity();
                if (comboAdapter != null) comboAdapter.notifyItemChanged(position, "QTY_TOTAL");
                updateTotal();
            } else onComboItemRemoved(position);
        }
    }

    // ====== callback từ CheckoutBottomSheet ======
    @Override
    public void onCheckoutConfirmed(@NonNull CheckoutInfo info) {
        Log.d(TAG, "Checkout address = " + info.address);
        placeOrder(info);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void placeOrder(@NonNull CheckoutInfo info) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (getContext() != null) {
                Toast.makeText(getContext(), "Vui lòng đăng nhập trước khi đặt hàng", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        final Context app = requireContext().getApplicationContext();
        if (binding != null) binding.checkoutButton.setEnabled(false);

        // ✅ Dùng custom userId (USRxxxxx) nếu có, fallback Firebase UID khi chưa có
        String customUid = null;
        try { customUid = new SessionManager(app).getUid(); } catch (Throwable ignored) {}
        String effectiveUserId = (customUid != null && !customUid.isEmpty())
                ? customUid : user.getUid();

        Map<String, Object> payload = buildOrderPayload(effectiveUserId, cartItems, info);

        // === DÙNG ID TÙY BIẾN CHO ORDERS ===
        OrdersIDGenerator gen = new OrdersIDGenerator();
        gen.nextOrderId()
                .addOnSuccessListener(orderId ->
                        FirebaseFirestore.getInstance().collection("orders").document(orderId).set(payload)
                                .addOnSuccessListener(aVoid -> recordVoucherUsageIfNeeded(
                                        effectiveUserId, // ✅ đồng bộ với users/{USRxxxxx}
                                        info.voucherCode,
                                        () -> {
                                            cartItems.clear();
                                            comboCartItems.clear();
                                            Toast.makeText(app, app.getString(R.string.order_placed_successfully), Toast.LENGTH_SHORT).show();

                                            runOnUiSafe(() -> {
                                                if (adapter != null) adapter.notifyDataSetChanged();
                                                if (comboAdapter != null) comboAdapter.notifyDataSetChanged();
                                                updateTotal();

                                                Intent i = new Intent(requireActivity(), MainActivity.class);
                                                i.putExtra(MainActivity.EXTRA_TARGET_FRAGMENT, "activities:pending");
                                                startActivity(i);

                                                if (binding != null) binding.checkoutButton.setEnabled(true);
                                            });
                                        },
                                        e -> {
                                            Toast.makeText(app, "Đơn đã tạo, nhưng cập nhật lượt voucher lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();

                                            cartItems.clear();
                                            comboCartItems.clear();
                                            runOnUiSafe(() -> {
                                                if (adapter != null) adapter.notifyDataSetChanged();
                                                if (comboAdapter != null) comboAdapter.notifyDataSetChanged();
                                                updateTotal();
                                                if (binding != null) binding.checkoutButton.setEnabled(true);
                                            });
                                        }))

                                .addOnFailureListener(e -> {
                                    Toast.makeText(app, "Lỗi tạo đơn: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    runOnUiSafe(() -> {
                                        if (binding != null) binding.checkoutButton.setEnabled(true);
                                    });
                                })
                                .addOnCompleteListener(task -> runOnUiSafe(() -> {
                                    if (binding != null) binding.checkoutButton.setEnabled(true);
                                }))
                )
                .addOnFailureListener(e -> {
                    Toast.makeText(app, "Lỗi sinh mã đơn: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    runOnUiSafe(() -> {
                        if (binding != null) binding.checkoutButton.setEnabled(true);
                    });
                });
    }

    /** Build payload đơn hàng theo CheckoutInfo (đã có giảm giá/ship/tổng) */
    private Map<String, Object> buildOrderPayload(String userId, List<CartItem> items, CheckoutInfo info) {
        List<Map<String, Object>> itemMaps = new ArrayList<>();
        // Thêm CartItem vào items array
        for (CartItem ci : items) {
            itemMaps.add(ci.toOrderItemMap());
        }
        // Thêm ComboCartItem vào items array
        Log.d(TAG, "Adding combo items to order payload, comboCartItems size: " + comboCartItems.size());
        for (com.pro.milkteaapp.models.ComboCartItem comboItem : comboCartItems) {
            Map<String, Object> comboMap = comboItem.toOrderItemMap();
            Log.d(TAG, "Combo item map: " + comboMap);
            Log.d(TAG, "Combo item type field: " + comboMap.get("type"));
            itemMaps.add(comboMap);
        }
        Log.d(TAG, "Total items in payload: " + itemMaps.size());

        Map<String, Object> order = new HashMap<>();
        order.put("userId", userId);
        order.put("status", "PENDING");
        order.put("items", itemMaps);

        order.put("subtotal", info.subtotal);
        order.put("discount", info.discount);
        order.put("shippingFee", info.shippingFee);
        order.put("finalTotal", info.grandTotal);

        final String shippingAddr = info.address.trim();
        order.put("address", shippingAddr);
        order.put("shippingAddress", shippingAddr);

        Map<String, Object> addressObj = new HashMap<>();
        if (info.addressObj != null) {
            Address a = info.addressObj;
            addressObj.put("fullName", a.getFullName());
            addressObj.put("phone", a.getPhone());
            addressObj.put("line1", a.getLine1());
            addressObj.put("line2", a.getLine2());
            addressObj.put("city", a.getCity());
            addressObj.put("province", a.getProvince());
            addressObj.put("postalCode", a.getPostalCode());
            addressObj.put("display", a.displayLine());
        } else {
            addressObj.put("display", shippingAddr);
        }
        order.put("addressObj", addressObj);

        order.put("paymentMethod", info.paymentMethod);
        order.put("voucherCode", info.voucherCode);
        order.put("shippingLabel", info.shippingLabel);

        order.put("createdAt", FieldValue.serverTimestamp());
        return order;
    }

    /** Ghi nhận users/{uid}/voucher_usages/{voucherId}.count += 1 nếu có code */
    private void recordVoucherUsageIfNeeded(@NonNull String uid,
                                            @Nullable String voucherCode,
                                            @NonNull Runnable onDone,
                                            @NonNull java.util.function.Consumer<Exception> onError) {
        if (voucherCode == null || voucherCode.trim().isEmpty()) { onDone.run(); return; }

        // Sử dụng VouchersRepository để tìm voucher
        VouchersRepository vouchersRepository = new VouchersRepository();
        vouchersRepository.fetchVoucherByCode(voucherCode.trim().toUpperCase(Locale.getDefault()), new VouchersRepository.OnVoucherListener() {
            @Override
            public void onVoucherReceived(@NonNull com.pro.milkteaapp.models.Voucher voucher) {
                String voucherId = voucher.getId();
                if (voucherId == null || voucherId.isEmpty()) {
                    onDone.run();
                    return;
                }

                // Update voucher_usages subcollection (vẫn dùng direct Firestore vì là subcollection)
                db.collection("users").document(uid)
                        .collection("voucher_usages").document(voucherId)
                        .update("count", com.google.firebase.firestore.FieldValue.increment(1))
                        .addOnSuccessListener(x -> onDone.run())
                        .addOnFailureListener(e -> {
                            Map<String,Object> seed = new HashMap<>();
                            seed.put("count", 1L);
                            db.collection("users").document(uid)
                                    .collection("voucher_usages").document(voucherId)
                                    .set(seed)
                                    .addOnSuccessListener(x -> onDone.run())
                                    .addOnFailureListener(onError::accept);
                        });
            }

            @Override
            public void onError(@NonNull Exception error) {
                // Voucher không tồn tại hoặc lỗi -> không ghi nhận usage
                onDone.run();
            }
        });
    }
}
