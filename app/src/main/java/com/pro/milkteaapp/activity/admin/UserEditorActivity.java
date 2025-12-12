package com.pro.milkteaapp.activity.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.activity.MainActivity;
import com.pro.milkteaapp.models.User;
import com.pro.milkteaapp.repository.UsersRepository;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Activity để chỉnh sửa user
 */
public class UserEditorActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "userId";

    // Views
    private TextInputEditText etName;
    private TextInputEditText etEmail;
    private TextInputEditText etPhone;
    private TextInputEditText etAddress;
    private MaterialAutoCompleteTextView actRole;
    private SwitchMaterial swActive;
    private Button btnCancel;
    private Button btnComplete;
    private ProgressBar progress;

    private boolean isEditMode = false;
    private String userId;
    private UsersRepository usersRepository;
    private final String[] roles = {"admin", "user", "manager", "staff"};

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
        userId = getIntent().getStringExtra(EXTRA_USER_ID);
        isEditMode = userId != null && !userId.trim().isEmpty();
        setContentView(R.layout.admin_activity_edit_user);

        // Bind views
        bindViews();

        // Repository
        usersRepository = new UsersRepository();

        // Setup
        setupRoleDropdown();
        setupListeners();

        // Load user nếu edit mode
        if (isEditMode) {
            loadUser(userId);
        } else {
            // Không hỗ trợ add user từ Activity này (vẫn dùng dialog vì cần password)
            Toast.makeText(this, "Chức năng thêm user vui lòng sử dụng nút thêm", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Update toolbar title
        updateToolbarTitle();
    }

    private void bindViews() {
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etAddress = findViewById(R.id.etAddress);
        actRole = findViewById(R.id.actRole);
        swActive = findViewById(R.id.swActive);
        btnCancel = findViewById(R.id.buttonCancel);
        btnComplete = findViewById(R.id.buttonComplete);
        progress = findViewById(R.id.progressBar);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    private void updateToolbarTitle() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        String title = isEditMode ? "CHỈNH SỬA NGƯỜI DÙNG" : "THÊM NGƯỜI DÙNG MỚI";
        TextView titleView = toolbar.findViewById(R.id.toolbarTitle);
        if (titleView != null) {
            titleView.setText(title);
        } else {
            toolbar.setTitle(title);
        }
    }

    private void setupRoleDropdown() {
        android.widget.ArrayAdapter<String> roleAdapter = new android.widget.ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                roles
        );
        actRole.setAdapter(roleAdapter);
    }

    private void setupListeners() {
        btnCancel.setOnClickListener(v -> finish());
        btnComplete.setOnClickListener(v -> saveUser());
        etName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(60)});
    }

    private void loadUser(String id) {
        setLoading(true);
        usersRepository.fetchUserById(id, new UsersRepository.OnUserListener() {
            @Override
            public void onUserReceived(@NonNull User user) {
                setLoading(false);
                
                etName.setText(safeShow(user.getFullName()));
                etEmail.setText(safeShow(user.getEmail()));
                etPhone.setText(safeShow(user.getPhone()));
                etAddress.setText(safeShow(user.getAddress()));
                
                // Set role
                String role = safeShow(user.getRole()).trim().toLowerCase(Locale.ROOT);
                if (role.isEmpty()) role = "user";
                int idx = Arrays.asList(roles).indexOf(role);
                if (idx < 0) idx = Arrays.asList(roles).indexOf("user");
                actRole.setText(roles[idx], false);
                
                // Set active
                swActive.setChecked(user.isActive());
            }

            @Override
            public void onError(@NonNull Exception error) {
                setLoading(false);
                toast("Lỗi tải user: " + error.getMessage());
                finish();
            }
        });
    }

    private void saveUser() {
        clearErrors();

        String newName = getTextOrEmpty(etName);
        String newEmail = getTextOrEmpty(etEmail);
        String newPhone = getTextOrEmpty(etPhone);
        String newAddress = getTextOrEmpty(etAddress);
        String newRole = actRole.getText() != null 
                ? actRole.getText().toString().trim().toLowerCase(Locale.ROOT) 
                : "user";

        if (TextUtils.isEmpty(newName)) {
            etName.setError("Tên không được để trống");
            etName.requestFocus();
            return;
        }

        if (!TextUtils.isEmpty(newEmail) && !android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
            etEmail.setError("Email không hợp lệ");
            etEmail.requestFocus();
            return;
        }

        // Validate phone
        if (!TextUtils.isEmpty(newPhone)) {
            String digitsOnly = newPhone.replaceAll("\\D+", "");
            if (digitsOnly.length() < 9 || digitsOnly.length() > 11) {
                etPhone.setError("Số điện thoại không hợp lệ (9-11 chữ số)");
                etPhone.requestFocus();
                return;
            }
        }

        setLoading(true);

        Map<String, Object> data = new HashMap<>();
        data.put("fullName", newName);
        data.put("email", newEmail);
        data.put("phone", newPhone);
        data.put("address", newAddress);
        data.put("role", newRole);
        data.put("active", swActive.isChecked());

        usersRepository.updateUser(userId, data, new UsersRepository.OnUserUpdateListener() {
            @Override
            public void onSuccess() {
                setLoading(false);
                toast("Đã cập nhật người dùng");
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(@NonNull Exception error) {
                setLoading(false);
                toast("Cập nhật thất bại: " + error.getMessage());
            }
        });
    }

    private void clearErrors() {
        etName.setError(null);
        etEmail.setError(null);
        etPhone.setError(null);
        etAddress.setError(null);
    }

    private String getTextOrEmpty(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private String safeShow(String s) {
        return s == null ? "" : s;
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnComplete.setEnabled(!loading);
        btnCancel.setEnabled(!loading);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
