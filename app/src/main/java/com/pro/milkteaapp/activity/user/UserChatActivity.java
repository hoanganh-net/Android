package com.pro.milkteaapp.activity.user;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.adapter.ChatMessagesAdapter;
import com.pro.milkteaapp.models.ChatMessage;
import com.pro.milkteaapp.models.User;
import com.pro.milkteaapp.repository.ChatRepository;
import com.pro.milkteaapp.repository.UsersRepository;

import java.util.List;

public class UserChatActivity extends AppCompatActivity {
    

    private RecyclerView recyclerView;
    private EditText etMessage;

    private ChatRepository chatRepository;
    private ChatMessagesAdapter adapter;
    private ListenerRegistration listener;
    
    private String currentUserId;
    private String currentUserName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_activity_chat);
        
        chatRepository = new ChatRepository();
        UsersRepository usersRepository = new UsersRepository();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        recyclerView = findViewById(R.id.recyclerView);
        etMessage = findViewById(R.id.etMessage);
        ImageButton btnSend = findViewById(R.id.btnSend);
        
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        toolbar.setTitle("Chat với Admin");
        
        btnSend.setOnClickListener(v -> sendMessage());
        
        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });
        
        // Load user info first, then create adapter with actual userId
        currentUserId = getUnifiedUidOrNotify();
        if (currentUserId == null) {
            Toast.makeText(this, "Lỗi: Không lấy được thông tin người dùng", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Create adapter with actual userId
        adapter = new ChatMessagesAdapter(currentUserId);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        
        usersRepository.fetchUserById(currentUserId, new UsersRepository.OnUserListener() {
            @Override
            public void onUserReceived(@NonNull User user) {
                currentUserName = user.getFullName();
                if (TextUtils.isEmpty(currentUserName)) {
                    currentUserName = user.getEmail();
                }
                if (TextUtils.isEmpty(currentUserName)) {
                    currentUserName = "User";
                }
                startListening();
            }

            @Override
            public void onError(@NonNull Exception error) {
                currentUserName = "User";
                startListening();
            }
        });
    }

    private String getUnifiedUidOrNotify() {
        try {
            SessionManager sm = new SessionManager(this);
            String customId = sm.getUid();
            if (customId != null && !customId.trim().isEmpty()) {
                return customId.trim();
            }
        } catch (Throwable e) {
            android.util.Log.w("UserChatActivity", "Failed to get UID from SessionManager: " + e.getMessage());
        }
        
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return null;
        }
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    private void startListening() {
        if (currentUserId == null) return;
        
        listener = chatRepository.listenMessages(currentUserId, new ChatRepository.OnChatMessagesListener() {
            @Override
            public void onMessagesReceived(@NonNull List<ChatMessage> messages) {
                adapter.submit(messages);
                if (!messages.isEmpty()) {
                    recyclerView.post(() -> {
                        if (recyclerView != null) {
                            recyclerView.smoothScrollToPosition(messages.size() - 1);
                        }
                    });
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                Toast.makeText(UserChatActivity.this, "Lỗi: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendMessage() {
        if (currentUserId == null || TextUtils.isEmpty(currentUserName)) {
            Toast.makeText(this, "Đang tải thông tin...", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String message = etMessage.getText() != null ? etMessage.getText().toString().trim() : "";
        if (TextUtils.isEmpty(message)) {
            return;
        }
        
        etMessage.setText("");
        
        chatRepository.sendMessageFromUser(currentUserId, currentUserName, message, new ChatRepository.OnChatSendListener() {
            @Override
            public void onSuccess() {
                // Tin nhắn đã được gửi, listener sẽ tự động cập nhật
            }

            @Override
            public void onError(@NonNull Exception error) {
                Toast.makeText(UserChatActivity.this, "Lỗi gửi tin nhắn: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) {
            listener.remove();
            listener = null;
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

