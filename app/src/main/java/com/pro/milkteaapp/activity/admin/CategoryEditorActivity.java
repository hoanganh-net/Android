package com.pro.milkteaapp.activity.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FieldValue;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.activity.MainActivity;
import com.pro.milkteaapp.models.Category;
import com.pro.milkteaapp.repository.CategoriesRepository;
import com.pro.milkteaapp.utils.ImageLoader;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Activity để thêm/sửa category
 */
public class CategoryEditorActivity extends AppCompatActivity {

    public static final String EXTRA_CATEGORY_ID = "categoryId";

    // Views
    private TextInputEditText etName;
    private TextInputEditText etImageUrl;
    private ImageView imgPreview;
    private SwitchMaterial cbActive;
    private MaterialButton btnPickBuiltinImage;
    private Button btnCancel;
    private Button btnComplete;
    private ProgressBar progress;

    private boolean isEditMode = false;
    private String categoryId;
    private CategoriesRepository categoriesRepository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Chặn non-admin
        if (!new SessionManager(this).isAdmin()) {
            Toast.makeText(this, "Bạn không có quyền truy cập (admin-only)", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }

        // Xác định mode
        categoryId = getIntent().getStringExtra(EXTRA_CATEGORY_ID);
        isEditMode = categoryId != null && !categoryId.trim().isEmpty();
        setContentView(R.layout.admin_activity_edit_category);

        // Bind views
        bindViews();

        // Repository
        categoriesRepository = new CategoriesRepository();

        // Setup listeners
        setupImagePreview();
        setupImagePicker();

        // Load category nếu edit mode
        if (isEditMode) {
            loadCategory(categoryId);
        } else {
            // Set giá trị mặc định cho add mode
            if (imgPreview != null) imgPreview.setImageResource(R.drawable.milktea);
            if (cbActive != null) cbActive.setChecked(true);
        }

        // Buttons
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> finish()); // Hủy thay đổi: quay lại không lưu
        }

        if (btnComplete != null) {
            btnComplete.setOnClickListener(v -> saveCategory()); // Hoàn thành: lưu thay đổi
        }
    }

    private void bindViews() {
        etName = findViewById(R.id.edtName);
        etImageUrl = findViewById(R.id.edtImageUrl);
        imgPreview = findViewById(R.id.imgPreview);
        cbActive = findViewById(R.id.cbActive);
        btnPickBuiltinImage = findViewById(R.id.btnPickBuiltinImage);
        btnCancel = findViewById(R.id.buttonCancel);
        btnComplete = findViewById(R.id.buttonComplete);
        progress = findViewById(R.id.progressBar);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
            String title = isEditMode ? "CHỈNH SỬA DANH MỤC" : "THÊM DANH MỤC MỚI";
            android.widget.TextView titleView = toolbar.findViewById(R.id.toolbarTitle);
            if (titleView != null) {
                titleView.setText(title);
            } else {
                toolbar.setTitle(title);
            }
        }
    }

    private void setupImagePreview() {
        if (etImageUrl != null) {
            etImageUrl.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String val = s == null ? "" : s.toString().trim();
                    if (val.isEmpty()) {
                        if (imgPreview != null) imgPreview.setImageResource(R.drawable.milktea);
                    } else {
                        ImageLoader.load(
                                imgPreview,
                                val,
                                R.drawable.milktea
                        );
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void setupImagePicker() {
        if (btnPickBuiltinImage != null) {
            btnPickBuiltinImage.setOnClickListener(v -> showImagePickerDialog());
        }
    }

    private void showImagePickerDialog() {
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

        new android.app.AlertDialog.Builder(this)
                .setTitle("Chọn ảnh danh mục")
                .setItems(CATE_IMAGES, (d, which) -> {
                    String name = CATE_IMAGES[which];
                    if (etImageUrl != null) {
                        etImageUrl.setText(name);
                    }
                    ImageLoader.load(
                            imgPreview,
                            name,
                            R.drawable.milktea
                    );
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void loadCategory(String id) {
        setLoading(true);
        categoriesRepository.fetchCategoryById(id, new CategoriesRepository.OnCategoryListener() {
            @Override
            public void onCategoryReceived(@NonNull Category category) {
                setLoading(false);
                
                if (etName != null) etName.setText(category.getName());
                if (etImageUrl != null) etImageUrl.setText(category.getImageUrl());
                if (cbActive != null) cbActive.setChecked(category.isActive());

                // Load preview ảnh
                ImageLoader.load(
                        imgPreview,
                        category.getImageUrl(),
                        R.drawable.milktea
                );
            }

            @Override
            public void onError(@NonNull Exception error) {
                setLoading(false);
                toast("Lỗi tải danh mục: " + error.getMessage());
                finish();
            }
        });
    }

    private void saveCategory() {
        String name = etName != null && etName.getText() != null
                ? etName.getText().toString().trim()
                : "";
        String imageUrl = etImageUrl != null && etImageUrl.getText() != null
                ? etImageUrl.getText().toString().trim()
                : "";
        boolean active = cbActive != null && cbActive.isChecked();

        if (name.isEmpty()) {
            toast("Vui lòng nhập tên danh mục");
            return;
        }
        if (imageUrl.isEmpty()) imageUrl = null;

        setLoading(true);

        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("imageUrl", imageUrl);
        data.put("active", active);
        data.put("searchableName", name.toLowerCase(Locale.ROOT));
        data.put("updatedAt", FieldValue.serverTimestamp());

        if (isEditMode) {
            // UPDATE
            categoriesRepository.updateCategory(categoryId, data, new CategoriesRepository.OnCategoryUpdateListener() {
                @Override
                public void onSuccess() {
                    setLoading(false);
                    toast("Đã cập nhật danh mục");
                    setResult(RESULT_OK);
                    finish();
                }

                @Override
                public void onError(@NonNull Exception error) {
                    setLoading(false);
                    toast("Cập nhật thất bại: " + error.getMessage());
                }
            });
        } else {
            // CREATE
            data.put("createdAt", FieldValue.serverTimestamp());
            categoriesRepository.createCategory(data, new CategoriesRepository.OnCategoryCreateListener() {
                @Override
                public void onSuccess(@NonNull String catId) {
                    setLoading(false);
                    toast("Đã thêm danh mục");
                    setResult(RESULT_OK);
                    finish();
                }

                @Override
                public void onError(@NonNull Exception error) {
                    setLoading(false);
                    toast("Thêm thất bại: " + error.getMessage());
                }
            });
        }
    }

    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnComplete != null) btnComplete.setEnabled(!loading);
        if (btnCancel != null) btnCancel.setEnabled(!loading);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
