package com.pro.milkteaapp.activity.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.activity.MainActivity;
import com.pro.milkteaapp.fragment.pickersheet.ComboProductPickerSheet;
import com.pro.milkteaapp.generator.ComboIDGenerator;
import com.pro.milkteaapp.models.Product;
import com.pro.milkteaapp.repository.ComboRepository;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Activity để thêm/sửa combo
 * - Nếu có EXTRA_COMBO_ID -> Edit mode
 * - Nếu không -> Add mode
 */
public class AdminComboEditorActivity extends AppCompatActivity {

    public static final String EXTRA_COMBO_ID = "comboId";

    private TextInputEditText etName, etImageUrl, etDescription, etDiscountPercent;
    private Button btnSave, btnReset;
    private ProgressBar progress;
    private MaterialToolbar toolbar;
    private View tvSelectedProduct, tvTotalPrice, tvComboPrice;
    private Button btnSelectProduct;

    private boolean isEditMode = false;
    private String comboId;

    private final List<Product> selectedProduct = new ArrayList<>();
    private ComboRepository comboRepository;

    private FirebaseAuth auth;

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

        comboId = getIntent().getStringExtra(EXTRA_COMBO_ID);
        isEditMode = comboId != null && !comboId.trim().isEmpty();
        setContentView(R.layout.admin_activity_add_combo);

        bindViews();
        setupToolbar();

        auth = FirebaseAuth.getInstance();
        comboRepository = new ComboRepository();

        btnSelectProduct.setOnClickListener(v -> openProductPicker());
        btnSave.setOnClickListener(v -> onSave());
        btnReset.setOnClickListener(v -> resetForm());

