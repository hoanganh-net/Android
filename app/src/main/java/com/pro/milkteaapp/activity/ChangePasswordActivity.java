package com.pro.milkteaapp.activity;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.pro.milkteaapp.databinding.UserActivityChangePasswordBinding;

import java.util.Objects;

public class ChangePasswordActivity extends AppCompatActivity {

    private UserActivityChangePasswordBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = UserActivityChangePasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();
        setupChangePasswordButton();
    }

    private android.widget.TextView customTitleView;
    
    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        centerToolbarTitle(binding.toolbar, "ĐỔI MẬT KHẨU");
    }
    
    private void centerToolbarTitle(com.google.android.material.appbar.MaterialToolbar toolbar, String title) {
        customTitleView = com.pro.milkteaapp.utils.ToolbarUtils.centerToolbarTitle(
                toolbar, title, customTitleView, getSupportActionBar());
    }

    private void setupChangePasswordButton() {
        binding.buttonChangePassword.setOnClickListener(v -> {
            String currentPassword = Objects.requireNonNull(binding.editTextCurrentPassword.getText()).toString();
            String newPassword = Objects.requireNonNull(binding.editTextNewPassword.getText()).toString();
            String confirmPassword = Objects.requireNonNull(binding.editTextConfirmPassword.getText()).toString();

            if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Vui lòng điền đầy đủ thông tin", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPassword.equals(confirmPassword)) {
                Toast.makeText(this, "Mật khẩu mới không khớp", Toast.LENGTH_SHORT).show();
                return;
            }

            // Change password logic here
            Toast.makeText(this, "Đã đổi mật khẩu thành công", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

}