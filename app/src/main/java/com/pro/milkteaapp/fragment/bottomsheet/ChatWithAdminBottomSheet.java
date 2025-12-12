package com.pro.milkteaapp.fragment.bottomsheet;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.firestore.ListenerRegistration;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.ChatMessagesAdapter;
import com.pro.milkteaapp.models.ChatMessage;
import com.pro.milkteaapp.models.User;
import com.pro.milkteaapp.repository.ChatRepository;
import com.pro.milkteaapp.repository.UsersRepository;

import java.util.List;

public class ChatWithAdminBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "ChatWithAdminBottomSheet";

    private RecyclerView chatRecyclerView;
    private EditText etChatMessage;

    private ChatRepository chatRepository;
    private ChatMessagesAdapter chatAdapter;
    private ListenerRegistration chatRegistration;
    
    private String currentUserId;
    private String currentUserName;

    public static ChatWithAdminBottomSheet newInstance() {
        return new ChatWithAdminBottomSheet();
    }

    @Override
    public int getTheme() {
        return R.style.AppBottomSheetDialogTheme;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.admin_bottomsheet_chat, container, false);
        
        chatRecyclerView = v.findViewById(R.id.chatRecyclerView);
        etChatMessage = v.findViewById(R.id.etChatMessage);
        ImageButton btnSendChat = v.findViewById(R.id.btnSendChat);
        
        chatRepository = new ChatRepository();
        UsersRepository usersRepository = new UsersRepository();
        
        // Load user info first, then create adapter with actual userId
        currentUserId = getUnifiedUidOrNotify();
        if (currentUserId == null) {
            Toast.makeText(requireContext(), "Lỗi: Không lấy được thông tin người dùng", Toast.LENGTH_SHORT).show();
            dismiss();
            return v;
        }
        
        // Create adapter with actual userId
        chatAdapter = new ChatMessagesAdapter(currentUserId);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        chatRecyclerView.setAdapter(chatAdapter);
        
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
                startChatListening();
            }

            @Override
            public void onError(@NonNull Exception error) {
                currentUserName = "User";
                startChatListening();
            }
        });
        
        btnSendChat.setOnClickListener(v1 -> sendChatMessage());
        etChatMessage.setOnEditorActionListener((v1, actionId, event) -> {
            sendChatMessage();
            return true;
        });
        
        return v;
    }

    private void startChatListening() {
        if (currentUserId == null) return;
        
        if (chatRegistration != null) {
            chatRegistration.remove();
            chatRegistration = null;
        }
        
        chatRegistration = chatRepository.listenMessages(currentUserId, new ChatRepository.OnChatMessagesListener() {
            @Override
            public void onMessagesReceived(@NonNull List<ChatMessage> messages) {
                if (chatRecyclerView == null || chatAdapter == null) return;
                chatAdapter.submit(messages);
                if (!messages.isEmpty()) {
                    chatRecyclerView.post(() -> {
                        if (chatRecyclerView != null && chatAdapter != null) {
                            chatRecyclerView.smoothScrollToPosition(messages.size() - 1);
                        }
                    });
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (getContext() == null) return;
                Toast.makeText(getContext(), "Lỗi tải chat: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void sendChatMessage() {
        if (currentUserId == null || TextUtils.isEmpty(currentUserName)) {
            Toast.makeText(requireContext(), "Đang tải thông tin...", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String message = etChatMessage.getText() != null 
            ? etChatMessage.getText().toString().trim() 
            : "";
        if (TextUtils.isEmpty(message)) {
            return;
        }
        
        etChatMessage.setText("");
        
        chatRepository.sendMessageFromUser(currentUserId, currentUserName, message, new ChatRepository.OnChatSendListener() {
            @Override
            public void onSuccess() {
                // Tin nhắn đã được gửi, listener sẽ tự động cập nhật
            }

            @Override
            public void onError(@NonNull Exception error) {
                Toast.makeText(requireContext(), "Lỗi gửi tin nhắn: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Nullable
    private String getUnifiedUidOrNotify() {
        try {
            com.pro.milkteaapp.SessionManager sm = new com.pro.milkteaapp.SessionManager(requireContext());
            String customId = sm.getUid();
            if (customId != null && !customId.trim().isEmpty()) {
                return customId.trim();
            }
        } catch (Throwable e) {
            android.util.Log.w("ChatWithAdmin", "Failed to get UID from SessionManager: " + e.getMessage());
        }
        
        if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null) {
            return null;
        }
        return com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (chatRegistration != null) {
            chatRegistration.remove();
            chatRegistration = null;
        }
    }
}

