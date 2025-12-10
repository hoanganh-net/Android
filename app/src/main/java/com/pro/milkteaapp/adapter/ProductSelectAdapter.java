package com.pro.milkteaapp.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.databinding.ItemProductSelectBinding;
import com.pro.milkteaapp.models.Products;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * Adapter để hiển thị sản phẩm với checkbox để chọn nhiều
 */
public class ProductSelectAdapter extends ListAdapter<Products, ProductSelectAdapter.ViewHolder> {

    private final Set<String> selectedProductIds = new HashSet<>();
    private OnProductToggleListener toggleListener;

    public interface OnProductToggleListener {
        void onProductToggled(Products product, boolean isSelected);
    }

    public ProductSelectAdapter(@NonNull Context context) {
        super(DIFF_CALLBACK);
    }

    public void setToggleListener(OnProductToggleListener listener) {
        this.toggleListener = listener;
    }

    public void setSelectedProductIds(@NonNull Set<String> productIds) {
        selectedProductIds.clear();
        selectedProductIds.addAll(productIds);
        notifyDataSetChanged();
    }

    public Set<String> getSelectedProductIds() {
        return new HashSet<>(selectedProductIds);
    }

    private static final DiffUtil.ItemCallback<Products> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Products>() {
                @Override
                public boolean areItemsTheSame(@NonNull Products oldItem, @NonNull Products newItem) {
                    return TextUtils.equals(oldItem.getId(), newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Products oldItem, @NonNull Products newItem) {
                    return TextUtils.equals(oldItem.getName(), newItem.getName())
                            && oldItem.getPrice() == newItem.getPrice();
                }
            };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemProductSelectBinding binding = ItemProductSelectBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Products product = getItem(position);
        if (product == null) return;

        String productId = product.getId();
        boolean isSelected = selectedProductIds.contains(productId);

        // Tên
        String displayName = product.getName();
        if (TextUtils.isEmpty(displayName)) {
            displayName = holder.itemView.getContext().getString(R.string.unknown_item);
        }
        holder.binding.productName.setText(displayName);

        // Giá
        holder.binding.productPrice.setText(MoneyUtils.formatVnd(product.getPrice()));

        // Checkbox
        holder.binding.checkbox.setChecked(isSelected);

        // Ảnh
        String imageUrl = product.getImageUrl();
        if (!TextUtils.isEmpty(imageUrl)) {
            Glide.with(holder.itemView.getContext())
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(R.drawable.ic_placeholder)
                    .error(R.drawable.milktea)
                    .into(holder.binding.productImage);
        } else {
            holder.binding.productImage.setImageResource(R.drawable.milktea);
        }

        // Click to toggle
        holder.itemView.setOnClickListener(v -> {
            boolean newState = !isSelected;
            if (newState) {
                selectedProductIds.add(productId);
            } else {
                selectedProductIds.remove(productId);
            }
            holder.binding.checkbox.setChecked(newState);
            if (toggleListener != null) {
                toggleListener.onProductToggled(product, newState);
            }
        });

        holder.binding.checkbox.setOnClickListener(v -> {
            boolean newState = holder.binding.checkbox.isChecked();
            if (newState) {
                selectedProductIds.add(productId);
            } else {
                selectedProductIds.remove(productId);
            }
            if (toggleListener != null) {
                toggleListener.onProductToggled(product, newState);
            }
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemProductSelectBinding binding;

        ViewHolder(@NonNull ItemProductSelectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

