package com.pro.milkteaapp.models;

import com.google.firebase.Timestamp;

public class ChatMessage {
    private String id;
    private String senderId;      // userId hoặc "admin"
    private String senderName;    // Tên người gửi
    private String message;       // Nội dung tin nhắn
    private Timestamp createdAt;
    private Boolean read;

    // No-args constructor required by Firestore
    public ChatMessage() {
    }

    public ChatMessage(String senderId, String senderName, Boolean read) {
        this.senderId = senderId;
        this.senderName = senderName;
        this.read = read;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSenderId() { return senderId; }

    public String getSenderName() { return senderName; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Boolean getRead() { return read; }
}

