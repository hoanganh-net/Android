package com.pro.milkteaapp.fragment.admin;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.ListenerRegistration;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.activity.admin.AdminMainActivity;
import com.pro.milkteaapp.activity.admin.AdminComboEditorActivity;
import com.pro.milkteaapp.adapter.admin.AdminComboAdapter;
import com.pro.milkteaapp.interfaces.AddActionHandler;
import com.pro.milkteaapp.models.Combo;
import com.pro.milkteaapp.repository.ComboRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fragment quản trị combo
 */
public class AdminCombosFragment extends Fragment
        implements AdminMainActivity.ScrollToTop,
        AdminMainActivity.SupportsRefresh,
        AddActionHandler {

    // UI
    private RecyclerView recyclerView;
    private LinearLayout emptyState;
    private LinearProgressIndicator progressBar;
    private TextInputEditText searchEditText;
    private SwipeRefreshLayout swipeRefresh;

    // Dữ liệu & adapter
    private final List<Combo> comboList = new ArrayList<>();
    private AdminComboAdapter adapter;
    private String lastSearch = "";

    // Quyền
    private boolean isAdminRole = false;

    // Repository
    private ComboRepository comboRepository;
    private ListenerRegistration realtimeReg;

    // Cờ để biết view đã được inflate chưa
    private boolean viewInited = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isAdminRole = isAdmin();
        comboRepository = new ComboRepository();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.admin_fragment_products, container, false); // Dùng layout tương tự

        if (!isAdminRole) {
            Toast.makeText(requireContext(), "Bạn không có quyền truy cập (admin-only).", Toast.LENGTH_SHORT).show();
            if (getActivity() != null) {
                getActivity().getOnBackPressedDispatcher().onBackPressed();
            }
            return new LinearLayout(requireContext());
        }

        bindViews(view);
        setupRecyclerView();
        setupSearch();
        setupSwipeToRefresh();

        loadOnce();

        viewInited = true;
        return view;
    }

    private void bindViews(View v) {
        recyclerView = v.findViewById(R.id.productsRecyclerView);
        emptyState = v.findViewById(R.id.emptyState);
        progressBar = v.findViewById(R.id.progressBar);
        searchEditText = v.findViewById(R.id.searchEditText);
        swipeRefresh = v.findViewById(R.id.swipeRefresh);

        Button btnAddFirst = v.findViewById(R.id.btnAddFirstProduct);
        if (btnAddFirst != null) {
            btnAddFirst.setText("Thêm Combo Đầu Tiên");
            btnAddFirst.setOnClickListener(_v -> openAddCombo());
        }
    }

    private void setupRecyclerView() {
        adapter = new AdminComboAdapter(comboList, new AdminComboAdapter.OnComboActionListener() {
            @Override
            public void onEditCombo(Combo combo) {
                onComboClick(combo);
            }

            @Override
            public void onDeleteCombo(Combo combo) {
                AdminCombosFragment.this.onDeleteCombo(combo);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(adapter);
    }

    private void setupSearch() {
        if (searchEditText == null) return;
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                lastSearch = s == null ? "" : s.toString();
                filterCombos(lastSearch);
            }
        });
    }

    private void setupSwipeToRefresh() {
        if (swipeRefresh == null) return;
        swipeRefresh.setOnRefreshListener(this::refresh);
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
        if (!isAdminRole) return;
        if (!viewInited) return;
        startRealtime();
    }

    private void startRealtime() {
        stopRealtime();
        showLoading(true);
        realtimeReg = comboRepository.listenAllCombos(new ComboRepository.OnCombosListener() {
            @Override
            public void onCombosReceived(@NonNull List<Combo> combos) {
                if (swipeRefresh != null && swipeRefresh.isRefreshing())
                    swipeRefresh.setRefreshing(false);
                comboList.clear();
                comboList.addAll(combos);
                if (adapter != null) {
                    filterCombos(lastSearch);
                }
                showLoading(false);
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (swipeRefresh != null && swipeRefresh.isRefreshing())
                    swipeRefresh.setRefreshing(false);
                showLoading(false);
                toast("Lỗi realtime: " + error.getMessage());
            }
        });
    }

    private void stopRealtime() {
        if (realtimeReg != null) {
            realtimeReg.remove();
            realtimeReg = null;
        }
    }

    private void loadOnce() {
        if (!isAdded()) return;
        showLoading(true);
        comboRepository.fetchAllCombos(new ComboRepository.OnCombosListener() {
            @Override
            public void onCombosReceived(@NonNull List<Combo> combos) {
                comboList.clear();
                comboList.addAll(combos);
                if (adapter != null) {
                    filterCombos(lastSearch);
                }
                showLoading(false);
            }

            @Override
            public void onError(@NonNull Exception error) {
                toast("Lỗi tải dữ liệu: " + error.getMessage());
                showLoading(false);
            }
        });
    }

    private void onComboClick(Combo combo) {
        // Click vào combo -> mở edit
        Intent it = new Intent(requireContext(), AdminComboEditorActivity.class);
        it.putExtra(AdminComboEditorActivity.EXTRA_COMBO_ID, combo.getId());
        startActivity(it);
    }

    private void deleteComboById(@NonNull String id) {
        showLoading(true);
        comboRepository.deleteCombo(id, new ComboRepository.OnComboUpdateListener() {
            @Override
            public void onSuccess() {
                showLoading(false);
                toast("Đã xóa combo.");
            }

            @Override
            public void onError(@NonNull Exception error) {
                showLoading(false);
                toast("Lỗi xóa: " + error.getMessage());
            }
        });
    }

    private void filterCombos(String query) {
        if (adapter == null) return;
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            adapter.updateList(comboList);
            checkEmptyState();
            return;
        }
        List<Combo> filtered = new ArrayList<>();
        for (Combo c : comboList) {
            String name = safe(c.getName()).toLowerCase(Locale.ROOT);
            if (name.contains(q)) {
                filtered.add(c);
            }
        }
        adapter.updateList(filtered);
        checkEmptyState();
    }

    private void checkEmptyState() {
        if (emptyState == null || recyclerView == null) return;
        boolean isEmpty = adapter == null || adapter.getItemCount() == 0;
        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void showLoading(boolean show) {
        if (progressBar != null) progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    // ====== Callbacks từ Activity/Adapter ======
    private void openAddCombo() {
        startActivity(new Intent(requireContext(), AdminComboEditorActivity.class));
    }

    @Override
    public void onAddAction() {
        openAddCombo();
    }

    public void onEditCombo(Combo combo) {
        Intent it = new Intent(requireContext(), AdminComboEditorActivity.class);
        it.putExtra(AdminComboEditorActivity.EXTRA_COMBO_ID, combo.getId());
        startActivity(it);
    }

    public void onDeleteCombo(Combo combo) {
        if (combo == null || safe(combo.getId()).isEmpty()) {
            toast("Không xác định được ID combo.");
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Xóa combo")
                .setMessage("Bạn có chắc muốn xóa \"" + safe(combo.getName()) + "\"?")
                .setPositiveButton("Xóa", (d, w) -> deleteComboById(combo.getId()))
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override
    public void scrollToTop() {
        if (recyclerView != null) recyclerView.smoothScrollToPosition(0);
    }

    @Override
    public void refresh() {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
        loadOnce();
    }

    private boolean isAdmin() {
        SharedPreferences p = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        return "admin".equalsIgnoreCase(p.getString("role", "user"));
    }

    private void toast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopRealtime();
    }
}

