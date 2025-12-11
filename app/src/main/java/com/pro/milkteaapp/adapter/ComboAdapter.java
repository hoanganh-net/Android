package com.pro.milkteaapp.adapter;

import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
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
import com.pro.milkteaapp.databinding.ItemComboBinding;
import com.pro.milkteaapp.models.Combo;
import com.pro.milkteaapp.models.Product;
import com.pro.milkteaapp.repository.ProductRepository;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ComboAdapter extends ListAdapter<Combo, ComboAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(Combo combo);
    }

    private static final String TAG = "ComboAdapter";
    private final OnItemClickListener listener;
    private static final long CLICK_DEBOUNCE_MS = 300L;
    private long lastClickTime = 0L;
    private final ProductRepository productsRepository = new ProductRepository();
    private final Map<String, String> productNameCache = new HashMap<>(); // Cache tên sản phẩm theo ID

    public ComboAdapter(@NonNull Context context, OnItemClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
        setHasStableIds(true);
    }

    private static final DiffUtil.ItemCallback<Combo> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Combo>() {
                @Override
                public boolean areItemsTheSame(@NonNull Combo oldItem, @NonNull Combo newItem) {
                    String oldId = oldItem.getId();
                    String newId = newItem.getId();
                    if (!TextUtils.isEmpty(oldId) && !TextUtils.isEmpty(newId)) {
                        return oldId.equals(newId);
                    }
                    String oldKey = safe(oldItem.getName()) + "_" + oldItem.getPrice();
                    String newKey = safe(newItem.getName()) + "_" + newItem.getPrice();
                    return oldKey.equals(newKey);
                }

                @Override
                public boolean areContentsTheSame(@NonNull Combo oldItem, @NonNull Combo newItem) {
                    return Objects.equals(oldItem.getName(), newItem.getName())
                            && Objects.equals(oldItem.getPrice(), newItem.getPrice())
                            && Objects.equals(oldItem.getImageUrl(), newItem.getImageUrl())
                            && Objects.equals(oldItem.getDescription(), newItem.getDescription())
                            && Objects.equals(oldItem.getStatus(), newItem.getStatus());
                }
            };

    @Override
    public long getItemId(int position) {
        Combo c = getItem(position);
        if (c == null) return RecyclerView.NO_ID;
        String key = !TextUtils.isEmpty(c.getId())
                ? c.getId()
                : (safe(c.getName()) + "_" + c.getPrice());
        return key.hashCode();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemComboBinding binding = ItemComboBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Combo combo = getItem(position);
        if (combo == null) return;

        // Tên
        String displayName = combo.getName();
        if (TextUtils.isEmpty(displayName)) {
            displayName = holder.itemView.getContext().getString(R.string.unknown_item);
        }
        holder.binding.comboName.setText(displayName);

        // Hiển thị tên sản phẩm
        loadAndDisplayProductNames(holder, combo);

        // Giá combo
        holder.binding.comboPrice.setText(MoneyUtils.formatVnd(combo.getPrice()));

        // Giá gốc và discount
        if (combo.getOriginalPrice() != null && combo.getOriginalPrice() > 0) {
            holder.binding.comboOriginalPrice.setVisibility(View.VISIBLE);
            holder.binding.comboOriginalPrice.setText(MoneyUtils.formatVnd(combo.getOriginalPrice()));
            holder.binding.comboOriginalPrice.setPaintFlags(
                    holder.binding.comboOriginalPrice.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            );
        } else {
            holder.binding.comboOriginalPrice.setVisibility(View.GONE);
        }

        // Hiển thị % giảm giá
        if (combo.getDiscountPercent() != null && combo.getDiscountPercent() > 0) {
            holder.binding.comboDiscount.setVisibility(View.VISIBLE);
            holder.binding.comboDiscount.setText("-" + combo.getDiscountPercent() + "%");
        } else {
            holder.binding.comboDiscount.setVisibility(View.GONE);
        }

        // A11y
        holder.binding.comboImage.setContentDescription(displayName);

        // Ảnh
        bindImage(holder, combo.getImageUrl(), R.drawable.milktea);

        // Click debounce
        holder.itemView.setOnClickListener(v -> {
            long now = SystemClock.elapsedRealtime();
            if (now - lastClickTime < CLICK_DEBOUNCE_MS) return;
            lastClickTime = now;
            if (listener != null) listener.onItemClick(combo);
        });
    }

    private void bindImage(@NonNull ViewHolder holder, String imageUrl, int fallbackRes) {
        // Sử dụng ImageLoader để xử lý URL và drawable
        com.pro.milkteaapp.utils.ImageLoader.load(holder.binding.comboImage, imageUrl, fallbackRes);
    }

    private void loadAndDisplayProductNames(@NonNull ViewHolder holder, @NonNull Combo combo) {
        List<String> productIds = combo.getProductIds();
        if (productIds == null || productIds.isEmpty()) {
            holder.binding.comboProductCount.setText(holder.itemView.getContext().getString(R.string.no_products_in_combo));
            return;
        }

        // Kiểm tra cache trước
        List<String> cachedNames = new ArrayList<>();
        List<String> missingIds = new ArrayList<>();
        
        for (String productId : productIds) {
            String cachedName = productNameCache.get(productId);
            if (cachedName != null) {
                cachedNames.add(cachedName);
            } else {
                missingIds.add(productId);
            }
        }

        // Nếu đã có đủ trong cache, hiển thị ngay
        if (missingIds.isEmpty() && cachedNames.size() == productIds.size()) {
            displayProductNames(holder, cachedNames, productIds.size());
            return;
        }

        // Hiển thị tạm thời với cache có sẵn
        if (!cachedNames.isEmpty()) {
            displayProductNames(holder, cachedNames, productIds.size());
        } else {
            // Hiển thị số lượng tạm thời
            holder.binding.comboProductCount.setText(productIds.size() + " sản phẩm");
        }

        // Load các sản phẩm còn thiếu
        if (!missingIds.isEmpty()) {
            loadMissingProduct(holder, combo.getId(), missingIds, cachedNames, productIds.size());
        }
    }

    private void loadMissingProduct(@NonNull ViewHolder holder, String comboId, 
                                    List<String> missingIds, List<String> existingNames, int totalCount) {
        final int[] loadedCount = {0};
        final int totalToLoad = missingIds.size();
        final List<String> loadedNames = new ArrayList<>(existingNames);

        for (String productId : missingIds) {
            productsRepository.fetchProductById(productId, new ProductRepository.OnSingleProductListener() {
                @Override
                public void onProductReceived(@NonNull Product product) {
                    String productName = product.getName();
                    if (!TextUtils.isEmpty(productName)) {
                        productNameCache.put(productId, productName);
                        loadedNames.add(productName);
                    }
                    loadedCount[0]++;
                    
                    // Kiểm tra xem ViewHolder này vẫn đang bind với combo này không
                    if (loadedCount[0] == totalToLoad) {
                        // Cập nhật UI nếu ViewHolder vẫn đang hiển thị combo này
                        if (holder.getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
                            Combo currentCombo = getItem(holder.getBindingAdapterPosition());
                            if (currentCombo != null && comboId.equals(currentCombo.getId())) {
                                displayProductNames(holder, loadedNames, totalCount);
                            }
                        }
                    }
                }

                @Override
                public void onError(@NonNull Exception error) {
                    loadedCount[0]++;
                    // Bỏ qua lỗi, tiếp tục với các sản phẩm khác
                    if (loadedCount[0] == totalToLoad && holder.getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
                        Combo currentCombo = getItem(holder.getBindingAdapterPosition());
                        if (currentCombo != null && comboId.equals(currentCombo.getId())) {
                            displayProductNames(holder, loadedNames, totalCount);
                        }
                    }
                }
            });
        }
    }

    private void displayProductNames(@NonNull ViewHolder holder, List<String> productNames, int totalCount) {
        if (productNames.isEmpty()) {
            holder.binding.comboProductCount.setText(totalCount + " sản phẩm");
            return;
        }

        StringBuilder displayText = new StringBuilder();
        
        // Hiển thị tối đa 2 tên sản phẩm đầu tiên
        int displayCount = Math.min(productNames.size(), 2);
        for (int i = 0; i < displayCount; i++) {
            if (i > 0) displayText.append(", ");
            displayText.append(productNames.get(i));
        }

        // Nếu còn sản phẩm khác, thêm "và X sản phẩm khác"
        if (productNames.size() < totalCount) {
            int remaining = totalCount - productNames.size();
            if (displayText.length() > 0) {
                displayText.append(" và ");
            }
            displayText.append(remaining).append(" sản phẩm khác");
        } else if (productNames.size() > displayCount) {
            int remaining = productNames.size() - displayCount;
            displayText.append(" và ").append(remaining).append(" sản phẩm khác");
        }

        holder.binding.comboProductCount.setText(displayText.toString());
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemComboBinding binding;

        ViewHolder(@NonNull ItemComboBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

