package com.pro.milkteaapp.fragment.admin.management;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.navigation.NavigationView;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.databinding.AdminFragmentManagementBinding;
import com.pro.milkteaapp.fragment.admin.AdminCombosFragment;
import com.pro.milkteaapp.fragment.admin.AdminProductsFragment;
import com.pro.milkteaapp.fragment.admin.AdminUsersFragment;
import com.pro.milkteaapp.interfaces.AddActionHandler;

public class ManagementFragment extends Fragment implements NavigationView.OnNavigationItemSelectedListener {

    private AdminFragmentManagementBinding binding;
    private Fragment currentFragment;
    private int currentMenuId = R.id.nav_products; // Mặc định là Sản phẩm

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = AdminFragmentManagementBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);

        // Setup NavigationView
        binding.navigationView.setNavigationItemSelectedListener(this);

        // Setup DrawerLayout với toolbar (nếu có)
        setupDrawerToggle();

        // Load fragment mặc định
        if (s == null) {
            switchToFragment(R.id.nav_products);
        } else {
            // Restore fragment đã chọn
            currentMenuId = s.getInt("current_menu_id", R.id.nav_products);
            binding.navigationView.setCheckedItem(currentMenuId);
            restoreFragment();
        }

        // FAB: uỷ quyền cho fragment hiện tại nếu fragment đó implements AddActionHandler
        binding.fabAdd.setOnClickListener(v1 -> {
            if (currentFragment instanceof AddActionHandler) {
                ((AddActionHandler) currentFragment).onAddAction();
            }
        });
    }

    private void setupDrawerToggle() {
        // Có thể thêm ActionBarDrawerToggle nếu cần
        // Hiện tại chỉ cần NavigationView
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        
        // Đóng drawer
        binding.drawerLayout.closeDrawer(GravityCompat.START);
        
        // Chuyển fragment
        if (itemId != currentMenuId) {
            switchToFragment(itemId);
        }
        
        return true;
    }

    private void switchToFragment(int menuId) {
        Fragment fragment = getFragmentForMenuId(menuId);
        if (fragment == null) return;

        FragmentTransaction ft = getChildFragmentManager().beginTransaction();
        
        // Ẩn fragment hiện tại nếu có
        if (currentFragment != null && currentFragment.isAdded()) {
            ft.hide(currentFragment);
        }
        
        // Hiển thị fragment mới
        String tag = getTagForMenuId(menuId);
        if (fragment.isAdded()) {
            ft.show(fragment);
        } else {
            ft.add(R.id.fragment_container_management, fragment, tag);
        }
        
        ft.commitAllowingStateLoss();
        
        currentFragment = fragment;
        currentMenuId = menuId;
        binding.navigationView.setCheckedItem(menuId);
    }

    private Fragment getFragmentForMenuId(int menuId) {
        // Tìm fragment đã tồn tại trước
        String tag = getTagForMenuId(menuId);
        Fragment existing = getChildFragmentManager().findFragmentByTag(tag);
        if (existing != null) {
            return existing;
        }

        // Tạo fragment mới
        if (menuId == R.id.nav_products) {
            return new AdminProductsFragment();
        } else if (menuId == R.id.nav_category) {
            return new CategoryManageFragment();
        } else if (menuId == R.id.nav_toppings) {
            return new ToppingManageFragment();
        } else if (menuId == R.id.nav_combo) {
            return new AdminCombosFragment();
        } else if (menuId == R.id.nav_voucher) {
            return new VoucherManageFragment();
        } else if (menuId == R.id.nav_point_redemption) {
            return new PointRedemptionManageFragment();
        } else if (menuId == R.id.nav_rating) {
            return new RatingManageFragment();
        } else if (menuId == R.id.nav_users) {
            return new AdminUsersFragment();
        }
        return null;
    }

    private String getTagForMenuId(int menuId) {
        if (menuId == R.id.nav_products) return "tag_products";
        if (menuId == R.id.nav_category) return "tag_category";
        if (menuId == R.id.nav_toppings) return "tag_toppings";
        if (menuId == R.id.nav_combo) return "tag_combo";
        if (menuId == R.id.nav_voucher) return "tag_voucher";
        if (menuId == R.id.nav_point_redemption) return "tag_point_redemption";
        if (menuId == R.id.nav_rating) return "tag_rating";
        if (menuId == R.id.nav_users) return "tag_users";
        return "tag_unknown";
    }

    private void restoreFragment() {
        Fragment fragment = getFragmentForMenuId(currentMenuId);
        if (fragment != null) {
            String tag = getTagForMenuId(currentMenuId);
            Fragment existing = getChildFragmentManager().findFragmentByTag(tag);
            if (existing != null) {
                currentFragment = existing;
            } else {
                FragmentTransaction ft = getChildFragmentManager().beginTransaction();
                ft.add(R.id.fragment_container_management, fragment, tag);
                ft.commitAllowingStateLoss();
                currentFragment = fragment;
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("current_menu_id", currentMenuId);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        currentFragment = null;
    }

    /**
     * Mở drawer từ bên ngoài (ví dụ từ toolbar)
     */
    public void openDrawer() {
        if (binding != null) {
            binding.drawerLayout.openDrawer(GravityCompat.START);
        }
    }
    
    /**
     * Đóng drawer
     */
    public void closeDrawer() {
        if (binding != null) {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
        }
    }
    
    /**
     * Kiểm tra drawer có đang mở không
     */
    public boolean isDrawerOpen() {
        return binding != null && binding.drawerLayout.isDrawerOpen(GravityCompat.START);
    }
    
    /**
     * Xử lý click vào item trong drawer từ AdminMainActivity
     */
    public void handleDrawerItemClick(int itemId) {
        if (itemId != currentMenuId) {
            switchToFragment(itemId);
        }
    }
    
    /**
     * Lấy fragment con hiện tại
     */
    public Fragment getCurrentChildFragment() {
        return currentFragment;
    }
}
