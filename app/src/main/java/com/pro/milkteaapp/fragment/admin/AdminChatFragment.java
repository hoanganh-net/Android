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
import androidx.core.content.ContextCompat;
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
import com.pro.milkteaapp.models.ChatMessage;
import com.pro.milkteaapp.models.User;
import com.pro.milkteaapp.repository.ChatRepository;
import com.pro.milkteaapp.repository.UsersRepository;
import com.pro.milkteaapp.utils.ImageLoader;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminChatFragment extends Fragment
        implements AdminMainActivity.SupportsRefresh {

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private View loadingState, emptyState;
    private ChatUsersAdapter adapter;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    
    private UsersRepository usersRepository;
    private ChatRepository chatRepository;
    private ListenerRegistration listener;
    
    private final List<UserChatInfo> allUsers = new ArrayList<>();
    private String selectedRole = "Tất cả"; // Mặc định hiển thị tất cả
    
    // Wrapper class để lưu User + thông tin tin nhắn
    private static class UserChatInfo {
        final User user;
        ChatMessage latestMessage;
        int unreadCount;
        
        UserChatInfo(User user) {
            this.user = user;
            this.latestMessage = null;
            this.unreadCount = 0;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        usersRepository = new UsersRepository();
        chatRepository = new ChatRepository();
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
    public void onResume() {
        super.onResume();
        // Refresh lại thông tin tin nhắn khi quay lại từ ChatActivity
        if (!allUsers.isEmpty()) {
            loadChatInfoForUsers();
        }
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
        for (UserChatInfo info : allUsers) {
            String role = info.user.getRole();
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
        List<UserChatInfo> filtered = new ArrayList<>();
        
        if ("Tất cả".equals(selectedRole)) {
            filtered.addAll(allUsers);
        } else {
            // So sánh không phân biệt hoa thường
            String selectedRoleLower = selectedRole.toLowerCase(Locale.ROOT);
            for (UserChatInfo info : allUsers) {
                String role = info.user.getRole();
                if (role == null || role.trim().isEmpty()) {
                    role = "user";
                }
                role = role.toLowerCase(Locale.ROOT);
                
                if (selectedRoleLower.equals(role)) {
                    filtered.add(info);
                }
            }
        }
        
        // Sắp xếp: user có tin nhắn mới (chưa đọc) lên đầu
        filtered.sort((a, b) -> {
            // Ưu tiên: có tin nhắn chưa đọc > có tin nhắn mới nhất > không có tin nhắn
            if (a.unreadCount > 0 && b.unreadCount == 0) return -1;
            if (a.unreadCount == 0 && b.unreadCount > 0) return 1;
            if (a.unreadCount > 0 && b.unreadCount > 0) {
                // Cả hai đều có tin nhắn chưa đọc, so sánh theo thời gian tin nhắn mới nhất
                if (a.latestMessage != null && b.latestMessage != null) {
                    Date aTime = a.latestMessage.getCreatedAt() != null ? a.latestMessage.getCreatedAt().toDate() : null;
                    Date bTime = b.latestMessage.getCreatedAt() != null ? b.latestMessage.getCreatedAt().toDate() : null;
                    if (aTime != null && bTime != null) {
                        return bTime.compareTo(aTime); // Mới hơn lên đầu
                    }
                }
            }
            // So sánh theo thời gian tin nhắn mới nhất
            if (a.latestMessage != null && b.latestMessage != null) {
                Date aTime = a.latestMessage.getCreatedAt() != null ? a.latestMessage.getCreatedAt().toDate() : null;
                Date bTime = b.latestMessage.getCreatedAt() != null ? b.latestMessage.getCreatedAt().toDate() : null;
                if (aTime != null && bTime != null) {
                    return bTime.compareTo(aTime); // Mới hơn lên đầu
                }
                if (aTime != null) return -1;
                if (bTime != null) return 1;
            }
            return 0;
        });
        
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
                
                // Tạo UserChatInfo cho mỗi user
                allUsers.clear();
                for (User user : users) {
                    allUsers.add(new UserChatInfo(user));
                }
                
                // Load thông tin tin nhắn cho mỗi user
                loadChatInfoForUsers();
                
                // Cập nhật menu drawer với tất cả các role
                updateDrawerMenuWithRoles();
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

    private void loadChatInfoForUsers() {
        if (chatRepository == null || allUsers.isEmpty()) {
            filterUsersByRole();
            return;
        }
        
        final int[] loadedCount = {0};
        final int totalUsers = allUsers.size();

        loadedCount[0] = 0;

        for (UserChatInfo info : allUsers) {
            String userId = info.user.getUid();
            if (userId == null || userId.isEmpty()) {
                loadedCount[0]++;
                if (loadedCount[0] == totalUsers) {
                    filterUsersByRole();
                }
                continue;
            }
            
            chatRepository.getLatestMessageAndUnreadCount(userId, new ChatRepository.OnLatestMessageListener() {
                @Override
                public void onResult(@Nullable ChatMessage latestMessage, int unreadCount) {
                    info.latestMessage = latestMessage;
                    info.unreadCount = unreadCount;
                    loadedCount[0]++;
                    
                    if (loadedCount[0] == totalUsers) {
                        filterUsersByRole();
                    }
                }

                @Override
                public void onError(@NonNull Exception error) {
                    // Nếu lỗi, vẫn cập nhật với giá trị null/0
                    info.latestMessage = null;
                    info.unreadCount = 0;
                    loadedCount[0]++;
                    if (loadedCount[0] == totalUsers) {
                        filterUsersByRole();
                    }
                }
            });
        }
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
        private final List<UserChatInfo> data = new ArrayList<>();
        private final OnUserClick onClick;
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM", Locale.getDefault());

        interface OnUserClick {
            void onClick(@NonNull User user);
        }

        ChatUsersAdapter(OnUserClick onClick) {
            this.onClick = onClick;
            setHasStableIds(true);
        }

        @SuppressLint("NotifyDataSetChanged")
        void submit(List<UserChatInfo> newData) {
            data.clear();
            if (newData != null) data.addAll(newData);
            notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            if (position < 0 || position >= data.size()) return RecyclerView.NO_ID;
            String id = data.get(position).user.getUid();
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
            h.bind(data.get(pos), onClick, timeFormat, dateFormat);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    private static class ChatUserVH extends RecyclerView.ViewHolder {
        private final TextView tvName, tvEmail, tvLastMessage, tvUnreadBadge;
        private final de.hdodenhof.circleimageview.CircleImageView imgAvatar;

        ChatUserVH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvUnreadBadge = itemView.findViewById(R.id.tvUnreadBadge);
            imgAvatar = itemView.findViewById(R.id.imgAvatar);
        }

        @SuppressLint("SetTextI18n")
        void bind(@NonNull UserChatInfo info, ChatUsersAdapter.OnUserClick onClick,
                  SimpleDateFormat timeFormat, SimpleDateFormat dateFormat) {
            User user = info.user;
            
            String name = user.getFullName();
            if (TextUtils.isEmpty(name)) name = "(No name)";
            tvName.setText(name);
            
            // Ẩn email
            tvEmail.setVisibility(View.GONE);
            
            // Hiển thị tin nhắn mới nhất
            if (info.latestMessage != null && info.latestMessage.getMessage() != null) {
                tvLastMessage.setVisibility(View.VISIBLE);
                String message = info.latestMessage.getMessage();
                // Chỉ hiển thị tin nhắn từ user (không phải admin)
                if (!"admin".equals(info.latestMessage.getSenderId())) {
                    tvLastMessage.setText(message);
                    tvLastMessage.setTextColor(ContextCompat.getColor(itemView.getContext(), android.R.color.black));
                    tvLastMessage.setTypeface(null, android.graphics.Typeface.BOLD);
                } else {
                    tvLastMessage.setText("Bạn: " + message);
                    tvLastMessage.setTextColor(ContextCompat.getColor(itemView.getContext(), android.R.color.darker_gray));
                    tvLastMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
                }
            } else {
                tvLastMessage.setVisibility(View.GONE);
            }
            
            // Hiển thị badge số tin nhắn chưa đọc
            if (info.unreadCount > 0) {
                tvUnreadBadge.setVisibility(View.VISIBLE);
                tvUnreadBadge.setText(String.valueOf(info.unreadCount));
                // Làm nổi bật item có tin nhắn mới
                itemView.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.primary_light));
                tvName.setTextColor(ContextCompat.getColor(itemView.getContext(), android.R.color.black));
                tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                tvUnreadBadge.setVisibility(View.GONE);
                itemView.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), android.R.color.white));
                tvName.setTextColor(ContextCompat.getColor(itemView.getContext(), android.R.color.black));
                tvName.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
            
            // Load avatar
            String avatar = user.getAvatar();
            ImageLoader.load(imgAvatar, avatar, R.drawable.ic_profile_placeholder);
            
            itemView.setOnClickListener(v -> {
                if (onClick != null) onClick.onClick(user);
            });
        }
    }
}

