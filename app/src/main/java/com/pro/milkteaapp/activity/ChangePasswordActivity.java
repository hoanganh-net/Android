package com.pro.milkteaapp.activity;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.pro.milkteaapp.R;
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
        // Set title vào TextView custom để căn giữa
        android.widget.TextView titleView = binding.toolbar.findViewById(R.id.toolbarTitle);
        if (titleView != null) {
            titleView.setText("ĐỔI MẬT KHẨU");
        } else {
            binding.toolbar.setTitle("ĐỔI MẬT KHẨU");
        }
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