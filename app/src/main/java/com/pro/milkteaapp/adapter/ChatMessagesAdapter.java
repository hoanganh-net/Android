package com.pro.milkteaapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChatMessagesAdapter extends RecyclerView.Adapter<ChatMessagesAdapter.VH> {

    private final List<ChatMessage> data = new ArrayList<>();
    private final String currentUserId;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public ChatMessagesAdapter(String currentUserId) {
        this.currentUserId = currentUserId;
        setHasStableIds(true);
    }

    public void submit(List<ChatMessage> newData) {
        data.clear();
        if (newData != null) data.addAll(newData);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= data.size()) return RecyclerView.NO_ID;
        String id = data.get(position).getId();
        return (id == null ? "" : id).hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        if (position < 0 || position >= data.size()) return 0;
        ChatMessage msg = data.get(position);
        // Nếu currentUserId là "admin" và senderId là "admin" -> sent
        // Nếu currentUserId là "user" và senderId không phải "admin" -> sent
        boolean isMe = false;
        if ("admin".equals(currentUserId)) {
            isMe = "admin".equals(msg.getSenderId());
        } else {
            // User gửi: senderId là userId của user
            isMe = currentUserId.equals(msg.getSenderId());
        }
        return isMe ? 1 : 0; // 1 = sent, 0 = received
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = viewType == 1 ? R.layout.item_chat_message_sent : R.layout.item_chat_message_received;
        View v = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        if (position < 0 || position >= data.size()) return;
        ChatMessage msg = data.get(position);
        if (msg == null) return;
        
        h.tvMessage.setText(msg.getMessage() != null ? msg.getMessage() : "");
        h.tvTime.setText(msg.getCreatedAt() != null ? timeFormat.format(msg.getCreatedAt().toDate()) : "");
        
        // Luôn hiển thị tên người gửi
        if (h.tvSender != null) {
            String senderName = msg.getSenderName() != null ? msg.getSenderName() : "";
            // Tính toán isSent trực tiếp từ message thay vì gọi getItemViewType lại
            boolean isSent = false;
            if ("admin".equals(currentUserId)) {
                isSent = "admin".equals(msg.getSenderId());
            } else {
                isSent = currentUserId != null && currentUserId.equals(msg.getSenderId());
            }
            
            if (isSent) {
                // Tin nhắn đã gửi: hiển thị "Bạn"
                h.tvSender.setText("Bạn");
                h.tvSender.setVisibility(View.VISIBLE);
            } else {
                // Tin nhắn đã nhận: hiển thị tên người gửi
                if (senderName.isEmpty()) {
                    if ("admin".equals(msg.getSenderId())) {
                        h.tvSender.setText("Admin");
                    } else {
                        h.tvSender.setText("Người dùng");
                    }
                } else {
                    h.tvSender.setText(senderName);
                }
                h.tvSender.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime, tvSender;

        VH(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvSender = itemView.findViewById(R.id.tvSender);
        }
    }
}

