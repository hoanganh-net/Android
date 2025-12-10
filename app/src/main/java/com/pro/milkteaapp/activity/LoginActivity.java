package com.pro.milkteaapp.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.activity.admin.AdminMainActivity;
import com.pro.milkteaapp.repository.AuthRepository;
import com.pro.milkteaapp.databinding.UserActivityLoginBinding;

public class LoginActivity extends AppCompatActivity {

    // Bật nếu bạn yêu cầu email đã xác thực mới cho vào app
    private static final boolean EMAIL_VERIFY_REQUIRED = false;

    private UserActivityLoginBinding binding;

    // Repo lo hết phần Firebase + Firestore + lưu local
    private AuthRepository authRepository;

    // Tránh double navigate
    private boolean isNavigating = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = UserActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Khởi tạo repository
        authRepository = new AuthRepository(
                FirebaseAuth.getInstance(),
                FirebaseFirestore.getInstance(),
                getApplicationContext(),
                EMAIL_VERIFY_REQUIRED
        );

        // Chỉ auto-route nếu đã từng ĐĂNG NHẬP (is_logged_in == true)
        checkExistingLoginAndRoute();

        // Đăng nhập
        binding.loginButton.setOnClickListener(v -> {
            String email = safeTrim(binding.emailEditText.getText() != null
                    ? binding.emailEditText.getText().toString() : "");
            String password = safeTrim(binding.passwordEditText.getText() != null
                    ? binding.passwordEditText.getText().toString() : "");

            if (!validate(email, password)) return;
            loginUser(email, password);
        });

        // Đăng ký
        binding.registerTextView.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));

        // Quên mật khẩu
        binding.forgotPasswordTextView.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class)));
    }

    // ====== CHỈ AUTO-ROUTE KHI IS_LOGGED_IN = TRUE ======
    private void checkExistingLoginAndRoute() {
        authRepository.autoLoginIfPossible(new AuthRepository.AutoLoginCallback() {
            @Override
            public void onSuccess(String role) {
                // Đã auto-login được → route thẳng
                routeByRole(role);
            }

            @Override
            public void onNotAvailable() {
                // Chưa từng đăng nhập hoặc không đủ điều kiện → ở lại màn hình login
            }

            @Override
            public void onError(String message) {
                // Phiên cũ lỗi / inconsistency → báo cho user, ở lại màn hình login
                if (!TextUtils.isEmpty(message)) {
                    Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // ====== LOGIN CHÍNH THỨC TỪ UI ======
    private void loginUser(String email, String password) {
        setLoading(true);
        authRepository.login(email, password, new AuthRepository.LoginCallback() {
            @Override
            public void onSuccess(String role) {
                setLoading(false);
                routeByRole(role);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showError(message);
            }
        });
    }

    // ====== ROUTING THEO ROLE ======
    private void routeByRole(String roleRaw) {
        if (isNavigating) return; // tránh double navigate
        isNavigating = true;

        String role = authRepository.normalizeRole(roleRaw);
        if ("admin".equals(role)) {
            redirectToAdminMain();
        } else {
            redirectToUserMain();
        }
    }

    private void redirectToAdminMain() {
        Toast.makeText(this, "Chào mừng Admin!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(LoginActivity.this, AdminMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void redirectToUserMain() {
        Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ====== TIỆN ÍCH UI ======
    private boolean validate(String email, String password) {
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            showError("Vui lòng nhập email và mật khẩu");
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Email không hợp lệ");
            return false;
        }
        if (password.length() < 6) {
            showError("Mật khẩu tối thiểu 6 ký tự");
            return false;
        }
        return true;
    }

    private void setLoading(boolean loading) {
        if (binding == null) return;
        binding.loginButton.setEnabled(!loading);
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private void showError(String message) {
        Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