        if (isEditMode) {
            toolbar.setTitle("SỬA COMBO");
            toolbar.setTitleTextAppearance(this, R.style.ToolbarTitleStyle);
            toolbar.setTitleTextColor(getResources().getColor(R.color.white, getTheme()));
            loadComboForEdit(comboId);
        } else {
            toolbar.setTitle("THÊM COMBO MỚI");
            toolbar.setTitleTextAppearance(this, R.style.ToolbarTitleStyle);
            toolbar.setTitleTextColor(getResources().getColor(R.color.white, getTheme()));
        }
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbar);
        etName = findViewById(R.id.editTextComboName);
        etDescription = findViewById(R.id.editTextComboDescription);
        etImageUrl = findViewById(R.id.editTextComboImage);
        etDiscountPercent = findViewById(R.id.editTextDiscountPercent);
        btnSave = findViewById(R.id.buttonAddCombo);
        btnReset = findViewById(R.id.buttonReset);
        progress = findViewById(R.id.progressBar);
        tvSelectedProduct = findViewById(R.id.tvSelectedProducts);
        tvTotalPrice = findViewById(R.id.tvTotalPrice);
        tvComboPrice = findViewById(R.id.tvComboPrice);
        btnSelectProduct = findViewById(R.id.btnSelectProducts);
        
        // Thêm listener để tự động tính lại giá khi % giảm giá thay đổi
        if (etDiscountPercent != null) {
            etDiscountPercent.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(android.text.Editable s) {
                    updateProductInfo();
                }
            });
        }
    }

    private void setupToolbar() {
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void openProductPicker() {
        ComboProductPickerSheet sheet = new ComboProductPickerSheet();
        
        // Set sản phẩm đã chọn (nếu edit)
        if (!selectedProduct.isEmpty()) {
            List<String> productIds = new ArrayList<>();
            for (Product p : selectedProduct) {
                if (p.getId() != null) {
                    productIds.add(p.getId());
                }
            }
            sheet.setPreselectedProductIds(productIds);
        }
        
        sheet.setListener(products -> {
            selectedProduct.clear();
            selectedProduct.addAll(products);
            updateProductInfo();
        });
        sheet.show(getSupportFragmentManager(), "ComboProductPicker");
    }

    private void updateProductInfo() {
        if (selectedProduct.isEmpty()) {
            ((android.widget.TextView) tvSelectedProduct).setText("Chưa chọn sản phẩm");
            ((android.widget.TextView) tvTotalPrice).setText("Tổng giá: 0đ");
            ((android.widget.TextView) tvComboPrice).setText("Giá combo: 0đ");
            return;
        }

        // Hiển thị danh sách sản phẩm
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selectedProduct.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(selectedProduct.get(i).getName());
        }
        ((android.widget.TextView) tvSelectedProduct).setText(sb.toString());

        // Tính tổng giá
        double totalPrice = 0.0;
        for (Product product : selectedProduct) {
            Double price = product.getPrice();
            if (price != null) {
                totalPrice += price;
            }
        }

        // Lấy % giảm giá từ input (mặc định 10% nếu không nhập)
        int discountPercent = 10;
        if (etDiscountPercent != null && etDiscountPercent.getText() != null) {
            String discountStr = etDiscountPercent.getText().toString().trim();
            if (!discountStr.isEmpty()) {
                try {
                    int inputPercent = Integer.parseInt(discountStr);
                    // Giới hạn từ 0-100%
                    if (inputPercent < 0) inputPercent = 0;
                    if (inputPercent > 100) inputPercent = 100;
                    discountPercent = inputPercent;
                } catch (NumberFormatException e) {
                    // Giữ giá trị mặc định nếu parse lỗi
                }
            }
        }

        // Tính giá combo sau giảm giá
        double discountAmount = totalPrice * discountPercent / 100.0;
        double comboPrice = totalPrice - discountAmount;

        ((android.widget.TextView) tvTotalPrice).setText("Tổng giá: " + MoneyUtils.formatVnd(totalPrice));
        ((android.widget.TextView) tvComboPrice).setText(
                String.format("Giá combo (giảm %d%%): %s", discountPercent, MoneyUtils.formatVnd(comboPrice))
        );
    }

    private void loadComboForEdit(String id) {
        setLoading(true);
        comboRepository.fetchComboById(id, new ComboRepository.OnComboListener() {
            @Override
            public void onComboReceived(@NonNull com.pro.milkteaapp.models.Combo combo) {
                setLoading(false);

                etName.setText(combo.getName() != null ? combo.getName() : "");
                etDescription.setText(combo.getDescription() != null ? combo.getDescription() : "");
                etImageUrl.setText(combo.getImageUrl() != null ? combo.getImageUrl() : "");
                
                // Load % giảm giá
                if (combo.getDiscountPercent() != null) {
                    etDiscountPercent.setText(String.valueOf(combo.getDiscountPercent()));
                } else {
                    etDiscountPercent.setText("10"); // Mặc định 10%
                }

                // Load sản phẩm từ productIds
                if (combo.getProductIds() != null && !combo.getProductIds().isEmpty()) {
                    loadProductByIds(combo.getProductIds());
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                setLoading(false);
                Toast.makeText(AdminComboEditorActivity.this, "Lỗi tải combo: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void loadProductByIds(List<String> productIds) {
        // Load từng sản phẩm theo ID
        selectedProduct.clear();
        if (productIds == null || productIds.isEmpty()) {
            updateProductInfo();
            return;
        }
        
        com.pro.milkteaapp.repository.ProductRepository productsRepo = new com.pro.milkteaapp.repository.ProductRepository();
        final int[] loadedCount = {0};
        final int totalCount = productIds.size();
        
        for (String productId : productIds) {
            productsRepo.fetchProductById(productId, new com.pro.milkteaapp.repository.ProductRepository.OnSingleProductListener() {
                @Override
                public void onProductReceived(@NonNull Product product) {
                    selectedProduct.add(product);
                    loadedCount[0]++;
                    if (loadedCount[0] == totalCount) {
                        updateProductInfo();
                    }
                }

                @Override
                public void onError(@NonNull Exception error) {
                    loadedCount[0]++;
                    if (loadedCount[0] == totalCount) {
                        updateProductInfo();
                    }
                }
            });
        }
    }

    private void onSave() {
        if (btnSave != null) btnSave.setEnabled(false);

        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String description = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";
        String imageUrl = etImageUrl.getText() != null ? etImageUrl.getText().toString().trim() : "";

        if (name.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập tên combo!", Toast.LENGTH_SHORT).show();
            if (btnSave != null) btnSave.setEnabled(true);
            return;
        }

        if (selectedProduct.isEmpty()) {
            Toast.makeText(this, "Vui lòng chọn ít nhất một sản phẩm!", Toast.LENGTH_SHORT).show();
            if (btnSave != null) btnSave.setEnabled(true);
            return;
        }

        // Tính giá
        double totalPrice = 0.0;
        List<String> productIds = new ArrayList<>();
        for (Product product : selectedProduct) {
            Double price = product.getPrice();
            if (price != null) {
                totalPrice += price;
            }
            if (product.getId() != null) {
                productIds.add(product.getId());
            }
        }

        // Lấy % giảm giá từ input
        int discountPercent = 10; // Mặc định 10%
        if (etDiscountPercent != null && etDiscountPercent.getText() != null) {
            String discountStr = etDiscountPercent.getText().toString().trim();
            if (!discountStr.isEmpty()) {
                try {
                    int inputPercent = Integer.parseInt(discountStr);
                    // Giới hạn từ 0-100%
                    if (inputPercent < 0) {
                        Toast.makeText(this, "% giảm giá không được âm, đã đặt về 0%", Toast.LENGTH_SHORT).show();
                        inputPercent = 0;
                    }
                    if (inputPercent > 100) {
                        Toast.makeText(this, "% giảm giá không được vượt quá 100%, đã đặt về 100%", Toast.LENGTH_SHORT).show();
                        inputPercent = 100;
                    }
                    discountPercent = inputPercent;
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "% giảm giá không hợp lệ, sử dụng mặc định 10%", Toast.LENGTH_SHORT).show();
                }
            }
        }

        // Tính giá combo sau giảm giá
        double discountAmount = totalPrice * discountPercent / 100.0;
        double comboPrice = totalPrice - discountAmount;

        setLoading(true);

        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("description", description.isEmpty() ? null : description);
        data.put("imageUrl", imageUrl.isEmpty() ? null : imageUrl);
        data.put("price", comboPrice);
        data.put("originalPrice", totalPrice);
        data.put("discountPercent", discountPercent);
        data.put("productIds", productIds);
        data.put("searchableName", name.toLowerCase(Locale.ROOT));
        data.put("status", "active");
        data.put("updatedAt", FieldValue.serverTimestamp());

        if (isEditMode) {
            // UPDATE
            comboRepository.updateCombo(comboId, data, new ComboRepository.OnComboUpdateListener() {
                @Override
                public void onSuccess() {
                    setLoading(false);
                    Toast.makeText(AdminComboEditorActivity.this, "Đã cập nhật combo.", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                }

                @Override
                public void onError(@NonNull Exception error) {
                    setLoading(false);
                    Toast.makeText(AdminComboEditorActivity.this, "Cập nhật thất bại: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    if (btnSave != null) btnSave.setEnabled(true);
                }
            });
        } else {
            // CREATE
            String uid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
            data.put("createdByUid", uid);
            data.put("createdAt", FieldValue.serverTimestamp());

            new ComboIDGenerator().nextComboId()
                    .addOnSuccessListener(newId -> {
                        data.put("id", newId);
                        comboRepository.createCombo(newId, data, new ComboRepository.OnComboCreateListener() {
                            @Override
                            public void onSuccess(@NonNull String comboId) {
                                setLoading(false);
                                Toast.makeText(AdminComboEditorActivity.this, "Đã thêm combo: " + comboId, Toast.LENGTH_SHORT).show();
                                setResult(RESULT_OK);
                                finish();
                            }

                            @Override
                            public void onError(@NonNull Exception error) {
                                setLoading(false);
                                Toast.makeText(AdminComboEditorActivity.this, "Thêm thất bại: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                                if (btnSave != null) btnSave.setEnabled(true);
                            }
                        });
                    })
                    .addOnFailureListener(e -> {
                        setLoading(false);
                        Toast.makeText(AdminComboEditorActivity.this, "Sinh mã combo lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        if (btnSave != null) btnSave.setEnabled(true);
                    });
        }
    }

    private void resetForm() {
        etName.setText("");
        etDescription.setText("");
        etImageUrl.setText("");
        if (etDiscountPercent != null) {
            etDiscountPercent.setText("10"); // Reset về 10%
        }
        selectedProduct.clear();
        updateProductInfo();
    }

    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnSave != null) btnSave.setEnabled(!loading);
        if (btnReset != null) btnReset.setEnabled(!loading);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}

