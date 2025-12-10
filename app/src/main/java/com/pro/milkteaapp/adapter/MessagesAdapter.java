package com.pro.milkteaapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.Message;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.VH> {

    public interface OnMessageClick { void onClick(@NonNull Message m); }

    private final List<Message> data = new ArrayList<>();
    private final List<Message> allData = new ArrayList<>();
    private final OnMessageClick onClick;
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    private String currentFilter = "Tất cả";

    public MessagesAdapter(OnMessageClick onClick) {
        this.onClick = onClick;
        setHasStableIds(true);
    }

    public void submit(List<Message> newData) {
        allData.clear();
        if (newData != null) allData.addAll(newData);
        applyFilter();
    }
    
    public void setFilter(String filter) {
        currentFilter = filter;
        applyFilter();
    }
    
    private void applyFilter() {
        data.clear();
        if ("Tất cả".equals(currentFilter)) {
            data.addAll(allData);
        } else if ("Hệ thống".equals(currentFilter)) {
            for (Message m : allData) {
                if (m.getType() != null && (m.getType().startsWith("order_") || "system".equals(m.getType()))) {
                    data.add(m);
                }
            }
        } else if ("Chat với Admin".equals(currentFilter)) {
            for (Message m : allData) {
                if (m.getType() != null && "chat".equals(m.getType())) {
                    data.add(m);
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override public long getItemId(int position) {
        String id = data.get(position).getId();
        return id == null ? position : id.hashCode();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int position) {
        Message m = data.get(position);
        h.tvTitle.setText(titleFromType(m.getType()));
        h.tvMessage.setText(m.getMessage() != null ? m.getMessage() : "");
        h.tvTime.setText(m.getCreatedAt() != null ? fmt.format(m.getCreatedAt().toDate()) : "");
        h.itemView.setAlpha(Boolean.TRUE.equals(m.getRead()) ? 0.6f : 1f);

        if (m.getRating() != null && m.getRating() > 0) {
            h.rbRated.setVisibility(View.VISIBLE);
            h.rbRated.setRating(m.getRating());
        } else {
            h.rbRated.setVisibility(View.GONE);
        }

        h.itemView.setOnClickListener(v -> { if (onClick != null) onClick.onClick(m); });
    }

    private String titleFromType(String type) {
        if ("order_done".equals(type)) return "Đơn hàng hoàn thành";
        if ("order_review".equals(type)) return "Đánh giá đơn hàng";
        return "Tin nhắn";
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvMessage, tvTime;
        RatingBar rbRated;
        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle   = itemView.findViewById(R.id.tvTitle);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime    = itemView.findViewById(R.id.tvTime);
            rbRated   = itemView.findViewById(R.id.rbRated);
        }
    }
}
