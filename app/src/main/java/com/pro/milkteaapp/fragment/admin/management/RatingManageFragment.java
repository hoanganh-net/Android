package com.pro.milkteaapp.fragment.admin.management;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.*;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.ListenerRegistration;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.admin.AdminRatingsAdapter;
import com.pro.milkteaapp.databinding.AdminFragmentRatingsBinding;
import com.pro.milkteaapp.repository.OrdersRepository;
import com.pro.milkteaapp.repository.UsersRepository;

import java.util.*;
import java.util.Locale;

public class RatingManageFragment extends Fragment {

    private AdminFragmentRatingsBinding b;
    private OrdersRepository ordersRepository;
    private UsersRepository usersRepository;
    private ListenerRegistration reg;
    private AdminRatingsAdapter adapter;
    private TextInputEditText searchEditText;
    private final List<AdminRatingsAdapter.Item> allItems = new ArrayList<>();
    private String lastSearch = "";

    // cache user để đỡ gọi Firestore lặp lại
    private final Map<String, UserBrief> userCache = new HashMap<>();

    public RatingManageFragment(){}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = AdminFragmentRatingsBinding.inflate(inflater, container, false);

        ordersRepository = new OrdersRepository();
        usersRepository = new UsersRepository();

        b.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AdminRatingsAdapter(this::onDeleteClicked);
        b.recycler.setAdapter(adapter);

        searchEditText = b.getRoot().findViewById(R.id.searchEditText);
        if (searchEditText != null) {
            searchEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    lastSearch = s == null ? "" : s.toString();
                    filterRatings();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        b.swipeRefresh.setOnRefreshListener(this::reload);

        // Load một lần ban đầu (giống AdminProductsFragment)
        loadOnce();

        return b.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        startRealtimeIfReady();
    }

    @Override
    public void onStop() {
        super.onStop();
        stopRealtime();
    }

    private void startRealtimeIfReady() {
        if (b == null || !isAdded()) return;
        listenData();
    }

    private void stopRealtime() {
        if (reg != null) {
            reg.remove();
            reg = null;
        }
    }

