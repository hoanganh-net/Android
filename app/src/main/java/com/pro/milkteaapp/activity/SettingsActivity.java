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

    private android.widget.TextView customTitleView;
    
    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        centerToolbarTitle(binding.toolbar, "CÀI ĐẶT");
    }
    
    private void centerToolbarTitle(com.google.android.material.appbar.MaterialToolbar toolbar, String title) {
        customTitleView = com.pro.milkteaapp.utils.ToolbarUtils.centerToolbarTitle(
                toolbar, title, customTitleView, getSupportActionBar());
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}