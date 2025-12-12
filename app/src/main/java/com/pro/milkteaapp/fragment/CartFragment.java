package com.pro.milkteaapp.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.activity.MainActivity;
import com.pro.milkteaapp.adapter.CartAdapter;
import com.pro.milkteaapp.adapter.ComboCartAdapter;
import com.pro.milkteaapp.constants.FirestoreConstants;
import com.pro.milkteaapp.generator.OrdersIDGenerator;
import com.pro.milkteaapp.databinding.FragmentCartBinding;
import com.pro.milkteaapp.fragment.bottomsheet.CheckoutBottomSheet;
import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.models.CheckoutInfo;
import com.pro.milkteaapp.repository.VouchersRepository;
import com.pro.milkteaapp.utils.CartManager;
import com.pro.milkteaapp.utils.CartFilterHelper;
import com.pro.milkteaapp.utils.MoneyUtils;
import com.pro.milkteaapp.utils.OrderPayloadBuilder;

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
    private com.pro.milkteaapp.utils.CartManager cartManager;
    private CartFilterHelper filterHelper;

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
        cartManager = CartManager.getInstance();

        setupRecyclerView();
        setupClickListeners();
        setupBackPressedHandler();
        setupToolbar();
        setupDrawer();
        updateTotal();
    }
    
    private void initializeFilterHelper() {
        if (getContext() != null && adapter != null && comboAdapter != null) {
            filterHelper = new CartFilterHelper(getContext(), adapter, comboAdapter);
        }
    }
    
    private void setupToolbar() {
        if (getActivity() == null || binding == null) return;
        androidx.appcompat.app.AppCompatActivity activity = (androidx.appcompat.app.AppCompatActivity) getActivity();
        activity.setSupportActionBar(binding.toolbar);
        
        // Setup drawer button click
        View btnDrawer = binding.getRoot().findViewById(R.id.btnDrawer);
        if (btnDrawer != null) {
            btnDrawer.setOnClickListener(v -> binding.drawerLayout.openDrawer(GravityCompat.END));
        }
    }
    
    private void setupDrawer() {
        if (binding == null || getContext() == null) return;
        
        // Setup drawer menu click
        // Set default selection là ALL (sau khi menu đã được inflate)
        binding.navigationView.post(() -> {
            if (isAdded()) {
                binding.navigationView.setCheckedItem(R.id.menu_all);
            }
        });

        binding.navigationView.setNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            String filter = getString(R.string.filter_all);

            if (itemId == R.id.menu_all) {
                filter = getString(R.string.filter_all);
            } else if (itemId == R.id.menu_products) {
                filter = getString(R.string.filter_products);
            } else if (itemId == R.id.menu_combo) {
                filter = getString(R.string.filter_combo);
            }

            // Cập nhật checked state
            binding.navigationView.setCheckedItem(item.getItemId());

            filterCartItems(filter);

            binding.drawerLayout.closeDrawer(GravityCompat.END);
            return true;
        });
    }
    
    private void filterCartItems(@NonNull String filter) {
        if (filterHelper == null || binding == null) return;
        binding.cartRecyclerView.setAdapter(filterHelper.getFilteredAdapter(filter));
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
        adapter = new CartAdapter(getContext(), cartManager.getCartItems(), new CartAdapter.OnItemActionClickListener() {
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
        comboAdapter = new ComboCartAdapter(getContext(), cartManager.getComboCartItems(), new ComboCartAdapter.OnItemActionClickListener() {
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
        
        // Initialize filter helper
        initializeFilterHelper();
        
        // Sử dụng ConcatAdapter để hiển thị cả hai loại
        if (filterHelper != null) {
            binding.cartRecyclerView.setAdapter(filterHelper.getFilteredAdapter(filterHelper.getSelectedFilter()));
        }

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
            if (!isAdded() || getContext() == null) return;

            if (cartManager.getCartItems().isEmpty() && cartManager.getComboCartItems().isEmpty()) {
                Toast.makeText(requireContext(), R.string.empty_cart, Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                Toast.makeText(requireContext(), getString(R.string.please_login_first), Toast.LENGTH_SHORT).show();
                return;
            }

            // Tạo danh sách kết hợp cho checkout
            java.util.ArrayList<com.pro.milkteaapp.models.CartItem> allItems = new java.util.ArrayList<>(cartManager.getCartItems());
            CheckoutBottomSheet sheet = CheckoutBottomSheet.newInstance(allItems, new java.util.ArrayList<>(cartManager.getComboCartItems()));
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
            try { 
                ((MainActivity) getActivity()).selectBottomNav(R.id.navigation_home); 
                return; 
            } catch (Throwable e) {
                Log.w(TAG, "Failed to select bottom nav: " + e.getMessage());
            }
        }
        getActivity().finish();
    }

    private void updateTotal() {
        if (binding == null) return;
        double total = 0;
        for (CartItem item : cartManager.getCartItems()) total += item.getTotalPrice();
        for (com.pro.milkteaapp.models.ComboCartItem comboItem : cartManager.getComboCartItems()) total += comboItem.getTotalPrice();
        binding.totalTextView.setText(MoneyUtils.formatVnd(total));
        binding.checkoutButton.setEnabled(!cartManager.getCartItems().isEmpty() || !cartManager.getComboCartItems().isEmpty());
    }

    @SuppressLint("NotifyDataSetChanged")
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
        if (position < 0 || position >= cartManager.getCartItemsCount()) return;

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.confirm_delete))
                .setMessage("Bạn có chắc chắn muốn xóa sản phẩm này khỏi giỏ hàng không?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    cartManager.removeCartItem(position);
                    if (adapter != null) adapter.notifyItemRemoved(position);
                    updateTotal();
                    if (isAdded() && getContext() != null) {
                        Toast.makeText(getContext(), getString(R.string.product_deleted), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override public void onQuantityIncreased(int position) {
        List<CartItem> items = cartManager.getCartItems();
        if (position >= 0 && position < items.size()) {
            items.get(position).increaseQuantity();
            if (adapter != null) adapter.notifyItemChanged(position, "QTY_TOTAL");
            updateTotal();
        }
    }

    @Override public void onQuantityDecreased(int position) {
        List<CartItem> items = cartManager.getCartItems();
        if (position >= 0 && position < items.size()) {
            CartItem item = items.get(position);
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
        if (position < 0 || position >= cartManager.getComboCartItemsCount()) return;

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.confirm_delete))
                .setMessage("Bạn có chắc chắn muốn xóa combo này khỏi giỏ hàng không?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    cartManager.removeComboCartItem(position);
                    if (comboAdapter != null) comboAdapter.notifyItemRemoved(position);
                    updateTotal();
                    if (isAdded() && getContext() != null) {
                        Toast.makeText(getContext(), getString(R.string.combo_deleted), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    public void onComboQuantityIncreased(int position) {
        List<com.pro.milkteaapp.models.ComboCartItem> items = cartManager.getComboCartItems();
        if (position >= 0 && position < items.size()) {
            items.get(position).increaseQuantity();
            if (comboAdapter != null) comboAdapter.notifyItemChanged(position, "QTY_TOTAL");
            updateTotal();
        }
    }

    public void onComboQuantityDecreased(int position) {
        List<com.pro.milkteaapp.models.ComboCartItem> items = cartManager.getComboCartItems();
        if (position >= 0 && position < items.size()) {
            com.pro.milkteaapp.models.ComboCartItem item = items.get(position);
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
        Log.d(TAG, "Checkout address = " + info.address());
        placeOrder(info);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void placeOrder(@NonNull CheckoutInfo info) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (isAdded() && getContext() != null) {
                Toast.makeText(getContext(), "Vui lòng đăng nhập trước khi đặt hàng", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        final Context app = requireContext().getApplicationContext();
        if (binding != null) binding.checkoutButton.setEnabled(false);

        // ✅ Dùng custom userId (USRxxxxx) nếu có, fallback Firebase UID khi chưa có
        String customUid = null;
        try { 
            customUid = new SessionManager(app).getUid(); 
        } catch (Throwable e) {
            Log.w(TAG, "Failed to get UID from SessionManager: " + e.getMessage());
        }
        String effectiveUserId = (customUid != null && !customUid.isEmpty())
                ? customUid : user.getUid();

        Map<String, Object> payload = OrderPayloadBuilder.buildOrderPayload(
                effectiveUserId, 
                cartManager.getCartItems(), 
                cartManager.getComboCartItems(), 
                info);

        // === DÙNG ID TÙY BIẾN CHO ORDERS ===
        OrdersIDGenerator gen = new OrdersIDGenerator();
        gen.nextOrderId()
                .addOnSuccessListener(orderId ->
                        FirebaseFirestore.getInstance().collection(FirestoreConstants.COLLECTION_ORDERS).document(orderId).set(payload)
                                .addOnSuccessListener(aVoid -> recordVoucherUsageIfNeeded(
                                        effectiveUserId, // ✅ đồng bộ với users/{USRxxxxx}
                                        info.voucherCode(),
                                        () -> {
                                            cartManager.clearAll();
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
                                            Toast.makeText(app, app.getString(R.string.voucher_update_error, e.getMessage()), Toast.LENGTH_LONG).show();

                                            cartManager.clearAll();
                                            runOnUiSafe(() -> {
                                                if (adapter != null) adapter.notifyDataSetChanged();
                                                if (comboAdapter != null) comboAdapter.notifyDataSetChanged();
                                                updateTotal();
                                                if (binding != null) binding.checkoutButton.setEnabled(true);
                                            });
                                        }))

                                .addOnFailureListener(e -> {
                                    Toast.makeText(app, app.getString(R.string.create_order_error, e.getMessage()), Toast.LENGTH_LONG).show();
                                    runOnUiSafe(() -> {
                                        if (binding != null) binding.checkoutButton.setEnabled(true);
                                    });
                                })
                                .addOnCompleteListener(task -> runOnUiSafe(() -> {
                                    if (binding != null) binding.checkoutButton.setEnabled(true);
                                }))
                )
                .addOnFailureListener(e -> {
                    Toast.makeText(app, app.getString(R.string.generate_order_id_error, e.getMessage()), Toast.LENGTH_LONG).show();
                    runOnUiSafe(() -> {
                        if (binding != null) binding.checkoutButton.setEnabled(true);
                    });
                });
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
                db.collection(FirestoreConstants.COLLECTION_USERS).document(uid)
                        .collection(FirestoreConstants.COLLECTION_VOUCHER_USAGES).document(voucherId)
                        .update("count", com.google.firebase.firestore.FieldValue.increment(1))
                        .addOnSuccessListener(x -> onDone.run())
                        .addOnFailureListener(e -> {
                            Map<String,Object> seed = new HashMap<>();
                            seed.put("count", 1L);
                            db.collection(FirestoreConstants.COLLECTION_USERS).document(uid)
                                    .collection(FirestoreConstants.COLLECTION_VOUCHER_USAGES).document(voucherId)
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
