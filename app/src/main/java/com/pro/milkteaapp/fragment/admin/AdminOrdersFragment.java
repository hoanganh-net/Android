package com.pro.milkteaapp.fragment.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.activity.admin.AdminOrderDetailActivity;
import com.pro.milkteaapp.adapter.admin.AdminOrdersAdapter;
import com.pro.milkteaapp.models.Order;
import com.pro.milkteaapp.repository.OrdersRepository;

import java.util.ArrayList;
import java.util.List;

public class AdminOrdersFragment extends Fragment {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private RecyclerView recyclerView;
    private View emptyState, loadingState;
    private SwipeRefreshLayout swipeRefresh;
    private OrdersRepository ordersRepository;
    private ListenerRegistration registration;
    private AdminOrdersAdapter adapter;

    private String currentStatus = "PENDING";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.admin_activity_orders, container, false);

        drawerLayout = v.findViewById(R.id.drawerLayout);
        navigationView = v.findViewById(R.id.navigationView);
        swipeRefresh = v.findViewById(R.id.swipeRefresh);
        recyclerView = v.findViewById(R.id.recycler);
        emptyState   = v.findViewById(R.id.emptyState);
        loadingState = v.findViewById(R.id.loadingState);

        ordersRepository = new OrdersRepository();
        
        setupDrawer();
        setupRecyclerView();

        swipeRefresh.setOnRefreshListener(this::loadData);
        loadData();

        return v;
    }

    /**
     * Mở drawer filter (được gọi từ AdminMainActivity)
     */
    public void openDrawer() {
        if (drawerLayout != null && isAdded()) {
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }
    
    /**
     * Đóng drawer
     */
    public void closeDrawer() {
        if (drawerLayout != null && isAdded()) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }
    
    /**
     * Kiểm tra drawer có đang mở không
     */
    public boolean isDrawerOpen() {
        return drawerLayout != null && isAdded() && drawerLayout.isDrawerOpen(GravityCompat.START);
    }

    private void setupDrawer() {
        if (drawerLayout == null || navigationView == null || !isAdded()) return;

        // Drawer sẽ được mở từ toolbar của AdminMainActivity
        // (AdminMainActivity đã setup icon menu để mở drawer)

        // Setup drawer menu click
        navigationView.setNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            String status = "PENDING";

            if (itemId == R.id.menu_pending_orders) {
                status = "PENDING";
            } else if (itemId == R.id.menu_finished_orders) {
                status = "FINISHED";
            } else if (itemId == R.id.menu_cancelled_orders) {
                status = "CANCELLED";
            }

            // Cập nhật status
            currentStatus = status;

            // Cập nhật checked state
            navigationView.setCheckedItem(item.getItemId());

            // Reset UI
            recyclerView.setVisibility(View.INVISIBLE);
            adapter.submitList(new ArrayList<>());

            // Load data với status mới
            showLoading(true);
            loadData();

            // Đóng drawer
            if (drawerLayout != null) {
                drawerLayout.closeDrawer(GravityCompat.START);
            }

            return true;
        });

        // Set default selection là PENDING (sau khi menu đã được inflate)
        navigationView.post(() -> {
            if (navigationView != null && isAdded()) {
                navigationView.setCheckedItem(R.id.menu_pending_orders);
            }
        });
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AdminOrdersAdapter(
                this::onItemClicked,
                null, // Không dùng xác nhận ở màn này
                null, // Không dùng huỷ ở màn này
                this::onDeleteClicked // Giữ lại chức năng xoá với đơn Cancelled
        );

        // Tắt toàn bộ nút confirm & cancel
        adapter.setShowConfirm(false);
        adapter.setShowCancel(false);

        recyclerView.setItemAnimator(null);
        adapter.setStateRestorationPolicy(
                RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        );
        recyclerView.setAdapter(adapter);
    }

    /** Click item → mở chi tiết đơn hàng dành riêng cho admin */
    private void onItemClicked(@NonNull Order order) {
        if (!isAdded()) return;
        Intent i = new Intent(requireContext(), AdminOrderDetailActivity.class);
        i.putExtra(AdminOrderDetailActivity.EXTRA_ORDER_ID, order.getId());
        startActivity(i);
    }

    private void loadData() {
        if (!isAdded()) return;

        if (registration != null) { registration.remove(); registration = null; }

        // Query theo status
        if ("PENDING".equalsIgnoreCase(currentStatus)) {
            registration = ordersRepository.listenAllOrders(new OrdersRepository.OnOrdersListener() {
                @Override
                public void onOrdersReceived(@NonNull List<Order> orders) {
                    if (!isAdded()) return;
                    swipeRefresh.setRefreshing(false);
                    // Filter chỉ PENDING
                    List<Order> filtered = new ArrayList<>();
                    for (Order o : orders) {
                        if ("PENDING".equalsIgnoreCase(o.getStatus())) {
                            filtered.add(o);
                        }
                    }
                    adapter.submitList(filtered);
                    if (filtered.isEmpty()) {
                        showEmpty();
                    } else {
                        showList();
                    }
                    showLoading(false);
                }

                @Override
                public void onError(@NonNull Exception error) {
                    if (!isAdded()) return;
                    swipeRefresh.setRefreshing(false);
                    showLoading(false);
                    showEmpty();
                    toastLong("Lỗi tải: " + error.getMessage());
                }
            });
        } else if ("FINISHED".equalsIgnoreCase(currentStatus)) {
            registration = ordersRepository.listenAllOrders(new OrdersRepository.OnOrdersListener() {
                @Override
                public void onOrdersReceived(@NonNull List<Order> orders) {
                    if (!isAdded()) return;
                    swipeRefresh.setRefreshing(false);
                    // Filter chỉ FINISHED
                    List<Order> filtered = new ArrayList<>();
                    for (Order o : orders) {
                        if ("FINISHED".equalsIgnoreCase(o.getStatus())) {
                            filtered.add(o);
                        }
                    }
                    // Sort theo finishedAt
                    filtered.sort((a, b) -> {
                        long la = tsToLong(a.getFinishedAt() != null ? a.getFinishedAt() : a.getCreatedAt());
                        long lb = tsToLong(b.getFinishedAt() != null ? b.getFinishedAt() : b.getCreatedAt());
                        return Long.compare(lb, la); // desc
                    });
                    adapter.submitList(filtered);
                    if (filtered.isEmpty()) {
                        showEmpty();
                    } else {
                        showList();
                    }
                    showLoading(false);
                }

                @Override
                public void onError(@NonNull Exception error) {
                    if (!isAdded()) return;
                    swipeRefresh.setRefreshing(false);
                    showLoading(false);
                    showEmpty();
                    toastLong("Lỗi tải: " + error.getMessage());
                }
            });
        } else { // CANCELLED
            registration = ordersRepository.listenAllOrders(new OrdersRepository.OnOrdersListener() {
                @Override
                public void onOrdersReceived(@NonNull List<Order> orders) {
                    if (!isAdded()) return;
                    swipeRefresh.setRefreshing(false);
                    // Filter CANCELLED hoặc CANCELED
                    List<Order> filtered = new ArrayList<>();
                    for (Order o : orders) {
                        String s = o.getStatus();
                        if ("CANCELLED".equalsIgnoreCase(s) || "CANCELED".equalsIgnoreCase(s)) {
                            filtered.add(o);
                        }
                    }
                    // Sort theo cancelledAt
                    filtered.sort((a, b) -> {
                        Timestamp ta = firstNonNullCancelTs(a);
                        Timestamp tb = firstNonNullCancelTs(b);
                        long la = tsToLong(ta != null ? ta : a.getCreatedAt());
                        long lb = tsToLong(tb != null ? tb : b.getCreatedAt());
                        return Long.compare(lb, la); // desc
                    });
                    adapter.setShowDelete(true);
                    adapter.submitList(filtered);
                    if (filtered.isEmpty()) {
                        showEmpty();
                    } else {
                        showList();
                    }
                    showLoading(false);
                }

                @Override
                public void onError(@NonNull Exception error) {
                    if (!isAdded()) return;
                    swipeRefresh.setRefreshing(false);
                    showLoading(false);
                    showEmpty();
                    toastLong("Lỗi tải: " + error.getMessage());
                }
            });
        }
    }

    /** Xoá hẳn record (tab Cancelled) */
    private void onDeleteClicked(@NonNull Order order) {
        FirebaseFirestore.getInstance().collection("orders")
                .document(order.getId())
                .delete()
                .addOnSuccessListener(s -> toast("🗑️ Đã xóa đơn #" + order.getId()))
                .addOnFailureListener(e -> toastLong("❌ Lỗi xóa: " + e.getMessage()));
    }

    // ===== Helpers =====
    private static long tsToLong(@Nullable Timestamp ts) {
        return ts != null ? ts.toDate().getTime() : 0L;
    }

    @Nullable
    private static Timestamp firstNonNullCancelTs(@NonNull Order o) {
        if (o.getCancelledAt() != null) return o.getCancelledAt(); // British
        try {
            // nếu model có thêm getCanceledAt() (US) thì tận dụng
            java.lang.reflect.Method m = o.getClass().getMethod("getCanceledAt");
            Object v = m.invoke(o);
            if (v instanceof Timestamp) return (Timestamp) v;
        } catch (Exception e) {
            android.util.Log.w("AdminOrders", "Failed to get canceledAt via reflection: " + e.getMessage());
        }
        return null;
    }

    private void showLoading(boolean s) { loadingState.setVisibility(s ? View.VISIBLE : View.GONE); }
    private void showEmpty() { emptyState.setVisibility(View.VISIBLE); recyclerView.setVisibility(View.GONE); }
    private void showList() { emptyState.setVisibility(View.GONE); recyclerView.setVisibility(View.VISIBLE); }

    private void toast(@NonNull String m) { if (!isAdded()) return; Toast.makeText(getContext(), m, Toast.LENGTH_SHORT).show(); }
    private void toastLong(@NonNull String m) { if (!isAdded()) return; Toast.makeText(getContext(), m, Toast.LENGTH_LONG).show(); }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (registration != null) { registration.remove(); registration = null; }
    }
}
