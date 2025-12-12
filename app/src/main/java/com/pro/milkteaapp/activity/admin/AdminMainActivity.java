package com.pro.milkteaapp.activity.admin;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.auth.FirebaseAuth;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.activity.LoginActivity;
import com.pro.milkteaapp.activity.MainActivity;
import com.pro.milkteaapp.databinding.AdminActivityMainBinding;
import com.pro.milkteaapp.fragment.admin.AdminOrdersFragment;
import com.pro.milkteaapp.fragment.admin.AdminStatisticsFragment;
import com.pro.milkteaapp.fragment.admin.AdminChatFragment;
import com.pro.milkteaapp.fragment.admin.management.ManagementFragment;
import com.pro.milkteaapp.repository.UsersRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AdminMainActivity extends AppCompatActivity {

    private AdminActivityMainBinding binding;

    private static final long NAV_DEBOUNCE_MS = 400L;
    private long lastNavClick = 0L;

    public static final String EXTRA_ADMIN_TARGET = "admin_tab"; // orders, users, stats, manage

    private static final String STATE_SELECTED_TAB = "state_selected_tab";
    // mặc định tab Đơn hàng
    private @IdRes int currentTabId = R.id.adminOrders;

    private static final String TAG_ORDERS     = "tab_orders";
    private static final String TAG_CHAT        = "tab_chat";
    private static final String TAG_STATISTICS = "tab_statistics";
    private static final String TAG_MANAGEMENT = "tab_management";

    private final Map<Integer, String> idToTag = new HashMap<>();
    private final Map<String, Fragment> tagToFragment = new HashMap<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            // nếu chưa đăng nhập thì đá về login
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                goToLoginRoot();
                return;
            }

            binding = AdminActivityMainBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());

            // map id bottom-nav -> tag fragment
            idToTag.put(R.id.adminOrders,     TAG_ORDERS);
            idToTag.put(R.id.adminChat,        TAG_CHAT);
            idToTag.put(R.id.adminStatistics, TAG_STATISTICS);
            idToTag.put(R.id.adminManagement, TAG_MANAGEMENT);

            setupToolbar();
            setupBottomNavigation();
            setupBackPressDispatcher(); // <-- migrate back gestures

            // kiểm tra quyền từ Session trước
            SessionManager sm = new SessionManager(this);
            if (sm.hasCachedRole()) {
                if (!sm.isAdmin()) {
                    Toast.makeText(this, getString(R.string.no_admin_access), Toast.LENGTH_SHORT).show();
                    goToUserRoot();
                    return;
                }
                continueInit(savedInstanceState);
            } else {
                fetchRoleThenInit(sm, savedInstanceState);
            }
        } catch (Exception e) {
            // Lỗi khởi tạo -> fallback về MainActivity
            Toast.makeText(this, getString(R.string.init_error, e.getMessage()), Toast.LENGTH_SHORT).show();
            goToUserRoot();
        }
    }

    private void continueInit(@Nullable Bundle savedInstanceState) {
        try {
            if (binding == null) {
                Toast.makeText(this, getString(R.string.init_error, "Binding is null"), Toast.LENGTH_SHORT).show();
                goToUserRoot();
                return;
            }
            
            if (savedInstanceState != null) {
                currentTabId = savedInstanceState.getInt(STATE_SELECTED_TAB, R.id.adminOrders);
            }

            restoreOrCreateFragments();

            if (savedInstanceState == null) {
                // xử lý intent điều hướng sang tab cụ thể
                if (!handleIntent(getIntent())) {
                    // mặc định chọn đơn hàng
                    binding.bottomNavigationView.setSelectedItemId(R.id.adminOrders);
                    switchTo(R.id.adminOrders);
                }
            } else {
                binding.bottomNavigationView.setSelectedItemId(currentTabId);
                switchTo(currentTabId);
            }
        } catch (Exception e) {
            // Lỗi khởi tạo fragment -> fallback về tab đơn hàng
            Toast.makeText(this, getString(R.string.init_error, e.getMessage()), Toast.LENGTH_SHORT).show();
            if (binding != null) {
                binding.bottomNavigationView.setSelectedItemId(R.id.adminOrders);
            }
        }
    }

    private void fetchRoleThenInit(SessionManager sm, @Nullable Bundle savedInstanceState) {
        String uid = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        UsersRepository usersRepository = new UsersRepository();
        
        // Thử fetch bằng authUid trước
        usersRepository.fetchUserByAuthUid(uid, new UsersRepository.OnUserListener() {
            @Override
            public void onUserReceived(@NonNull com.pro.milkteaapp.models.User user) {
                String role = user.getRole();
                if (role == null || role.trim().isEmpty()) {
                    // backward-compatible: kiểm tra isAdmin nếu có
                    role = "user"; // default
                }
                sm.setRole(role);
                if (!sm.isAdmin()) {
                    Toast.makeText(AdminMainActivity.this, "Bạn không có quyền truy cập (admin-only)", Toast.LENGTH_SHORT).show();
                    goToUserRoot();
                    return;
                }
                continueInit(savedInstanceState);
            }

            @Override
            public void onError(@NonNull Exception error) {
                // Fallback: thử fetch bằng userId nếu có trong session
                String sessionUid = sm.getUid();
                if (!TextUtils.isEmpty(sessionUid)) {
                    usersRepository.fetchUserById(sessionUid, new UsersRepository.OnUserListener() {
                        @Override
                        public void onUserReceived(@NonNull com.pro.milkteaapp.models.User user) {
                            String role = user.getRole();
                            if (role == null || role.trim().isEmpty()) {
                                role = "user";
                            }
                            sm.setRole(role);
                            if (!sm.isAdmin()) {
                                Toast.makeText(AdminMainActivity.this, "Bạn không có quyền truy cập (admin-only)", Toast.LENGTH_SHORT).show();
                                goToUserRoot();
                                return;
                            }
                            continueInit(savedInstanceState);
                        }

                        @Override
                        public void onError(@NonNull Exception error2) {
                            Toast.makeText(AdminMainActivity.this, getString(R.string.cannot_check_permission, error2.getMessage()), Toast.LENGTH_SHORT).show();
                            goToUserRoot();
                        }
                    });
                } else {
                    Toast.makeText(AdminMainActivity.this, getString(R.string.cannot_check_permission, error.getMessage()), Toast.LENGTH_SHORT).show();
                    goToUserRoot();
                }
            }
        });
    }

    private void setupToolbar() {
        if (binding == null) return;
        
        setSupportActionBar(binding.toolbar);
        updateToolbarTitle(currentTabId);

        // để click menu trên toolbar cũng chạy vào onOptionsItemSelected
        binding.toolbar.setOnMenuItemClickListener(this::onOptionsItemSelected);
        
        // Đổi màu menu icon (3 chấm dọc) thành trắng
        // Sử dụng post để đảm bảo menu đã được inflate
        binding.toolbar.post(() -> {
            if (binding != null && binding.toolbar.getOverflowIcon() != null) {
                binding.toolbar.getOverflowIcon().setTint(getResources().getColor(R.color.white, getTheme()));
            }
        });
        
        // Hiển thị nút menu drawer ở bên trái cho admin
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_menu);
        }
        binding.toolbar.setNavigationOnClickListener(v -> {
            // Mở drawer của fragment hiện tại
            openCurrentFragmentDrawer();
        });
    }

    /**
     * Mở drawer của fragment hiện tại
     */
    private void openCurrentFragmentDrawer() {
        Fragment current = tagToFragment.get(idToTag.get(currentTabId));
        if (current == null) return;
        
        if (current instanceof com.pro.milkteaapp.fragment.admin.management.ManagementFragment) {
            // ManagementFragment có drawer chung cho tất cả child fragments
            ((com.pro.milkteaapp.fragment.admin.management.ManagementFragment) current).openDrawer();
        } else if (current instanceof AdminChatFragment) {
            ((AdminChatFragment) current).openDrawer();
        } else if (current instanceof AdminStatisticsFragment) {
            ((AdminStatisticsFragment) current).openDrawer();
        } else if (current instanceof AdminOrdersFragment) {
            ((AdminOrdersFragment) current).openDrawer();
        }
    }
    
    private void setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener(onNavSelectedListener);
        binding.bottomNavigationView.setOnItemReselectedListener(item -> {
            // tuỳ anh muốn scroll-to-top thì implement trong fragment
        });
    }

    private final NavigationBarView.OnItemSelectedListener onNavSelectedListener = item -> {
        long now = SystemClock.elapsedRealtime();
        if (now - lastNavClick < NAV_DEBOUNCE_MS) return false;
        lastNavClick = now;
        return switchTo(item.getItemId());
    };

    private void restoreOrCreateFragments() {
        Fragment orders     = getSupportFragmentManager().findFragmentByTag(TAG_ORDERS);
        Fragment chat       = getSupportFragmentManager().findFragmentByTag(TAG_CHAT);
        Fragment statistics = getSupportFragmentManager().findFragmentByTag(TAG_STATISTICS);
        Fragment management = getSupportFragmentManager().findFragmentByTag(TAG_MANAGEMENT);

        if (orders == null)     orders     = new AdminOrdersFragment();
        if (chat == null)       chat       = new AdminChatFragment();
        if (statistics == null) statistics = new AdminStatisticsFragment();
        if (management == null) management = new ManagementFragment();

        tagToFragment.put(TAG_ORDERS, orders);
        tagToFragment.put(TAG_CHAT, chat);
        tagToFragment.put(TAG_STATISTICS, statistics);
        tagToFragment.put(TAG_MANAGEMENT, management);

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        if (!orders.isAdded())     ft.add(R.id.fragment_container, orders, TAG_ORDERS).hide(orders);
        if (!chat.isAdded())       ft.add(R.id.fragment_container, chat, TAG_CHAT).hide(chat);
        if (!statistics.isAdded()) ft.add(R.id.fragment_container, statistics, TAG_STATISTICS).hide(statistics);
        if (!management.isAdded()) ft.add(R.id.fragment_container, management, TAG_MANAGEMENT).hide(management);
        ft.commitNowAllowingStateLoss();
    }

    private boolean switchTo(@IdRes int menuId) {
        if (binding == null) return false;
        
        String targetTag = idToTag.get(menuId);
        if (targetTag == null) return false;

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

        // ẩn hết
        for (Map.Entry<String, Fragment> e : tagToFragment.entrySet()) {
            Fragment f = e.getValue();
            if (f != null && f.isAdded()) {
                ft.hide(f);
            }
        }

        // hiện fragment mục tiêu
        Fragment target = tagToFragment.get(targetTag);
        if (target == null) return false;
        ft.show(target);
        ft.commitAllowingStateLoss();

        currentTabId = menuId;
        updateToolbarTitle(menuId);
        return true;
    }

    
    private void updateToolbarTitle(@IdRes int menuId) {
        if (binding == null || getSupportActionBar() == null) return;
        
        int titleRes = R.string.app_name;
        if (menuId == R.id.adminOrders)          titleRes = R.string.admin_title_orders;
        else if (menuId == R.id.adminChat)       titleRes = R.string.admin_title_chat;
        else if (menuId == R.id.adminStatistics) titleRes = R.string.admin_title_statistics;
        else if (menuId == R.id.adminManagement) titleRes = R.string.admin_title_management;
        
        // Set title vào TextView custom để căn giữa
        android.widget.TextView titleView = binding.toolbar.findViewById(R.id.toolbarTitle);
        if (titleView != null) {
            titleView.setText(getString(titleRes));
        } else {
            binding.toolbar.setTitle(getString(titleRes));
        }
        
        // Hiển thị icon menu cho tất cả các tab để mở drawer
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_menu);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private boolean handleIntent(Intent intent) {
        if (intent == null || binding == null) return false;
        
        String target = intent.getStringExtra(EXTRA_ADMIN_TARGET);
        if (target == null) return false;

        @IdRes int menuId = mapTargetToMenuId(target);
        if (menuId == 0) return false;

        if (binding.bottomNavigationView.getSelectedItemId() == menuId) {
            switchTo(menuId);
        } else {
            binding.bottomNavigationView.setSelectedItemId(menuId);
        }
        return true;
    }

    // MIGRATION: sử dụng OnBackPressedDispatcher thay cho onBackPressed() deprecated
    private void setupBackPressDispatcher() {
        // back về tab Đơn hàng nếu đang ở tab khác
        // hành vi mặc định: đóng Activity
        // Callback back mới (AndroidX)
        OnBackPressedCallback backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Kiểm tra drawer của fragment hiện tại
                Fragment current = tagToFragment.get(idToTag.get(currentTabId));
                if (current != null) {
                    // Kiểm tra drawer của ManagementFragment
                    if (current instanceof ManagementFragment mgmt) {
                        if (mgmt.isDrawerOpen()) {
                            mgmt.closeDrawer();
                            return;
                        }
                    }
                    // Kiểm tra drawer của ChatFragment
                    else if (current instanceof AdminChatFragment chat) {
                        if (chat.isDrawerOpen()) {
                            chat.closeDrawer();
                            return;
                        }
                    }
                    // Kiểm tra drawer của StatisticsFragment
                    else if (current instanceof AdminStatisticsFragment stats) {
                        if (stats.isDrawerOpen()) {
                            stats.closeDrawer();
                            return;
                        }
                    }
                    // Kiểm tra drawer của OrdersFragment
                    else if (current instanceof AdminOrdersFragment orders) {
                        if (orders.isDrawerOpen()) {
                            orders.closeDrawer();
                            return;
                        }
                    }
                }
                
                // Xử lý back press cho child fragments
                if (current != null && current.getChildFragmentManager().popBackStackImmediate()) {
                    return;
                }
                
                // back về tab Đơn hàng nếu đang ở tab khác
                if (currentTabId != R.id.adminOrders) {
                    if (binding != null) {
                        binding.bottomNavigationView.setSelectedItemId(R.id.adminOrders);
                    }
                    switchTo(R.id.adminOrders);
                } else {
                    // hành vi mặc định: đóng Activity
                    finish();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
    }

    private void goToUserRoot() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void goToLoginRoot() {
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // GIỮ NGUYÊN: dùng đúng cái XML anh đã gửi
        getMenuInflater().inflate(R.menu.menu_admin_toolbar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        // CHỈ xử lý những item hiện đang có trong XML
        // Icon profile đã được xóa
        if (id == R.id.action_switch_to_user) {
            goToUserRoot();
            return true;
        } else if (id == R.id.action_logout) {
            logout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void logout() {
        try {
            FirebaseAuth.getInstance().signOut();
        } catch (Throwable e) {
            android.util.Log.w("AdminMain", "Failed to sign out: " + e.getMessage());
        }
        new SessionManager(this).clear();
        goToLoginRoot();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SELECTED_TAB, currentTabId);
    }

    @IdRes
    private int mapTargetToMenuId(@NonNull String target) {
        return switch (target) {
            case "orders" -> R.id.adminOrders;
            case "chat" -> R.id.adminChat;
            case "stats", "statistics" -> R.id.adminStatistics;
            case "manage", "management" -> R.id.adminManagement;
            default -> 0;
        };
    }

    // interface để fragment nào thích thì implement
    public interface SupportsRefresh { void refresh(); }
}
