package com.pro.milkteaapp.fragment.admin;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.firestore.ListenerRegistration;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.activity.admin.AdminMainActivity;
import com.pro.milkteaapp.activity.admin.ChatActivity;
import com.pro.milkteaapp.models.User;
import com.pro.milkteaapp.repository.ChatRepository;
import com.pro.milkteaapp.repository.UsersRepository;
import com.pro.milkteaapp.utils.ImageLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminChatFragment extends Fragment
        implements AdminMainActivity.ScrollToTop, AdminMainActivity.SupportsRefresh {

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private View loadingState, emptyState;
    private ChatUsersAdapter adapter;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    
    private UsersRepository usersRepository;
    private ListenerRegistration listener;
    
    private final List<User> allUsers = new ArrayList<>();
    private String selectedRole = "Tất cả"; // Mặc định hiển thị tất cả

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        usersRepository = new UsersRepository();
        ChatRepository chatRepository = new ChatRepository();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.admin_activity_chat, container, false);
        
        swipeRefresh = v.findViewById(R.id.swipeRefresh);
        recyclerView = v.findViewById(R.id.recycler);
        loadingState = v.findViewById(R.id.loadingState);
        emptyState = v.findViewById(R.id.emptyState);
        drawerLayout = v.findViewById(R.id.drawerLayout);
        navigationView = v.findViewById(R.id.navigationView);
        
        adapter = new ChatUsersAdapter(user -> {
            Intent intent = new Intent(requireContext(), ChatActivity.class);
            intent.putExtra("userId", user.getUid());
            intent.putExtra("userName", user.getFullName() != null ? user.getFullName() : user.getEmail());
            startActivity(intent);
        });
        
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        recyclerView.setAdapter(adapter);
        
        setupDrawer();
        swipeRefresh.setOnRefreshListener(this::refresh);
        
        showLoading(true);
        toggleEmpty(false);
        return v;
    }

    @Override
    public void onStart() {
        super.onStart();
        attachListener();
    }

    @Override
    public void onStop() {
        super.onStop();
        detachListener();
    }

    private void setupDrawer() {
        if (navigationView != null) {
            // Set listener cho drawer
            navigationView.setNavigationItemSelectedListener(item -> {
                // Đóng drawer
                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                }
                
                // Xử lý lọc theo role - lấy title từ menu item
                selectedRole = item.getTitle() != null ? item.getTitle().toString() : "";
                
                // Cập nhật checked state
                navigationView.setCheckedItem(item.getItemId());
                
                // Lọc lại danh sách
                filterUsersByRole();
                
                return true;
            });
        }
    }
    
    /**
     * Cập nhật menu drawer với tất cả các role có trong danh sách users
     */
    private void updateDrawerMenuWithRoles() {
        if (navigationView == null || allUsers.isEmpty()) return;
        
        // Lấy tất cả các role unique từ danh sách users
        java.util.Set<String> uniqueRoles = new java.util.HashSet<>();
        for (User user : allUsers) {
            String role = user.getRole();
            if (role == null || role.trim().isEmpty()) {
                role = "user";
            }
            uniqueRoles.add(role.toLowerCase(Locale.ROOT));
        }
        
        // Tạo menu động
        android.view.Menu menu = navigationView.getMenu();
        menu.clear();
        
        // Thêm "Tất cả" luôn có
        menu.add(android.view.Menu.NONE, R.id.filter_all, android.view.Menu.NONE, "Tất cả")
            .setCheckable(true)
            .setChecked("Tất cả".equals(selectedRole));
        
        // Sắp xếp roles để hiển thị theo thứ tự: user, admin, và các role khác
        java.util.List<String> sortedRoles = new java.util.ArrayList<>(uniqueRoles);
        java.util.Collections.sort(sortedRoles);
        
        // Thêm các role unique, viết hoa chữ cái đầu
        for (String role : sortedRoles) {
            String roleDisplay = role.substring(0, 1).toUpperCase() + role.substring(1);
            int itemId = android.view.View.generateViewId();
            menu.add(android.view.Menu.NONE, itemId, android.view.Menu.NONE, roleDisplay)
                .setCheckable(true)
                .setChecked(roleDisplay.equalsIgnoreCase(selectedRole));
        }
        
        // Set checked item mặc định nếu chưa có
        if ("Tất cả".equals(selectedRole)) {
            navigationView.setCheckedItem(R.id.filter_all);
        }
    }
    
    /**
     * Mở drawer từ bên ngoài (ví dụ từ toolbar)
     */
    public void openDrawer() {
        if (drawerLayout != null) {
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }
    
    /**
     * Đóng drawer
     */
    public void closeDrawer() {
        if (drawerLayout != null) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }
    
    /**
     * Kiểm tra drawer có đang mở không
     */
    public boolean isDrawerOpen() {
        return drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START);
    }
    
    private void filterUsersByRole() {
        List<User> filtered = new ArrayList<>();
        
        if ("Tất cả".equals(selectedRole)) {
            filtered.addAll(allUsers);
        } else {
            // So sánh không phân biệt hoa thường
            String selectedRoleLower = selectedRole.toLowerCase(Locale.ROOT);
            for (User u : allUsers) {
                String role = u.getRole();
                if (role == null || role.trim().isEmpty()) {
                    role = "user";
                }
                role = role.toLowerCase(Locale.ROOT);
                
                if (selectedRoleLower.equals(role)) {
                    filtered.add(u);
                }
            }
        }
        
        adapter.submit(filtered);
        toggleEmpty(filtered.isEmpty());
    }

    private void attachListener() {
        detachListener();
        showLoading(true);
        toggleEmpty(false);
        
        listener = usersRepository.listenAllUsers(new UsersRepository.OnUsersListener() {
            @Override
            public void onUsersReceived(@NonNull List<User> users) {
                if (!isAdded()) return;
                
                showLoading(false);
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                
                // Lưu tất cả users (không filter)
                allUsers.clear();
                allUsers.addAll(users);
                
                // Cập nhật menu drawer với tất cả các role
                updateDrawerMenuWithRoles();
                
                // Filter theo role được chọn
                filterUsersByRole();
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (!isAdded()) return;
                
                showLoading(false);
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                allUsers.clear();
                adapter.submit(new ArrayList<>());
                toggleEmpty(true);
                if (isAdded() && getContext() != null) {
                    Toast.makeText(getContext(), "Lỗi tải dữ liệu: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void detachListener() {
        if (listener != null) {
            listener.remove();
            listener = null;
        }
    }

    @Override
    public void refresh() {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);
        attachListener();
    }
    
    @Override
    public void scrollToTop() {
        if (recyclerView != null) {
            recyclerView.smoothScrollToPosition(0);
        }
    }

    private void toggleEmpty(boolean showEmpty) {
        if (loadingState != null) loadingState.setVisibility(View.GONE);
        if (emptyState != null) emptyState.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(showEmpty ? View.GONE : View.VISIBLE);
    }

    private void showLoading(boolean show) {
        if (loadingState != null) loadingState.setVisibility(show ? View.VISIBLE : View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        if (emptyState != null) emptyState.setVisibility(View.GONE);
    }

    // Adapter
    private static class ChatUsersAdapter extends RecyclerView.Adapter<ChatUserVH> {
        private final List<User> data = new ArrayList<>();
        private final OnUserClick onClick;

        interface OnUserClick {
            void onClick(@NonNull User user);
        }

        ChatUsersAdapter(OnUserClick onClick) {
            this.onClick = onClick;
            setHasStableIds(true);
        }

        @SuppressLint("NotifyDataSetChanged")
        void submit(List<User> newData) {
            data.clear();
            if (newData != null) data.addAll(newData);
            notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            if (position < 0 || position >= data.size()) return RecyclerView.NO_ID;
            String id = data.get(position).getUid();
            return (id == null ? "" : id).hashCode();
        }

        @NonNull
        @Override
        public ChatUserVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_user, parent, false);
            return new ChatUserVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ChatUserVH h, int pos) {
            if (pos < 0 || pos >= data.size()) return;
            h.bind(data.get(pos), onClick);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    private static class ChatUserVH extends RecyclerView.ViewHolder {
        private final TextView tvName, tvEmail;
        private final de.hdodenhof.circleimageview.CircleImageView imgAvatar;

        ChatUserVH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            imgAvatar = itemView.findViewById(R.id.imgAvatar);
        }

        void bind(@NonNull User user, ChatUsersAdapter.OnUserClick onClick) {
            String name = user.getFullName();
            if (TextUtils.isEmpty(name)) name = "(No name)";
            tvName.setText(name);
            
            String email = user.getEmail();
            tvEmail.setText(TextUtils.isEmpty(email) ? "(no email)" : email);
            
            // Load avatar
            String avatar = user.getAvatar();
            ImageLoader.load(imgAvatar, avatar, R.drawable.ic_profile_placeholder);
            
            itemView.setOnClickListener(v -> {
                if (onClick != null) onClick.onClick(user);
            });
        }
    }
}

