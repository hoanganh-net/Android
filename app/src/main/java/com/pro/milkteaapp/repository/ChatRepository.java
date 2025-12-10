package com.pro.milkteaapp.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.pro.milkteaapp.constants.FirestoreConstants;
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

    public interface OnChatMessageListener {
        void onMessageReceived(@Nullable ChatMessage message);
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
     * Đánh dấu tin nhắn đã đọc
     */
    public void markAsRead(@NonNull String userId, @NonNull String messageId) {
        db.collection("chats")
                .document(userId)
                .collection("messages")
                .document(messageId)
                .update("read", true, "readAt", Timestamp.now());
    }

    /**
     * Lấy danh sách users có tin nhắn (cho admin xem danh sách chat)
     * Lấy từ collection users và kiểm tra xem có collection chats/{userId}/messages không
     */
    public void fetchUsersWithChats(@NonNull OnUsersListener listener) {
        // Lấy tất cả users, sau đó filter những user có tin nhắn
        db.collection(FirestoreConstants.COLLECTION_USERS)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<String> userIds = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String uid = doc.getString(FirestoreConstants.FIELD_UID);
                        if (uid != null) {
                            userIds.add(uid);
                        }
                    }
                    listener.onUsersReceived(userIds);
                })
                .addOnFailureListener(listener::onError);
    }

    public interface OnUsersListener {
        void onUsersReceived(@NonNull List<String> userIds);
        void onError(@NonNull Exception error);
    }
}

