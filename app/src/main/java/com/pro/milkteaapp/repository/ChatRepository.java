package com.pro.milkteaapp.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.pro.milkteaapp.models.ChatMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository để quản lý các thao tác với Chat trong Firestore.
 * Cấu trúc: chats/{userId}/messages/{messageId}
 */
public class ChatRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface OnChatMessagesListener {
        void onMessagesReceived(@NonNull List<ChatMessage> messages);
        void onError(@NonNull Exception error);
    }

    public interface OnChatSendListener {
        void onSuccess();
        void onError(@NonNull Exception error);
    }

    /**
     * Lắng nghe realtime messages của một user
     */
    @NonNull
    public ListenerRegistration listenMessages(@NonNull String userId, @NonNull OnChatMessagesListener listener) {
        Query q = db.collection("chats")
                .document(userId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING);

        return q.addSnapshotListener((snap, err) -> {
            if (err != null) {
                listener.onError(err);
                return;
            }

            List<ChatMessage> messages = new ArrayList<>();
            if (snap != null) {
                for (DocumentSnapshot doc : snap.getDocuments()) {
                    ChatMessage msg = doc.toObject(ChatMessage.class);
                    if (msg != null) {
                        msg.setId(doc.getId());
                        messages.add(msg);
                    }
                }
            }
            listener.onMessagesReceived(messages);
        });
    }

    /**
     * Gửi tin nhắn từ admin đến user
     */
    public void sendMessage(@NonNull String userId, @NonNull String message, @NonNull OnChatSendListener listener) {
        Map<String, Object> data = new HashMap<>();
        data.put("senderId", "admin");
        data.put("senderName", "Admin");
        data.put("message", message);
        data.put("createdAt", Timestamp.now());
        data.put("read", false);

        db.collection("chats")
                .document(userId)
                .collection("messages")
                .add(data)
                .addOnSuccessListener(docRef -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    /**
     * Gửi tin nhắn từ user đến admin
     */
    public void sendMessageFromUser(@NonNull String userId, @NonNull String userName, @NonNull String message, @NonNull OnChatSendListener listener) {
        Map<String, Object> data = new HashMap<>();
        data.put("senderId", userId);
        data.put("senderName", userName);
        data.put("message", message);
        data.put("createdAt", Timestamp.now());
        data.put("read", false);

        db.collection("chats")
                .document(userId)
                .collection("messages")
                .add(data)
                .addOnSuccessListener(docRef -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    /**
     * Đánh dấu tất cả tin nhắn chưa đọc từ user là đã đọc (khi admin mở chat)
     */
    public void markAllUserMessagesAsRead(@NonNull String userId, @NonNull OnChatSendListener listener) {
        // Lấy tất cả tin nhắn chưa đọc
        db.collection("chats")
                .document(userId)
                .collection("messages")
                .whereEqualTo("read", false)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot == null || querySnapshot.isEmpty()) {
                        listener.onSuccess();
                        return;
                    }
                    
                    // Batch update tất cả tin nhắn chưa đọc từ user (không phải admin)
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    int count = 0;
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        ChatMessage msg = doc.toObject(ChatMessage.class);
                        if (msg != null) {
                            String senderId = msg.getSenderId();
                            // Chỉ đánh dấu tin nhắn từ user (không phải admin)
                            if (senderId != null && !"admin".equals(senderId)) {
                                batch.update(doc.getReference(), "read", true, "readAt", Timestamp.now());
                                count++;
                            }
                        }
                    }
                    
                    // Tạo biến final để sử dụng trong lambda
                    final int finalCount = count;
                    final String finalUserId = userId;
                    
                    if (count > 0) {
                        batch.commit()
                                .addOnSuccessListener(aVoid -> {
                                    android.util.Log.d("ChatRepository", "Marked " + finalCount + " messages as read for user: " + finalUserId);
                                    listener.onSuccess();
                                })
                                .addOnFailureListener(e -> {
                                    android.util.Log.e("ChatRepository", "Error marking messages as read: " + e.getMessage());
                                    listener.onError(e);
                                });
                    } else {
                        listener.onSuccess();
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("ChatRepository", "Error fetching unread messages: " + e.getMessage());
                    listener.onError(e);
                });
    }

    /**
     * Lấy tin nhắn mới nhất và số tin nhắn chưa đọc từ user
     */
    public void getLatestMessageAndUnreadCount(@NonNull String userId, @NonNull OnLatestMessageListener listener) {
        // Lấy tất cả tin nhắn để vừa lấy tin nhắn mới nhất vừa đếm số chưa đọc
        db.collection("chats")
                .document(userId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    ChatMessage latestMessage = null;
                    int unreadCount = 0;
                    
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        // Lấy tin nhắn mới nhất
                        DocumentSnapshot latestDoc = querySnapshot.getDocuments().get(0);
                        latestMessage = latestDoc.toObject(ChatMessage.class);
                        if (latestMessage != null) {
                            latestMessage.setId(latestDoc.getId());
                        }
                        
                        // Đếm số tin nhắn chưa đọc (chỉ tin nhắn từ user, không phải admin)
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            ChatMessage msg = doc.toObject(ChatMessage.class);
                            if (msg != null) {
                                Boolean read = msg.getRead();
                                String senderId = msg.getSenderId();
                                // Chỉ đếm tin nhắn chưa đọc từ user (không phải admin)
                                if ((read == null || !read) && senderId != null && !"admin".equals(senderId)) {
                                    unreadCount++;
                                }
                            }
                        }
                    }
                    
                    listener.onResult(latestMessage, unreadCount);
                })
                .addOnFailureListener(listener::onError);
    }

    public interface OnLatestMessageListener {
        void onResult(@Nullable ChatMessage latestMessage, int unreadCount);
        void onError(@NonNull Exception error);
    }
}

