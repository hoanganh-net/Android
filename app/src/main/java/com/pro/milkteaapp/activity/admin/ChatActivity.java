package com.pro.milkteaapp.activity.admin;

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
import com.google.firebase.firestore.ListenerRegistration;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.ChatMessagesAdapter;
import com.pro.milkteaapp.models.ChatMessage;
import com.pro.milkteaapp.repository.ChatRepository;

import java.util.List;

public class ChatActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private RecyclerView recyclerView;
    private EditText etMessage;
    private ImageButton btnSend;
    
    private ChatRepository chatRepository;
    private ChatMessagesAdapter adapter;
    private ListenerRegistration listener;
    
    private String userId;
    private String userName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_activity_chat);
        
        userId = getIntent().getStringExtra("userId");
        userName = getIntent().getStringExtra("userName");
        
        if (TextUtils.isEmpty(userId)) {
            Toast.makeText(this, "Lỗi: Không có thông tin người dùng", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        chatRepository = new ChatRepository();
        
        toolbar = findViewById(R.id.toolbar);
        recyclerView = findViewById(R.id.recyclerView);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            // Hiển thị nút back
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(userName != null ? userName : "CHAT");
        }
        toolbar.setTitleTextAppearance(this, R.style.ToolbarTitleStyle);
        toolbar.setTitleTextColor(getResources().getColor(R.color.white, getTheme()));
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        
        adapter = new ChatMessagesAdapter("admin");
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        
        btnSend.setOnClickListener(v -> sendMessage());
        
        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });
        
        startListening();
    }

    private void startListening() {
        listener = chatRepository.listenMessages(userId, new ChatRepository.OnChatMessagesListener() {
            @Override
            public void onMessagesReceived(@NonNull List<ChatMessage> messages) {
                if (adapter == null || recyclerView == null) return;
                adapter.submit(messages);
                if (!messages.isEmpty()) {
                    recyclerView.post(() -> {
                        if (recyclerView != null && adapter != null) {
                            recyclerView.smoothScrollToPosition(messages.size() - 1);
                        }
                    });
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                Toast.makeText(ChatActivity.this, "Lỗi: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendMessage() {
        String message = etMessage.getText() != null ? etMessage.getText().toString().trim() : "";
        if (TextUtils.isEmpty(message)) {
            return;
        }
        
        etMessage.setText("");
        
        chatRepository.sendMessage(userId, message, new ChatRepository.OnChatSendListener() {
            @Override
            public void onSuccess() {
                // Tin nhắn đã được gửi, listener sẽ tự động cập nhật
            }

            @Override
            public void onError(@NonNull Exception error) {
                Toast.makeText(ChatActivity.this, "Lỗi gửi tin nhắn: " + error.getMessage(), Toast.LENGTH_SHORT).show();
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

