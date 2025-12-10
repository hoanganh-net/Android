package com.pro.milkteaapp.activity;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.pro.milkteaapp.R;
import com.pro.milkteaapp.databinding.UserActivityOrderHistoryBinding;
import com.pro.milkteaapp.fragment.OrdersFragment;

public class OrderHistoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UserActivityOrderHistoryBinding b = UserActivityOrderHistoryBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        setSupportActionBar(b.toolbar);
        b.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        centerToolbarTitle(b.toolbar, getString(R.string.order_history));

        if (savedInstanceState == null) {
            OrdersFragment fragment = OrdersFragment.newInstance("ALL", false);
            getSupportFragmentManager().beginTransaction()
                    .replace(b.container.getId(), fragment)
                    .commit();
            
            // Setup nút filter trong toolbar để mở drawer của fragment
            if (b.btnFilter != null) {
                b.btnFilter.setOnClickListener(v -> {
                    // Tìm fragment và gọi method mở drawer
                    OrdersFragment ordersFragment = (OrdersFragment) getSupportFragmentManager()
                            .findFragmentById(b.container.getId());
                    if (ordersFragment != null) {
                        ordersFragment.openFilterDrawer();
                    }
                });
            }
        }
    }
    
    private android.widget.TextView customTitleView;
    
    private void centerToolbarTitle(com.google.android.material.appbar.MaterialToolbar toolbar, String title) {
        customTitleView = com.pro.milkteaapp.utils.ToolbarUtils.centerToolbarTitle(
                toolbar, title, customTitleView, getSupportActionBar());
    }
}
