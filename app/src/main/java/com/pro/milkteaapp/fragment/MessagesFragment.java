package com.pro.milkteaapp.fragment;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.navigation.NavigationView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.constants.FirestoreConstants;
import com.pro.milkteaapp.activity.MainActivity;
import com.pro.milkteaapp.adapter.MessagesAdapter;
import com.pro.milkteaapp.databinding.FragmentMessagesBinding;
import com.pro.milkteaapp.fragment.bottomsheet.RateOrderBottomSheet;
import com.pro.milkteaapp.models.Message;

import java.util.ArrayList;
import java.util.List;

public class MessagesFragment extends Fragment {

    private FragmentMessagesBinding binding;
    private MessagesAdapter adapter;
    private ListenerRegistration registration;
    private String selectedFilter = "Tất cả";

    public MessagesFragment() { super(R.layout.user_fragment_messages); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding = FragmentMessagesBinding.bind(view);
        
        // Setup tin nhắn hệ thống
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new MessagesAdapter(this::onMessageClick);
        binding.recyclerView.setAdapter(adapter);

        binding.swipeRefresh.setOnRefreshListener(this::reloadOnce);
        
        // Setup nút chat với admin
        binding.btnChatWithAdmin.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(requireContext(), com.pro.milkteaapp.activity.user.UserChatActivity.class);
            startActivity(intent);
        });
        
        setupToolbar();
        setupDrawer();
        startListening();
    }
    
    private void setupToolbar() {
        if (getActivity() == null || binding == null) return;
        androidx.appcompat.app.AppCompatActivity activity = (androidx.appcompat.app.AppCompatActivity) getActivity();
        activity.setSupportActionBar(binding.toolbar);
        
        // Setup drawer button click
        View btnDrawer = binding.getRoot().findViewById(R.id.btnDrawer);
        if (btnDrawer != null) {
            btnDrawer.setOnClickListener(v -> {
                if (binding.drawerLayout != null) {
                    binding.drawerLayout.openDrawer(GravityCompat.END);
                }
            });
        }
    }
    
    private void setupDrawer() {
        if (binding == null) return;
        
        // Setup drawer menu click
        if (binding.navigationView != null) {
            // Set default selection là ALL (sau khi menu đã được inflate)
            binding.navigationView.post(() -> {
                if (binding.navigationView != null && isAdded()) {
                    binding.navigationView.setCheckedItem(R.id.menu_all);
                }
            });
            
            binding.navigationView.setNavigationItemSelectedListener(item -> {
                int itemId = item.getItemId();
                
                // Đóng drawer trước
                if (binding.drawerLayout != null) {
                    binding.drawerLayout.closeDrawer(GravityCompat.END);
                }
                
                if (itemId == R.id.menu_all) {
                    // Filter: Tất cả
                    selectedFilter = "Tất cả";
                    binding.navigationView.setCheckedItem(R.id.menu_all);
                    filterMessages();
                } else if (itemId == R.id.menu_system) {
                    // Filter: Hệ thống
                    selectedFilter = "Hệ thống";
                    binding.navigationView.setCheckedItem(R.id.menu_system);
                    filterMessages();
                } else if (itemId == R.id.menu_chat) {
                    // Mở màn hình chat với admin
                    android.content.Intent intent = new android.content.Intent(requireContext(), com.pro.milkteaapp.activity.user.UserChatActivity.class);
                    startActivity(intent);
                    // Không cập nhật checked state vì đây là action, không phải filter
                    // Reset về menu_all sau khi quay lại
                    binding.navigationView.post(() -> {
                        if (binding.navigationView != null && isAdded()) {
                            binding.navigationView.setCheckedItem(R.id.menu_all);
                        }
                    });
                }
                
                return true;
            });
        }
    }
    
    private void filterMessages() {
        // Filter logic sẽ được implement trong adapter hoặc khi load data
        if (adapter != null) {
            adapter.setFilter(selectedFilter);
        }
    }

    private void startListening() {
        showLoading(true);
        String uid = getUnifiedUidOrNotify();
        if (uid == null) return;

        Query q = FirebaseFirestore.getInstance()
                .collection(FirestoreConstants.COLLECTION_USERS).document(uid)
                .collection(FirestoreConstants.COLLECTION_INBOX)
                .orderBy("createdAt", Query.Direction.DESCENDING);

        if (registration != null) { registration.remove(); registration = null; }
        registration = q.addSnapshotListener((snap, err) -> {
            if (binding == null) return;
            binding.swipeRefresh.setRefreshing(false);
            showLoading(false);
            if (err != null) {
                if (isAdded() && getContext() != null) {
                    Toast.makeText(getContext(), err.getMessage(), Toast.LENGTH_LONG).show();
                }
                return;
            }

            List<Message> data = new ArrayList<>();
            if (snap != null) {
                for (DocumentSnapshot d : snap.getDocuments()) {
                    Message m = d.toObject(Message.class);
                    if (m == null) m = new Message();
                    m.setId(d.getId());
                    data.add(m);
                }
            }
            adapter.submit(data);
            toggleEmpty(data.isEmpty());
        });
    }

    private void reloadOnce() {
        String uid = getUnifiedUidOrNotify();
        if (uid == null) return;

        FirebaseFirestore.getInstance()
                .collection(FirestoreConstants.COLLECTION_USERS).document(uid)
                .collection(FirestoreConstants.COLLECTION_INBOX)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(t -> {
                    if (binding == null) return;
                    binding.swipeRefresh.setRefreshing(false);
                    if (!t.isSuccessful()) {
                        Exception e = t.getException();
                        if (e != null && isAdded() && getContext() != null) {
                            Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                        return;
                    }
                    List<Message> data = new ArrayList<>();
                    for (DocumentSnapshot d : t.getResult().getDocuments()) {
                        Message m = d.toObject(Message.class);
                        if (m == null) m = new Message();
                        m.setId(d.getId());
                        data.add(m);
                    }
                    adapter.submit(data);
                    toggleEmpty(data.isEmpty());
                });
    }

    private void onMessageClick(@NonNull Message m) {
        String uid = getUnifiedUidOrNotify();
        if (uid != null) {
            FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .collection("inbox").document(m.getId())
                    .update("read", true, "readAt", Timestamp.now());
        }
        
        // Xử lý theo loại message
        if ("chat".equals(m.getType())) {
            // Mở màn hình chat với admin
            android.content.Intent intent = new android.content.Intent(requireContext(), com.pro.milkteaapp.activity.user.UserChatActivity.class);
            startActivity(intent);
        } else if ("order_done".equals(m.getType()) && m.getOrderId() != null) {
            // Mở dialog đánh giá đơn hàng
            RateOrderBottomSheet.newInstance(m.getOrderId())
                    .show(getParentFragmentManager(), "rate_order");
        }
    }

    private void toggleEmpty(boolean empty) {
        if (binding == null) return;
        binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void showLoading(boolean show) {
        if (binding == null) return;
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            binding.recyclerView.setVisibility(View.GONE);
            binding.emptyView.setVisibility(View.GONE);
        }
    }

    /**
     * Lấy userId đồng nhất:
     * - Ưu tiên SessionManager.getUid() (USRxxxxx)
     * - Fallback FirebaseAuth UID nếu chưa có (để không vỡ luồng cũ)
     */
    @Nullable
    private String getUnifiedUidOrNotify() {
        // Ưu tiên custom userId (USRxxxxx)
        String customId = null;
        try {
            customId = new SessionManager(requireContext()).getUid();
        } catch (Throwable e) {
            android.util.Log.w("MessagesFragment", "Failed to get UID from SessionManager: " + e.getMessage());
        }

        if (customId != null && !customId.trim().isEmpty()) {
            return customId.trim();
        }

        // Fallback sang Firebase UID (không khuyến nghị, nhưng an toàn)
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            if (isAdded() && getContext() != null) {
                Toast.makeText(getContext(), getString(R.string.not_logged_in), Toast.LENGTH_LONG).show();
            }
            return null;
        }
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }


    @Override
    public void onDestroyView() {
        if (registration != null) { 
            registration.remove(); 
            registration = null; 
        }
        binding = null;
        super.onDestroyView();
    }
}
