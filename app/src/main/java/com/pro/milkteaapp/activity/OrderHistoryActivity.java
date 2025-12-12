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
        // Set title vào TextView custom để căn giữa
        android.widget.TextView titleView = b.toolbar.findViewById(R.id.toolbarTitle);
        if (titleView != null) {
            titleView.setText(getString(R.string.order_history));
        } else {
            b.toolbar.setTitle(getString(R.string.order_history));
        }

        if (savedInstanceState == null) {
            OrdersFragment fragment = OrdersFragment.newInstance("ALL", false);
            getSupportFragmentManager().beginTransaction()
                    .replace(b.container.getId(), fragment)
                    .commit();
            
            // Setup nút filter trong toolbar để mở drawer của fragment
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
