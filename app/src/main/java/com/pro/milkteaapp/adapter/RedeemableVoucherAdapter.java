package com.pro.milkteaapp.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.Voucher;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter để hiển thị danh sách voucher có thể đổi bằng reward points
 */
public class RedeemableVoucherAdapter extends RecyclerView.Adapter<RedeemableVoucherAdapter.VH> {

    public interface OnRedeemListener {
        void onRedeem(@NonNull Voucher voucher, long pointsRequired);
    }

    private final List<Voucher> vouchers = new ArrayList<>();
    private final OnRedeemListener listener;
    private long currentRewardPoints = 0L;
    private final SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    public RedeemableVoucherAdapter(@NonNull List<Voucher> vouchers, long currentRewardPoints, 
                                   @NonNull OnRedeemListener listener) {
        this.vouchers.addAll(vouchers);
        this.currentRewardPoints = currentRewardPoints;
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateRewardPoints(long points) {
        this.currentRewardPoints = points;
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void submitList(@NonNull List<Voucher> newList) {
        vouchers.clear();
        vouchers.addAll(newList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_redeemable_voucher, parent, false);
        return new VH(v);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Voucher voucher = vouchers.get(position);
        if (voucher == null) return;

        // Mã voucher
        holder.tvCode.setText(voucher.getCode() != null ? voucher.getCode() : "");

        // Ưu đãi
        holder.tvBenefit.setText(buildBenefit(voucher));

        // Điểm cần để đổi
        Long pointsRequired = voucher.getPointsRequired();
        if (pointsRequired != null && pointsRequired > 0) {
            holder.tvPointsRequired.setText(pointsRequired + " điểm");
            holder.tvPointsRequired.setVisibility(View.VISIBLE);
        } else {
            holder.tvPointsRequired.setVisibility(View.GONE);
        }

        // Hạn sử dụng
        if (voucher.getEndAt() != null) {
            holder.tvExpiry.setText("HSD: " + df.format(voucher.getEndAt().toDate()));
            holder.tvExpiry.setVisibility(View.VISIBLE);
        } else {
            holder.tvExpiry.setVisibility(View.GONE);
        }

        // Điều kiện đơn tối thiểu
        if (voucher.getMinOrder() > 0) {
            holder.tvMinOrder.setText("Đơn tối thiểu: " + MoneyUtils.formatVnd((double) voucher.getMinOrder()));
            holder.tvMinOrder.setVisibility(View.VISIBLE);
        } else {
            holder.tvMinOrder.setVisibility(View.GONE);
        }

        // Nút đổi
        boolean canRedeem = pointsRequired != null && pointsRequired > 0 && currentRewardPoints >= pointsRequired;
        holder.btnRedeem.setEnabled(canRedeem);
        holder.btnRedeem.setText(canRedeem ? "Đổi ngay" : "Không đủ điểm");
        holder.btnRedeem.setOnClickListener(v -> {
            if (canRedeem && pointsRequired != null) {
                listener.onRedeem(voucher, pointsRequired);
            }
        });

        // Mô tả
        if (voucher.getDescription() != null && !voucher.getDescription().isEmpty()) {
            holder.tvDescription.setText(voucher.getDescription());
            holder.tvDescription.setVisibility(View.VISIBLE);
        } else {
            holder.tvDescription.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return vouchers.size();
    }

    @NonNull
    private String buildBenefit(@NonNull Voucher v) {
        String type = v.getType();
        if (type == null) return "";

        switch (type) {
            case "ORDER_FIXED":
                return "Giảm " + MoneyUtils.formatVnd((double) v.getAmount());
            case "ORDER_PERCENT":
                String percent = v.getPercent() + "%";
                if (v.getMaxDiscount() != null && v.getMaxDiscount() > 0) {
                    percent += " (tối đa " + MoneyUtils.formatVnd(v.getMaxDiscount().doubleValue()) + ")";
                }
                return "Giảm " + percent;
            case "SHIPPING_FIXED":
                return "Miễn phí vận chuyển";
            default:
                return "";
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        MaterialCardView card;
        TextView tvCode, tvBenefit, tvPointsRequired, tvExpiry, tvMinOrder, tvDescription;
        MaterialButton btnRedeem;

        VH(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            tvCode = itemView.findViewById(R.id.tvCode);
            tvBenefit = itemView.findViewById(R.id.tvBenefit);
            tvPointsRequired = itemView.findViewById(R.id.tvPointsRequired);
            tvExpiry = itemView.findViewById(R.id.tvExpiry);
            tvMinOrder = itemView.findViewById(R.id.tvMinOrder);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            btnRedeem = itemView.findViewById(R.id.btnRedeem);
        }
    }
}

