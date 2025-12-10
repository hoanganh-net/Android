package com.pro.milkteaapp.fragment.admin.management;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.ListenerRegistration;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.CategoryAdapter;
import com.pro.milkteaapp.interfaces.AddActionHandler;
import com.pro.milkteaapp.models.Category;
import com.pro.milkteaapp.repository.CategoriesRepository;
import com.pro.milkteaapp.utils.ImageLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Quản lý danh mục cho admin – bản không dùng ViewModel.
 * Truy cập Firestore trực tiếp.
 */
public class CategoryManageFragment extends Fragment implements
        AddActionHandler,
        CategoryAdapter.OnCategoryActionListener {

    // Views
    private androidx.recyclerview.widget.RecyclerView recyclerView;
    private LinearProgressIndicator progressBar;
    private SwipeRefreshLayout swipeRefresh;
    private View emptyState;
    private FloatingActionButton fabAdd;
    private TextInputEditText edtSearch;
    private Button btnAddFirst;

    // Repository
    private CategoriesRepository categoriesRepository;
    private ListenerRegistration reg; // để remove khi onDestroyView

    // Adapter + data
    private CategoryAdapter adapter;
    private final List<Category> list = new ArrayList<>();

    public CategoryManageFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.admin_fragment_category, container, false);

        // init repository
        categoriesRepository = new CategoriesRepository();

        // bind views
        recyclerView = v.findViewById(R.id.recyclerViewCategories);
        progressBar = v.findViewById(R.id.progressBar);
        swipeRefresh = v.findViewById(R.id.swipeRefresh);
        emptyState = v.findViewById(R.id.emptyState);
        fabAdd = v.findViewById(R.id.fabAdd);
        edtSearch = v.findViewById(R.id.edtSearch);
        btnAddFirst = v.findViewById(R.id.btnAddFirst);

        // adapter (admin mode = true)
        adapter = new CategoryAdapter(this, true);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(adapter);

        // btn add
        if (fabAdd != null) fabAdd.setOnClickListener(_v -> onAddAction());
        if (btnAddFirst != null) btnAddFirst.setOnClickListener(_v -> onAddAction());

        // swipe refresh: vì mình nghe realtime nên chỉ tắt refresh
        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(() -> swipeRefresh.setRefreshing(false));
        }

        // search
        if (edtSearch != null) {
            edtSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    applyFilter(s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT));
                }
            });
        }

        listenCategoriesRealtime();
        return v;
    }

    /**
     * Nghe realtime tất cả danh mục
     */
    private void listenCategoriesRealtime() {
        showLoading(true);
        reg = categoriesRepository.listenAllCategories(new CategoriesRepository.OnCategoriesListener() {
            @Override
            public void onCategoriesReceived(@NonNull List<Category> categories) {
                if (!isAdded()) return;
                list.clear();
                list.addAll(categories);
                showLoading(false);

                // áp dụng filter nếu đang search
                String q = edtSearch != null && edtSearch.getText() != null
                        ? edtSearch.getText().toString().trim().toLowerCase(Locale.ROOT)
                        : "";
                if (q.isEmpty()) {
                    adapter.submit(list);
                } else {
                    applyFilter(q);
                }
                updateEmptyState();
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (!isAdded()) return;
                showLoading(false);
                toast("Lỗi tải danh mục: " + error.getMessage());
            }
        });
    }

    private void applyFilter(String q) {
        if (q == null) q = "";
        q = q.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            adapter.submit(list);
            updateEmptyState();
            return;
        }
        List<Category> filtered = new ArrayList<>();
        for (Category it : list) {
            String name = it.getName() == null ? "" : it.getName();
            if (name.toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(it);
            }
        }
        adapter.submit(filtered);
        updateEmptyState();
    }

    private void showLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void updateEmptyState() {
        boolean isEmpty = adapter.getItemCount() == 0;
        if (emptyState != null) emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    /* ================= AddActionHandler ================= */
    @Override
    public void onAddAction() {
        showCategoryDialog(null);
    }

    /* ================= Dialog thêm/sửa danh mục ================= */
    private void showCategoryDialog(@Nullable Category category) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout._dialog_category, null);

        TextInputEditText edtName = dialogView.findViewById(R.id.edtName);
        TextInputEditText edtImageUrl = dialogView.findViewById(R.id.edtImageUrl);
        ImageView imgPreview = dialogView.findViewById(R.id.imgPreview);
        MaterialCheckBox cbActive = dialogView.findViewById(R.id.cbActive);
        Button btnSave = dialogView.findViewById(R.id.btnSave);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        // nút mới
        Button btnPickBuiltinImage = dialogView.findViewById(R.id.btnPickBuiltinImage);

        // nếu sửa
        if (category != null) {
            if (edtName != null) edtName.setText(category.getName());
            if (edtImageUrl != null) edtImageUrl.setText(category.getImageUrl());
            if (cbActive != null) cbActive.setChecked(category.isActive());

            // dùng ImageLoader để support cả url và tên drawable
            ImageLoader.load(
                    imgPreview,
                    category.getImageUrl(),
                    R.drawable.milktea
            );
        } else {
            imgPreview.setImageResource(R.drawable.milktea);
            if (cbActive != null) cbActive.setChecked(true);
        }

        // preview ảnh khi gõ
        if (edtImageUrl != null) {
            edtImageUrl.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String val = s == null ? "" : s.toString().trim();
                    if (val.isEmpty()) {
                        imgPreview.setImageResource(R.drawable.milktea);
                    } else {
                        ImageLoader.load(
                                imgPreview,
                                val,
                                R.drawable.milktea
                        );
                    }
                }
            });
        }

        // ====== PICK ẢNH CÓ SẴN (giống edit profile) ======
        if (btnPickBuiltinImage != null) {
            btnPickBuiltinImage.setOnClickListener(v -> {
                // mảng ảnh demo – anh có thể nối thêm tùy ý
                final String[] CATE_IMAGES = new String[]{
                        "milktea_brownsugar",
                        "milktea_matcha",
                        "milktea_taro",
                        "milktea_olong",
                        "milktea_greenthai",
                        "milktea_strawberry",
                        "milktea_chocolate",
                        "milktea_caramel",
                        "milktea_mango",
                        "tea_blacktea",
                        "ic_milk_tea",
                        "milktea"
                };

                new AlertDialog.Builder(requireContext())
                        .setTitle("Chọn ảnh danh mục")
                        .setItems(CATE_IMAGES, (d, which) -> {
                            String name = CATE_IMAGES[which];
                            if (edtImageUrl != null) {
                                edtImageUrl.setText(name);
                            }
                            ImageLoader.load(
                                    imgPreview,
                                    name,
                                    R.drawable.milktea
                            );
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (btnCancel != null) btnCancel.setOnClickListener(v -> dialog.dismiss());

        if (btnSave != null) {
            AlertDialog finalDialog = dialog;
            btnSave.setOnClickListener(v -> {
                String name = edtName != null && edtName.getText() != null
                        ? edtName.getText().toString().trim()
                        : "";
                String imageUrl = edtImageUrl != null && edtImageUrl.getText() != null
                        ? edtImageUrl.getText().toString().trim()
                        : "";
                boolean active = cbActive != null && cbActive.isChecked();

                if (name.isEmpty()) {
                    toast("Vui lòng nhập tên danh mục");
                    return;
                }
                if (imageUrl.isEmpty()) imageUrl = null;

                Map<String, Object> data = new HashMap<>();
                data.put("name", name);
                data.put("imageUrl", imageUrl);
                data.put("active", active);
                data.put("searchableName", name.toLowerCase(Locale.ROOT));

                if (category == null) {
                    // thêm mới
                    addCategory(data, finalDialog);
                } else {
                    // cập nhật
                    updateCategory(category.getId(), data, finalDialog);
                }
            });
        }

        dialog.show();
    }

    /* ================= CRUD qua Repository ================= */

    private void addCategory(Map<String, Object> data, AlertDialog dialog) {
        categoriesRepository.createCategory(data, new CategoriesRepository.OnCategoryCreateListener() {
            @Override
            public void onSuccess(@NonNull String categoryId) {
                toast("Đã thêm danh mục");
                dialog.dismiss();
            }

            @Override
            public void onError(@NonNull Exception error) {
                toast("Lỗi thêm: " + error.getMessage());
            }
        });
    }

    private void updateCategory(String id, Map<String, Object> data, AlertDialog dialog) {
        categoriesRepository.updateCategory(id, data, new CategoriesRepository.OnCategoryUpdateListener() {
            @Override
            public void onSuccess() {
                toast("Đã cập nhật danh mục");
                dialog.dismiss();
            }

            @Override
            public void onError(@NonNull Exception error) {
                toast("Lỗi cập nhật: " + error.getMessage());
            }
        });
    }

    private void deleteCategory(String id) {
        categoriesRepository.deleteCategory(id, new CategoriesRepository.OnCategoryUpdateListener() {
            @Override
            public void onSuccess() {
                toast("Đã xóa danh mục");
            }

            @Override
            public void onError(@NonNull Exception error) {
                toast("Lỗi xóa: " + error.getMessage());
            }
        });
    }

    /* ================= callback từ adapter ================= */

    @Override
    public void onEdit(Category category) {
        showCategoryDialog(category);
    }

    @Override
    public void onDelete(Category category) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Xóa danh mục")
                .setMessage("Bạn có chắc muốn xóa \"" + (category.getName() == null ? "" : category.getName()) + "\" không?")
                .setPositiveButton("Xóa", (d, w) -> deleteCategory(category.getId()))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void toast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (reg != null) {
            reg.remove();
            reg = null;
        }
    }
}
