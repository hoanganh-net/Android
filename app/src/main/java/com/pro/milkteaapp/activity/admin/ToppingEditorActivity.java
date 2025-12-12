package com.pro.milkteaapp.activity.admin;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.activity.MainActivity;
import com.pro.milkteaapp.models.Topping;
import com.pro.milkteaapp.repository.CategoriesRepository;
import com.pro.milkteaapp.repository.ToppingsRepository;
import com.pro.milkteaapp.utils.ImageLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Activity để thêm/sửa topping
 */
public class ToppingEditorActivity extends AppCompatActivity {

    public static final String EXTRA_TOPPING_ID = "toppingId";

    // Views
    private TextInputEditText etName;
    private TextInputEditText etPrice;
    private TextInputEditText etImageUrl;
    private ImageView imgPreview;
    private MaterialButton btnPickBuiltinImage;
    private TextView tvCategories;
    private MaterialButton btnPickCategories;
    private SwitchMaterial switchActive;
    private Button btnCancel;
    private Button btnComplete;
    private ProgressBar progress;

    private boolean isEditMode = false;
    private String toppingId;
    private ToppingsRepository toppingsRepository;
    private CategoriesRepository categoriesRepository;
    private final ArrayList<String> allCategories = new ArrayList<>();

    @SuppressLint("SetTextI18n")
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
        toppingId = getIntent().getStringExtra(EXTRA_TOPPING_ID);
        isEditMode = toppingId != null && !toppingId.trim().isEmpty();
        setContentView(R.layout.admin_activity_edit_topping);

        // Bind views
        bindViews();

        // Repository
        toppingsRepository = new ToppingsRepository();
        categoriesRepository = new CategoriesRepository();

        // Load categories
        loadCategories();

        // Setup image preview
        setupImagePreview();
        setupImagePicker();

        // Load topping nếu edit mode
        if (isEditMode) {
            loadTopping(toppingId);
        } else {
            // Set giá trị mặc định cho add mode
            if (switchActive != null) switchActive.setChecked(true);
            if (tvCategories != null) tvCategories.setText("Chưa chọn");
            if (imgPreview != null) imgPreview.setImageResource(R.drawable.milktea);
        }

