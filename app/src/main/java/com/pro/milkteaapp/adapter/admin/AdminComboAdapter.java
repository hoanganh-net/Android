package com.pro.milkteaapp.adapter.admin;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.Combo;
import com.pro.milkteaapp.models.Product;
import com.pro.milkteaapp.repository.ProductRepository;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AdminComboAdapter extends RecyclerView.Adapter<AdminComboAdapter.ComboViewHolder> {

    public interface OnComboActionListener {
        void onEditCombo(Combo combo);
        void onDeleteCombo(Combo combo);
    }

    private final OnComboActionListener listener;
    private List<Combo> comboList;
    private final ProductRepository productsRepository = new ProductRepository();
    private final Map<String, String> productNameCache = new HashMap<>(); // Cache tên sản phẩm theo ID

    public AdminComboAdapter(@NonNull List<Combo> initialList,
                             @NonNull OnComboActionListener listener) {
        this.comboList = new ArrayList<>(initialList);
        this.listener = listener;
        setHasStableIds(true);
    }

    public void updateList(@NonNull List<Combo> newList) {
        final List<Combo> oldList = this.comboList;
        final List<Combo> newCopy = new ArrayList<>(newList);

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldList.size();
            }

            @Override
            public int getNewListSize() {
                return newCopy.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                String oldId = safeStr(oldList.get(oldItemPosition).getId());
                String newId = safeStr(newCopy.get(newItemPosition).getId());
                if (!TextUtils.isEmpty(oldId) && !TextUtils.isEmpty(newId)) return oldId.equals(newId);
                String ok = safeStr(oldList.get(oldItemPosition).getName()) + "_" + safeDouble(oldList.get(oldItemPosition).getPrice());
                String nk = safeStr(newCopy.get(newItemPosition).getName()) + "_" + safeDouble(newCopy.get(newItemPosition).getPrice());
                return ok.equals(nk);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Combo o = oldList.get(oldItemPosition);
                Combo n = newCopy.get(newItemPosition);
                return Objects.equals(safeStr(o.getName()), safeStr(n.getName())) &&
                        Objects.equals(safeStr(o.getImageUrl()), safeStr(n.getImageUrl())) &&
                        Objects.equals(o.getPrice(), n.getPrice());
            }
        });

        this.comboList = newCopy;
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ComboViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.admin_item_product, parent, false);
        return new ComboViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ComboViewHolder holder, int position) {
        Combo combo = comboList.get(position);
        if (combo == null) return;

        holder.tvName.setText(safeStr(combo.getName()));
        holder.tvPrice.setText(MoneyUtils.formatVnd(combo.getPrice()));

        // Hiển thị tên sản phẩm và status
        String status = safeStr(combo.getStatus());
        String statusText = status.equals("active") ? "Hoạt động" : "Tạm ngưng";
        loadAndDisplayProductNames(holder, combo, statusText);

        // Ảnh
        String imageUrl = combo.getImageUrl();
        if (!TextUtils.isEmpty(imageUrl)) {
            Glide.with(holder.itemView.getContext())
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(R.drawable.ic_placeholder)
                    .error(R.drawable.milktea)
                    .into(holder.imgProduct);
        } else {
            holder.imgProduct.setImageResource(R.drawable.milktea);
        }

        // Nút Edit
        View btnEdit = holder.itemView.findViewById(R.id.btnEdit);
        if (btnEdit != null) {
            btnEdit.setOnClickListener(v -> {
                if (listener != null) listener.onEditCombo(combo);
            });
        }

        // Nút Delete
        View btnDelete = holder.itemView.findViewById(R.id.btnDelete);
        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDeleteCombo(combo);
            });
        }

        // Ẩn icon featured vì combo không có tính năng này
        View iconFeatured = holder.itemView.findViewById(R.id.iconFeatured);
        if (iconFeatured != null) {
            iconFeatured.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return comboList.size();
    }

    @Override
    public long getItemId(int position) {
        Combo c = comboList.get(position);
        if (c == null) return RecyclerView.NO_ID;
        String key = !TextUtils.isEmpty(c.getId()) ? c.getId() : (safeStr(c.getName()) + "_" + safeDouble(c.getPrice()));
        return key.hashCode();
    }

    private static String safeStr(String s) {
        return s == null ? "" : s;
    }

    private static String safeDouble(Double d) {
        return d == null ? "0" : String.valueOf(d);
    }

    private void loadAndDisplayProductNames(@NonNull ComboViewHolder holder, @NonNull Combo combo, String statusText) {
        List<String> productIds = combo.getProductIds();
        if (productIds == null || productIds.isEmpty()) {
            holder.tvCategory.setText(holder.itemView.getContext().getString(R.string.no_products_in_combo) + " • " + statusText);
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
            displayProductNames(holder, cachedNames, productIds.size(), statusText);
            return;
        }

        // Hiển thị tạm thời với cache có sẵn hoặc số lượng
        if (!cachedNames.isEmpty()) {
            displayProductNames(holder, cachedNames, productIds.size(), statusText);
        } else {
            // Hiển thị số lượng tạm thời
            holder.tvCategory.setText(productIds.size() + " sản phẩm • " + statusText);
        }

        // Load các sản phẩm còn thiếu
        if (!missingIds.isEmpty()) {
            loadMissingProduct(holder, combo.getId(), missingIds, cachedNames, productIds.size(), statusText);
        }
    }

    private void loadMissingProduct(@NonNull ComboViewHolder holder, String comboId, 
                                    List<String> missingIds, List<String> existingNames, int totalCount, String statusText) {
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
                            int pos = holder.getBindingAdapterPosition();
                            if (pos >= 0 && pos < comboList.size()) {
                                Combo currentCombo = comboList.get(pos);
                                if (currentCombo != null && comboId.equals(currentCombo.getId())) {
                                    String currentStatus = safeStr(currentCombo.getStatus());
                                    String currentStatusText = currentStatus.equals("active") ? "Hoạt động" : "Tạm ngưng";
                                    displayProductNames(holder, loadedNames, totalCount, currentStatusText);
                                }
                            }
                        }
                    }
                }

                @Override
                public void onError(@NonNull Exception error) {
                    loadedCount[0]++;
                    // Bỏ qua lỗi, tiếp tục với các sản phẩm khác
                    if (loadedCount[0] == totalToLoad && holder.getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
                        int pos = holder.getBindingAdapterPosition();
                        if (pos >= 0 && pos < comboList.size()) {
                            Combo currentCombo = comboList.get(pos);
                            if (currentCombo != null && comboId.equals(currentCombo.getId())) {
                                String currentStatus = safeStr(currentCombo.getStatus());
                                String currentStatusText = currentStatus.equals("active") ? "Hoạt động" : "Tạm ngưng";
                                displayProductNames(holder, loadedNames, totalCount, currentStatusText);
                            }
                        }
                    }
                }
            });
        }
    }

    private void displayProductNames(@NonNull ComboViewHolder holder, List<String> productNames, int totalCount, String statusText) {
        StringBuilder displayText = new StringBuilder();
        
        if (productNames.isEmpty()) {
            displayText.append(totalCount).append(" sản phẩm");
        } else {
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
        }
        
        displayText.append(" • ").append(statusText);
        holder.tvCategory.setText(displayText.toString());
    }

    static class ComboViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName, tvPrice, tvCategory;
        final ImageView imgProduct;

        ComboViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.productName);
            tvPrice = itemView.findViewById(R.id.productPrice);
            tvCategory = itemView.findViewById(R.id.productCategory);
            imgProduct = itemView.findViewById(R.id.productImage);
        }
    }
}

