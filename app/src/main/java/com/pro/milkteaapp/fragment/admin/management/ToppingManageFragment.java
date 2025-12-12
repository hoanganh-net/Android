package com.pro.milkteaapp.fragment.admin.management;

import android.annotation.SuppressLint;
import android.content.Intent;
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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.admin.AdminToppingAdapter;
import com.pro.milkteaapp.databinding.AdminFragmentToppingsBinding;
import com.pro.milkteaapp.interfaces.AddActionHandler;
import com.pro.milkteaapp.models.Topping;
import com.pro.milkteaapp.repository.CategoriesRepository;
import com.pro.milkteaapp.repository.ToppingsRepository;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.*;
import java.util.Locale;

public class ToppingManageFragment extends Fragment
        implements AdminToppingAdapter.Listener, AddActionHandler {

    private AdminFragmentToppingsBinding binding;
    private CategoriesRepository categoriesRepository;
    private ToppingsRepository toppingsRepository;
    private ListenerRegistration sub;
    private final ArrayList<Topping> data = new ArrayList<>();
    private final ArrayList<Topping> allToppings = new ArrayList<>();
    private AdminToppingAdapter adapter;
    private final ArrayList<String> allCategories = new ArrayList<>();
    private String lastSearch = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = AdminFragmentToppingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        categoriesRepository = new CategoriesRepository();
        toppingsRepository = new ToppingsRepository();

        adapter = new AdminToppingAdapter(data, this);
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);

        TextInputEditText searchEditText = binding.getRoot().findViewById(R.id.searchEditText);
        if (searchEditText != null) {
            searchEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    lastSearch = s == null ? "" : s.toString();
                    filterToppings();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        binding.swipe.setOnRefreshListener(this::reload);

        // Load một lần ban đầu (giống AdminProductFragment)
        loadOnce();
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
        if (binding == null) return;
        loadCategoriesThenListenToppings();
    }

    private void stopRealtime() {
        if (sub != null) {
            sub.remove();
            sub = null;
        }
    }

    // Load một lần ban đầu (không phụ thuộc categories)
    private void loadOnce() {
        if (!isAdded() || binding == null) return;
        setLoading(true);
        toppingsRepository.fetchAllToppings(new ToppingsRepository.OnToppingsListener() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onToppingsReceived(@NonNull List<Topping> toppings) {
                if (!isAdded() || binding == null) return;
                setLoading(false);
                allToppings.clear();
                allToppings.addAll(toppings);
                filterToppings();
            }

            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onError(@NonNull Exception error) {
                if (!isAdded() || binding == null) return;
                setLoading(false);
                setEmpty(true);
                adapter.notifyDataSetChanged();
                Toast.makeText(requireContext(), "Lỗi tải toppings: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // Gọi khi nhấn FAB trong ManagementFragment
    @Override
    public void onAddAction() {
        openToppingEditor(null);
    }

    private void setLoading(boolean b) {
        binding.progress.setVisibility(b ? View.VISIBLE : View.GONE);
    }

    private void setEmpty(boolean b) {
        binding.emptyState.setVisibility(b ? View.VISIBLE : View.GONE);
    }

    private void loadCategoriesThenListenToppings() {
        categoriesRepository.fetchActiveCategories(new CategoriesRepository.OnCategoriesListener() {
            @Override
            public void onCategoriesReceived(@NonNull List<com.pro.milkteaapp.models.Category> categories) {
                allCategories.clear();
                for (com.pro.milkteaapp.models.Category cat : categories) {
                    String name = cat.getName();
                    if (!TextUtils.isEmpty(name)) allCategories.add(name.trim());
                }
                listenToppings();
            }

            @Override
            public void onError(@NonNull Exception error) {
                listenToppings();
            }
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void listenToppings() {
        if (sub != null) { sub.remove(); sub = null; }
        if (binding == null || !isAdded()) return;
        sub = toppingsRepository.listenAllToppings(new ToppingsRepository.OnToppingsListener() {
            @Override
            public void onToppingsReceived(@NonNull List<Topping> toppings) {
                if (!isAdded() || binding == null) return;
                binding.swipe.setRefreshing(false);
                setLoading(false);
                allToppings.clear();
                allToppings.addAll(toppings);
                filterToppings();
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (!isAdded() || binding == null) return;
                binding.swipe.setRefreshing(false);
                setLoading(false);
                setEmpty(true);
                adapter.notifyDataSetChanged();
                Toast.makeText(requireContext(), "Lỗi tải toppings: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void reload() {
        loadCategoriesThenListenToppings();
    }

    @Override
    public void onEdit(@NonNull Topping t) { openToppingEditor(t); }
    
    /* ================= Mở Activity chỉnh sửa topping ================= */
    private void openToppingEditor(@Nullable Topping topping) {
        Intent intent = new Intent(requireContext(), com.pro.milkteaapp.activity.admin.ToppingEditorActivity.class);
        if (topping != null && topping.getId() != null) {
            intent.putExtra(com.pro.milkteaapp.activity.admin.ToppingEditorActivity.EXTRA_TOPPING_ID, topping.getId());
        }
        startActivity(intent);
    }

    @Override
    public void onDelete(@NonNull Topping t) {
        if (TextUtils.isEmpty(t.getId())) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Xoá topping")
                .setMessage("Bạn chắc muốn xoá \"" + t.getName() + "\"?")
                .setPositiveButton("Xoá", (d, w) ->
                        toppingsRepository.deleteTopping(t.getId(), new ToppingsRepository.OnToppingUpdateListener() {
                            @Override
                            public void onSuccess() {
                                // Success
                            }

                            @Override
                            public void onError(@NonNull Exception error) {
                                Toast.makeText(requireContext(), "Lỗi xoá: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }))
                .setNegativeButton("Huỷ", null)
                .show();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void filterToppings() {
        if (allToppings.isEmpty()) {
            data.clear();
            setEmpty(true);
            adapter.notifyDataSetChanged();
            return;
        }

        String query = lastSearch.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            data.clear();
            data.addAll(allToppings);
            setEmpty(data.isEmpty());
            adapter.notifyDataSetChanged();
            return;
        }

        data.clear();
        for (Topping t : allToppings) {
            String name = t.getName() != null ? t.getName().toLowerCase(Locale.ROOT) : "";
            if (name.contains(query)) {
                data.add(t);
            }
        }
        setEmpty(data.isEmpty());
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (sub != null) { sub.remove(); sub = null; }
        binding = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Đảm bảo realtime listener được đăng ký
        if (sub == null && binding != null) {
            startRealtimeIfReady();
        }
    }
}
