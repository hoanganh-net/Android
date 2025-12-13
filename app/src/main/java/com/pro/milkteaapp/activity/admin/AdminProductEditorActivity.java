package com.pro.milkteaapp.activity.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.activity.MainActivity;
import com.pro.milkteaapp.generator.ProductsIDGenerator;
import com.pro.milkteaapp.models.Category;
import com.pro.milkteaapp.repository.CategoriesRepository;
import com.pro.milkteaapp.repository.ComboRepository;
import com.pro.milkteaapp.repository.ProductRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Màn hình gộp: Thêm + Sửa sản phẩm.
 * - Nếu có EXTRA_PRODUCT_ID -> Edit mode
 * - Nếu không -> Add mode
 * CRUD gọi Firestore trực tiếp.
 */
public class AdminProductEditorActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT_ID = "productId";

    // Inputs dùng chung
    private TextInputEditText etName, etPrice, etImageUrl, etDescription;
    private MaterialAutoCompleteTextView actCategory;
    private MaterialButton btnPickDrawable;
    private ImageView imagePreview;
    private com.google.android.material.switchmaterial.SwitchMaterial switchActive;

    // Nút theo template
    private Button btnSave, btnReset;
    // Nút mới: Hủy Thay Đổi, Hoàn Thành
    private Button btnCancel, btnComplete;
    private ProgressBar progress;

    private boolean isEditMode = false;
    private String productId;

    // Dropdown
    private final ArrayList<String> categories = new ArrayList<>();
    private ArrayAdapter<String> categoryAdapter;
    private static final boolean REQUIRE_CATEGORY_IN_LIST = true;
    
    // Drawable list
    private final ArrayList<String> drawableList = new ArrayList<>();

    private FirebaseAuth auth;
    private CategoriesRepository categoriesRepository;
    private ProductRepository productsRepository;

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

        // Xác định mode & layout - sử dụng layout chung cho cả add và edit
        productId = getIntent().getStringExtra(EXTRA_PRODUCT_ID);
        isEditMode = productId != null && !productId.trim().isEmpty();
        setContentView(R.layout.admin_activity_add_product);

        // Bind
        bindViewsByTemplate();

        // Firebase
        // Firestore + Auth
        auth = FirebaseAuth.getInstance();
        categoriesRepository = new CategoriesRepository();
        productsRepository = new ProductRepository();

        // Dropdown categories
        categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, categories);
        actCategory.setAdapter(categoryAdapter);
        actCategory.setOnClickListener(v -> actCategory.showDropDown());
        
        // Button chọn drawable
        setupDrawableList();
        if (btnPickDrawable != null) {
            btnPickDrawable.setOnClickListener(v -> showDrawablePickerDialog());
        }
        
        // Setup image preview listener
        setupImagePreview();

        // Load danh mục, xong nếu edit thì load sản phẩm
        loadCategories(() -> {
            if (isEditMode) loadProductForEdit(productId);
        });

        if (btnSave  != null) btnSave.setOnClickListener(v -> onSave());
        if (btnReset != null) btnReset.setOnClickListener(v -> resetForm());
        
        // Setup các nút cho edit mode
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> finish()); // Hủy thay đổi: quay lại không lưu
        }
        
        if (btnComplete != null) {
            btnComplete.setOnClickListener(v -> onSave()); // Hoàn thành: lưu thay đổi
        }
    }

    /* ------------------------- Bind theo template ------------------------- */
    private void bindViewsByTemplate() {
        etName        = findViewById(R.id.editTextProductName);
        etPrice       = findViewById(R.id.editTextProductPrice);
        actCategory   = findViewById(R.id.actCategory);
        etImageUrl    = findViewById(R.id.editTextProductImage);
        etDescription = findViewById(R.id.editTextProductDescription);
        switchActive  = findViewById(R.id.switchActive);
        progress      = findViewById(R.id.progressBar);
        
        // Bind các view này cho cả edit và add mode (có trong cả 2 layout)
        View btnPickDrawableView = findViewById(R.id.btnPickDrawable);
        if (btnPickDrawableView instanceof MaterialButton) {
            btnPickDrawable = (MaterialButton) btnPickDrawableView;
        }
        
        View imagePreviewView = findViewById(R.id.imagePreview);
        if (imagePreviewView instanceof ImageView) {
            imagePreview = (ImageView) imagePreviewView;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
            String title = isEditMode ? getString(R.string.title_edit_product) : getString(R.string.title_add_product);
            // Set title vào TextView custom để căn giữa
            android.widget.TextView titleView = toolbar.findViewById(R.id.toolbarTitle);
            if (titleView != null) {
                titleView.setText(title);
            } else {
                toolbar.setTitle(title);
            }
        }

        // Setup buttons dựa trên mode
        View layoutEditButtons = findViewById(R.id.layoutEditButtons);
        if (layoutEditButtons != null) {
            layoutEditButtons.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
        }
        
        View buttonAddProduct = findViewById(R.id.buttonAddProduct);
        if (buttonAddProduct != null) {
            buttonAddProduct.setVisibility(isEditMode ? View.GONE : View.VISIBLE);
        }
        
        View buttonReset = findViewById(R.id.buttonReset);
        if (buttonReset != null) {
            buttonReset.setVisibility(isEditMode ? View.GONE : View.VISIBLE);
        }

        btnSave  = isEditMode
                ? firstNonNull(findViewById(R.id.buttonUpdateProduct), findViewById(R.id.buttonComplete))
                : firstNonNull(findViewById(R.id.buttonAddProduct),   findViewById(R.id.buttonUpdateProduct));

        btnReset = findViewById(R.id.buttonReset);
        
        // Nút mới cho edit mode
        btnCancel = findViewById(R.id.buttonCancel);
        btnComplete = findViewById(R.id.buttonComplete);
    }

    @SafeVarargs
    private <T> T firstNonNull(T... views) {
        for (T v : views) if (v != null) return v;
        return null;
    }

    /* ------------------------- Load categories ------------------------- */
    private void loadCategories(Runnable onDone) {
        setLoading(true);
        categoriesRepository.fetchActiveCategories(new CategoriesRepository.OnCategoriesListener() {
            @Override
            public void onCategoriesReceived(@NonNull List<Category> categoryList) {
                categories.clear();
                for (com.pro.milkteaapp.models.Category cat : categoryList) {
                    String name = cat.getName();
                    if (name != null && !name.trim().isEmpty()) categories.add(name.trim());
                }
                if (categoryAdapter != null) categoryAdapter.notifyDataSetChanged();
                setLoading(false);
                if (onDone != null) onDone.run();
            }

            @Override
            public void onError(@NonNull Exception error) {
                setLoading(false);
                toast("Lỗi tải danh mục: " + error.getMessage());
                if (onDone != null) onDone.run();
            }
        });
    }

    /* ------------------------- Load product (Edit) ------------------------- */
    private void loadProductForEdit(String id) {
        setLoading(true);
        productsRepository.fetchProductById(id, new ProductRepository.OnSingleProductListener() {
            @Override
            public void onProductReceived(@NonNull com.pro.milkteaapp.models.Product product) {
                setLoading(false);

                String name        = product.getName() != null ? product.getName() : "";
                Double price       = product.getPrice();
                String imageUrl    = product.getImageUrl() != null ? product.getImageUrl() : "";
                String description = product.getDescription() != null ? product.getDescription() : "";
                String category    = product.getCategory() != null ? product.getCategory() : "";
                String status      = product.getStatus() != null ? product.getStatus() : "";

                etName.setText(name);
                etPrice.setText(formatPrice(price));
                etImageUrl.setText(imageUrl);
                etDescription.setText(description);
                
                // Set status switch (active nếu status là "active", ngược lại inactive)
                if (switchActive != null) {
                    boolean isActive = "active".equalsIgnoreCase(status);
                    switchActive.setChecked(isActive);
                }
                
                // Preview ảnh
                updateImagePreview(imageUrl);

                // Nếu category trong db đã bị ẩn thì vẫn hiển thị để user thấy
                if (!category.isEmpty() && containsIgnoreCase(categories, category)) {
                    categories.add(category + " (đã ẩn)");
                    if (categoryAdapter != null) categoryAdapter.notifyDataSetChanged();
                }
                actCategory.setText(category, false);
            }

            @Override
            public void onError(@NonNull Exception error) {
                setLoading(false);
                toast("Lỗi tải sản phẩm: " + error.getMessage());
                finish();
            }
        });
    }

    /* ------------------------- Save (Add / Update) ------------------------- */
    private void onSave() {
        if (btnSave != null) btnSave.setEnabled(false);

        String name        = t(etName);
        String priceStr    = t(etPrice);
        String categoryUI  = actCategory.getText() == null ? "" : actCategory.getText().toString().trim();
        String image       = t(etImageUrl);
        String description = t(etDescription);

        if (name.isEmpty() || priceStr.isEmpty() || categoryUI.isEmpty()) {
            toast("Vui lòng nhập đủ Tên, Giá và Danh mục!");
            if (btnSave != null) btnSave.setEnabled(true);
            return;
        }

        String normalizedCat = categoryUI.replace(" (đã ẩn)", "").trim();

        // Nếu bắt buộc chọn trong list mà người dùng gõ tay -> báo lỗi
        if (REQUIRE_CATEGORY_IN_LIST && containsIgnoreCase(categories, normalizedCat)) {
            toast("Vui lòng chọn danh mục hợp lệ từ danh sách!");
            actCategory.showDropDown();
            if (btnSave != null) btnSave.setEnabled(true);
            return;
        }

        Double price = parsePrice(priceStr);
        if (price == null || price <= 0) {
            toast("Giá không hợp lệ!");
            if (btnSave != null) btnSave.setEnabled(true);
            return;
        }

        setLoading(true);

        // Lấy status từ switch
        String status = (switchActive != null && switchActive.isChecked()) 
                ? com.pro.milkteaapp.constants.FirestoreConstants.STATUS_ACTIVE 
                : com.pro.milkteaapp.constants.FirestoreConstants.STATUS_INACTIVE;

        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("price", price);
        data.put("category", normalizedCat);
        data.put("imageUrl", image.isEmpty() ? null : image);
        data.put("description", description.isEmpty() ? null : description);
        data.put("status", status);
        data.put("searchableName", name.toLowerCase(Locale.ROOT));
        data.put("updatedAt", FieldValue.serverTimestamp());

        if (isEditMode) {
            // UPDATE qua Repository
            productsRepository.updateProduct(productId, data, new ProductRepository.OnProductUpdateListener() {
                @Override
                public void onSuccess() {
                    // Tự động cập nhật giá combo chứa sản phẩm này
                    updateCombosContainingProduct(productId);
                    
                    setLoading(false);
                    toast("Đã cập nhật sản phẩm.");
                    setResult(RESULT_OK);
                    // Hoàn thành: quay lại trang trước
                    finish();
                }

                @Override
                public void onError(@NonNull Exception error) {
                    setLoading(false);
                    toast("Cập nhật thất bại: " + error.getMessage());
                    if (btnSave != null) btnSave.setEnabled(true);
                }
            });
        } else {
            // CREATE: sinh productId trước, dùng làm documentId luôn
            String uid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
            data.put("status", "active");
            data.put("createdByUid", uid);
            data.put("createdAt", FieldValue.serverTimestamp());

            new ProductsIDGenerator().nextProductId()
                    .addOnSuccessListener(newId -> {
                        data.put("id", newId); // Lưu vào document để client khác đọc được
                        productsRepository.createProduct(newId, data, new ProductRepository.OnProductCreateListener() {
                            @Override
                            public void onSuccess(@NonNull String productId) {
                                setLoading(false);
                                toast("Đã thêm sản phẩm: " + productId);
                                setResult(RESULT_OK);
                                finish();
                            }

                            @Override
                            public void onError(@NonNull Exception error) {
                                setLoading(false);
                                toast("Thêm thất bại: " + error.getMessage());
                                if (btnSave != null) btnSave.setEnabled(true);
                            }
                        });
                    })
                    .addOnFailureListener(e -> {
                        setLoading(false);
                        toast("Sinh mã sản phẩm lỗi: " + e.getMessage());
                        if (btnSave != null) btnSave.setEnabled(true);
                    });
        }
    }

    private void resetForm() {
        etName.setText("");
        etPrice.setText("");
        actCategory.setText("", false);
        etImageUrl.setText("");
        etDescription.setText("");
        if (imagePreview != null) {
            imagePreview.setImageResource(R.drawable.ic_placeholder);
        }
        etName.requestFocus();
    }

    /* ------------------------- Utils ------------------------- */
    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnSave  != null) btnSave.setEnabled(!loading);
        if (btnReset != null) btnReset.setEnabled(!loading);
    }

    private static String t(TextInputEditText e) {
        return e.getText() != null ? e.getText().toString().trim() : "";
    }

    /** true nếu list có chứa v (bỏ qua hoa thường) */
    private static boolean containsIgnoreCase(ArrayList<String> list, String v) {
        for (String s : list) {
            if (s.equalsIgnoreCase(v)) return false;
        }
        return true;
    }

    private static String formatPrice(Double price) {
        if (price == null) return "";
        return String.valueOf(Math.round(price));
    }

    private static Double parsePrice(String raw) {
        try {
            String norm = raw.replaceAll("[^\\d.]", "").replace(",", "").trim();
            if (norm.isEmpty()) return null;
            return Double.parseDouble(norm);
        } catch (Exception e) { return null; }
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
    
    /* ------------------------- Setup Drawable List ------------------------- */
    private void setupDrawableList() {
        drawableList.clear();
        // Danh sách drawable resources phổ biến
        drawableList.add("milktea_matcha");
        drawableList.add("milktea_taro");
        drawableList.add("milktea_olong");
        drawableList.add("milktea_greenthai");
        drawableList.add("milktea_strawberry");
        drawableList.add("milktea_chocolate");
        drawableList.add("milktea_cheese");
        drawableList.add("milktea_caramel");
        drawableList.add("milktea_blueberry");
        drawableList.add("milktea_mango");
        drawableList.add("milktea_brownsugar");
        drawableList.add("milktea");
        drawableList.add("tea_blacktea");
        drawableList.add("tea_hongtratac");
        drawableList.add("tea_tranhaihoa");
        drawableList.add("tea_tranhaihoakem");
        drawableList.add("tea_trasuibot");
        drawableList.add("juice_apple");
        drawableList.add("juice_orange");
        drawableList.add("juice_pineapple");
        drawableList.add("juice_watermelon");
        drawableList.add("juice_bublecoconut");
        drawableList.add("juice_mix");
        drawableList.add("milk_coffe");
        drawableList.add("black_coffe");
        drawableList.add("brown_coffe");
    }
    
    /* ------------------------- Show Drawable Picker Dialog ------------------------- */
    private void showDrawablePickerDialog() {
        // Chuyển danh sách thành mảng String để dùng với AlertDialog
        String[] drawableArray = drawableList.toArray(new String[0]);
        
        new android.app.AlertDialog.Builder(this)
                .setTitle("Chọn hình ảnh từ Drawable")
                .setItems(drawableArray, (dialog, which) -> {
                    if (which >= 0 && which < drawableList.size()) {
                        String selectedDrawable = drawableList.get(which);
                        etImageUrl.setText(selectedDrawable);
                        updateImagePreview(selectedDrawable);
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
    
    /* ------------------------- Setup Image Preview ------------------------- */
    private void setupImagePreview() {
        // Listen text change để tự động preview
        etImageUrl.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(android.text.Editable s) {
                String imageUrl = s != null ? s.toString().trim() : "";
                updateImagePreview(imageUrl);
            }
        });
        
        // Preview ảnh ban đầu nếu có
        if (isEditMode && etImageUrl.getText() != null) {
            String currentImage = etImageUrl.getText().toString().trim();
            if (!currentImage.isEmpty()) {
                updateImagePreview(currentImage);
            }
        }
    }
    
    /* ------------------------- Update Image Preview ------------------------- */
    private void updateImagePreview(String imageUrlOrName) {
        if (imagePreview == null) return;
        
        if (imageUrlOrName == null || imageUrlOrName.trim().isEmpty()) {
            imagePreview.setImageResource(R.drawable.ic_placeholder);
            return;
        }
        
        // Sử dụng ImageLoader để load ảnh (hỗ trợ cả URL và drawable)
        com.pro.milkteaapp.utils.ImageLoader.load(
            imagePreview,
            imageUrlOrName.trim(),
            R.drawable.ic_placeholder
        );
    }

    /**
     * Tự động cập nhật giá của tất cả combo chứa sản phẩm này
     */
    private void updateCombosContainingProduct(String productId) {
        ComboRepository comboRepository = new ComboRepository();
        ProductRepository productRepo = new ProductRepository();
        
        // Tìm tất cả combo chứa productId
        comboRepository.findCombosContainingProduct(productId, new ComboRepository.OnCombosListener() {
            @Override
            public void onCombosReceived(@NonNull List<com.pro.milkteaapp.models.Combo> combos) {
                if (combos.isEmpty()) {
                    return; // Không có combo nào chứa sản phẩm này
                }
                
                // Cập nhật giá cho từng combo
                final int[] updatedCount = {0};
                final int totalCombos = combos.size();
                
                for (com.pro.milkteaapp.models.Combo combo : combos) {
                    comboRepository.recalculateComboPrice(combo.getId(), productRepo, new ComboRepository.OnComboUpdateListener() {
                        @Override
                        public void onSuccess() {
                            updatedCount[0]++;
                            if (updatedCount[0] == totalCombos) {
                                android.util.Log.d("AdminProductEditor", "Đã cập nhật giá cho " + totalCombos + " combo");
                            }
                        }

                        @Override
                        public void onError(@NonNull Exception error) {
                            updatedCount[0]++;
                            android.util.Log.e("AdminProductEditor", "Lỗi cập nhật combo " + combo.getId() + ": " + error.getMessage());
                        }
                    });
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                android.util.Log.e("AdminProductEditor", "Lỗi tìm combo chứa sản phẩm: " + error.getMessage());
            }
        });
    }
    
}
