package com.pro.milkteaapp.activity;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.pro.milkteaapp.R;
import com.pro.milkteaapp.databinding.UserActivitySettingsBinding;

public class SettingsActivity extends AppCompatActivity {

    private UserActivitySettingsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = UserActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setTitle("CÀI ĐẶT");
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}