    // Load một lần ban đầu
    private void loadOnce() {
        if (!isAdded() || b == null) return;
        showLoading(true);
        ordersRepository.fetchOrdersWithRating(200, new OrdersRepository.OnOrdersListener() {
            @Override
            public void onOrdersReceived(@NonNull List<com.pro.milkteaapp.models.Order> orders) {
                if (!isAdded() || b == null) return;
                b.swipeRefresh.setRefreshing(false);
                showLoading(false);

                List<AdminRatingsAdapter.Item> items = new ArrayList<>();

                for (com.pro.milkteaapp.models.Order order : orders) {
                    Double rating = order.getRating();
                    if (rating == null || rating <= 0) continue;

                    String orderId = order.getId();
                    String nameDenorm = order.getUserName();
                    String avatarDenorm = order.getUserAvatar();
                    String userId = firstNonEmpty(order.getUserId(), order.getRatedBy());
                    String comment = order.getReview();
                    long when = order.getRatedAt() != null ? order.getRatedAt().toDate().getTime() : 0L;

                    items.add(new AdminRatingsAdapter.Item(
                            orderId,
                            userId,
                            firstNonEmpty(nameDenorm, "Người dùng"),
                            avatarDenorm,
                            rating,
                            comment,
                            when
                    ));
                }

                allItems.clear();
                allItems.addAll(items);
                filterRatings();
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (!isAdded() || b == null) return;
                b.swipeRefresh.setRefreshing(false);
                showLoading(false);
                showEmpty(true);
                Toast.makeText(requireContext(), "Lỗi tải: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void reload() {
        if (reg != null) { reg.remove(); reg = null; }
        listenData();
    }

    private void listenData() {
        // Note: Firestore không hỗ trợ orderBy nhiều field nếu không có composite index
        // Nên tạm thời chỉ orderBy ratedAt
        reg = ordersRepository.listenOrdersWithRating(200, new OrdersRepository.OnOrdersListener() {
            @Override
            public void onOrdersReceived(@NonNull List<com.pro.milkteaapp.models.Order> orders) {
                if (!isAdded() || b == null) return;

                b.swipeRefresh.setRefreshing(false);
                showLoading(false);

                List<AdminRatingsAdapter.Item> items = new ArrayList<>();

                for (com.pro.milkteaapp.models.Order order : orders) {
                    Double rating = order.getRating();
                    if (rating == null || rating <= 0) continue;

                    String orderId = order.getId();
                    String nameDenorm = order.getUserName();
                    String avatarDenorm = order.getUserAvatar();
                    String userId = firstNonEmpty(order.getUserId(), order.getRatedBy());
                    String comment = order.getReview();
                    long when = order.getRatedAt() != null ? order.getRatedAt().toDate().getTime() : 0L;

                    items.add(new AdminRatingsAdapter.Item(
                            orderId,
                            userId,
                            firstNonEmpty(nameDenorm, "Người dùng"),
                            avatarDenorm,
                            rating,
                            comment,
                            when
                    ));
                }

                allItems.clear();
                allItems.addAll(items);
                filterRatings();

                // Enrich cho những mục còn thiếu tên/avatar
                b.recycler.post(() -> {
                    for (int i = 0; i < items.size(); i++) {
                        final int pos = i;
                        AdminRatingsAdapter.Item it = items.get(pos);

                        // nếu đã có tên denorm thì bỏ qua
                        if (!TextUtils.isEmpty(it.userName) && !"Người dùng".equals(it.userName)) continue;

                        if (TextUtils.isEmpty(it.userId)) continue;

                        UserBrief cached = userCache.get(it.userId);
                        if (cached != null) {
                            applyUserBrief(pos, cached);
                            continue;
                        }

                        usersRepository.fetchUserById(it.userId, new UsersRepository.OnUserListener() {
                            @Override
                            public void onUserReceived(@NonNull com.pro.milkteaapp.models.User user) {
                                UserBrief br = new UserBrief(
                                        firstNonEmpty(
                                                user.getFullName(),
                                                user.getDisplayName(),
                                                emailPrefix(user.getEmail()),
                                                "Người dùng"
                                        ),
                                        user.getAvatar() // User model chỉ có getAvatar(), không có getPhotoUrl()
                                );
                                userCache.put(it.userId, br);
                                applyUserBrief(pos, br);
                            }

                            @Override
                            public void onError(@NonNull Exception error) {
                                // Ignore error
                            }
                        });
                    }
                });
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (!isAdded() || b == null) return;
                b.swipeRefresh.setRefreshing(false);
                showLoading(false);
                showEmpty(true);
                Toast.makeText(requireContext(), "Lỗi tải: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void applyUserBrief(int position, @NonNull UserBrief br) {
        if (!isAdded() || b == null) return;
        List<AdminRatingsAdapter.Item> cur = adapter.getCurrentList();
        if (position < 0 || position >= cur.size()) return;

        AdminRatingsAdapter.Item old = cur.get(position);
        AdminRatingsAdapter.Item upd = new AdminRatingsAdapter.Item(
                old.orderId, old.userId, br.name, br.avatar, old.stars, old.comment, old.ratedAtMs
        );

        List<AdminRatingsAdapter.Item> clone = new ArrayList<>(cur);
        clone.set(position, upd);
        adapter.submitList(clone);
    }

    private void onDeleteClicked(@NonNull AdminRatingsAdapter.Item it) {
        if (!isAdded() || b == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Xoá đánh giá?")
                .setMessage("Xoá rating của đơn #" + it.orderId + " (không xoá đơn hàng).")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> doDeleteRating(it.orderId))
                .show();
    }

    /** Xoá rating: reset các trường đánh giá; giữ nguyên userId/ratedBy để history vẫn còn quy chiếu */
    private void doDeleteRating(@NonNull String orderId) {
        showLoading(true);
        Map<String, Object> upd = new HashMap<>();
        upd.put("rating", 0);
        upd.put("review", com.google.firebase.firestore.FieldValue.delete());
        upd.put("ratedAt", com.google.firebase.firestore.FieldValue.delete());
        // KHÔNG xóa userId / ratedBy / userName / userAvatar để tiện debug & audit

        ordersRepository.updateOrder(orderId, upd, new OrdersRepository.OnOrderUpdateListener() {
            @Override
            public void onSuccess() {
                showLoading(false);
                Toast.makeText(requireContext(), "Đã xoá đánh giá của đơn #" + orderId, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull Exception error) {
                showLoading(false);
                Toast.makeText(requireContext(), "Lỗi xoá: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showLoading(boolean show) {
        b.progress.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) b.empty.setVisibility(View.GONE);
    }

    private void showEmpty(boolean empty) {
        b.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void filterRatings() {
        if (allItems.isEmpty()) {
            adapter.submitList(new ArrayList<>());
            showEmpty(true);
            return;
        }

        String query = lastSearch.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            adapter.submitList(new ArrayList<>(allItems));
            showEmpty(allItems.isEmpty());
            return;
        }

        List<AdminRatingsAdapter.Item> filtered = new ArrayList<>();
        for (AdminRatingsAdapter.Item item : allItems) {
            String userName = item.userName != null ? item.userName.toLowerCase(Locale.ROOT) : "";
            String comment = item.comment != null ? item.comment.toLowerCase(Locale.ROOT) : "";
            String orderId = item.orderId != null ? item.orderId.toLowerCase(Locale.ROOT) : "";
            if (userName.contains(query) || comment.contains(query) || orderId.contains(query)) {
                filtered.add(item);
            }
        }
        adapter.submitList(filtered);
        showEmpty(filtered.isEmpty());
    }

    @Override
    public void onDestroyView() {
        if (reg != null) { reg.remove(); reg = null; }
        b = null;
        super.onDestroyView();
    }

    private static String firstNonEmpty(String... arr) {
        if (arr == null) return null;
        for (String s : arr) if (s != null && !s.trim().isEmpty()) return s.trim();
        return null;
    }
    private static String emailPrefix(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    /**
     * @param avatar URL hoặc tên drawable
     */
    private record UserBrief(String name, String avatar) {
    }
    @Override
    public void onResume() {
        super.onResume();
        // Chỉ reload nếu listener chưa được đăng ký
        if (reg == null) {
            showLoading(true);
            listenData();
        }
    }
}