        // Buttons
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> finish()); // Hủy thay đổi: quay lại không lưu
        }

        if (btnComplete != null) {
            btnComplete.setOnClickListener(v -> saveTopping()); // Hoàn thành: lưu thay đổi
        }

        if (btnPickCategories != null) {
            btnPickCategories.setOnClickListener(v -> pickCategories());
        }
    }

    private void bindViews() {
        etName = findViewById(R.id.edtName);
        etPrice = findViewById(R.id.edtPrice);
        etImageUrl = findViewById(R.id.edtImageUrl);
        imgPreview = findViewById(R.id.imgPreview);
        btnPickBuiltinImage = findViewById(R.id.btnPickBuiltinImage);
        tvCategories = findViewById(R.id.txtCategories);
        btnPickCategories = findViewById(R.id.btnPickCategories);
        switchActive = findViewById(R.id.switchActive);
        btnCancel = findViewById(R.id.buttonCancel);
        btnComplete = findViewById(R.id.buttonComplete);
        progress = findViewById(R.id.progressBar);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
            String title = isEditMode ? "CHỈNH SỬA TOPPING" : "THÊM TOPPING MỚI";
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
        // Danh sách ảnh topping có sẵn
        final String[] TOPPING_IMAGES = new String[]{
                "milktea_brownsugar",
                "milktea_matcha",
                "milktea_taro",
                "milktea_oolong",
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
                .setTitle("Chọn ảnh topping")
                .setItems(TOPPING_IMAGES, (d, which) -> {
                    String name = TOPPING_IMAGES[which];
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

    private void loadCategories() {
        categoriesRepository.fetchActiveCategories(new CategoriesRepository.OnCategoriesListener() {
            @Override
            public void onCategoriesReceived(@NonNull List<com.pro.milkteaapp.models.Category> categoryList) {
                allCategories.clear();
                for (com.pro.milkteaapp.models.Category cat : categoryList) {
                    String name = cat.getName();
                    if (name != null && !name.trim().isEmpty()) {
                        allCategories.add(name.trim());
                    }
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                // Ignore error, categories sẽ được load lại khi cần
            }
        });
    }

    private void loadTopping(String id) {
        setLoading(true);
        toppingsRepository.fetchToppingById(id, new ToppingsRepository.OnToppingListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onToppingReceived(@NonNull Topping topping) {
                setLoading(false);
                
                if (etName != null) etName.setText(topping.getName());
                if (etPrice != null) etPrice.setText(String.valueOf(topping.getPrice()));
                if (etImageUrl != null) {
                    String imageUrl = topping.getImageUrl();
                    etImageUrl.setText(imageUrl != null ? imageUrl : "");
                    // Update preview
                    if (TextUtils.isEmpty(imageUrl)) {
                        if (imgPreview != null) imgPreview.setImageResource(R.drawable.milktea);
                    } else {
                        ImageLoader.load(imgPreview, imageUrl, R.drawable.milktea);
                    }
                }
                if (switchActive != null) {
                    boolean active = topping.getActive();
                    switchActive.setChecked(active);
                }
                
                if (topping.getCategories() != null && !topping.getCategories().isEmpty()) {
                    if (tvCategories != null) {
                        tvCategories.setText(TextUtils.join(", ", topping.getCategories()));
                    }
                } else {
                    if (tvCategories != null) tvCategories.setText("Chưa chọn");
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                setLoading(false);
                toast("Lỗi tải topping: " + error.getMessage());
                finish();
            }
        });
    }

    private void pickCategories() {
        if (allCategories.isEmpty()) {
            toast("Đang tải danh sách category...");
            loadCategories();
            return;
        }

        final String[] items = allCategories.toArray(new String[0]);
        final boolean[] checked = new boolean[items.length];
        
        // Parse categories hiện tại
        Set<String> pre = new HashSet<>(parseCategories(tvCategories != null ? tvCategories.getText().toString() : ""));
        for (int i = 0; i < items.length; i++) {
            checked[i] = pre.contains(items[i]);
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Chọn category áp dụng")
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> {})
                .setPositiveButton("Xong", (dialog, which) -> {
                    ArrayList<String> chosen = new ArrayList<>();
                    for (int i = 0; i < items.length; i++) {
                        if (checked[i]) chosen.add(items[i]);
                    }
                    if (tvCategories != null) {
                        tvCategories.setText(chosen.isEmpty() ? "Chưa chọn" : TextUtils.join(", ", chosen));
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private List<String> parseCategories(String text) {
        List<String> result = new ArrayList<>();
        if (TextUtils.isEmpty(text) || "Chưa chọn".equals(text)) return result;
        String[] parts = text.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    private void saveTopping() {
        String name = etName != null && etName.getText() != null
                ? etName.getText().toString().trim()
                : "";
        String priceStr = etPrice != null && etPrice.getText() != null
                ? etPrice.getText().toString().trim()
                : "";
        String imageUrl = etImageUrl != null && etImageUrl.getText() != null
                ? etImageUrl.getText().toString().trim()
                : "";
        boolean active = switchActive != null && switchActive.isChecked();

        if (name.isEmpty()) {
            toast("Vui lòng nhập tên topping");
            if (etName != null) etName.requestFocus();
            return;
        }

        long price;
        try {
            price = Long.parseLong(priceStr.replace(".", "").replace(",", "").trim());
        } catch (Exception ex) {
            toast("Giá không hợp lệ");
            if (etPrice != null) etPrice.requestFocus();
            return;
        }

        List<String> categories = parseCategories(tvCategories != null ? tvCategories.getText().toString() : "");

        setLoading(true);

        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("price", price);
        data.put("imageUrl", imageUrl);
        data.put("categories", categories);
        data.put("active", active);

        if (isEditMode) {
            // UPDATE
            toppingsRepository.updateTopping(toppingId, data, new ToppingsRepository.OnToppingUpdateListener() {
                @Override
                public void onSuccess() {
                    setLoading(false);
                    toast("Đã cập nhật topping");
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
            toppingsRepository.createTopping(data, new ToppingsRepository.OnToppingCreateListener() {
                @Override
                public void onSuccess(@NonNull String topId) {
                    setLoading(false);
                    toast("Đã thêm topping");
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
