package com.pro.milkteaapp.models;

import com.google.firebase.Timestamp;

public class ChatMessage {
    private String id;
    private String senderId;      // userId hoặc "admin"
    private String senderName;    // Tên người gửi
    private String message;       // Nội dung tin nhắn
    private Timestamp createdAt;
    private Boolean read;

    public ChatMessage() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Boolean getRead() { return read; }
    public void setRead(Boolean read) { this.read = read; }
}

