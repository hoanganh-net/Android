package com.pro.milkteaapp.adapter.admin;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pro.milkteaapp.R;
import com.pro.milkteaapp.databinding.AdminItemToppingRowBinding;
import com.pro.milkteaapp.models.Topping;
import com.pro.milkteaapp.utils.ImageLoader;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.util.List;

public class AdminToppingAdapter extends RecyclerView.Adapter<AdminToppingAdapter.VH> {

    public interface Listener {
        void onEdit(@NonNull Topping t);
        void onDelete(@NonNull Topping t);
    }

    private final List<Topping> data;
    private final Listener listener;

    public AdminToppingAdapter(List<Topping> data, Listener listener) {
        this.data = data;
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override public long getItemId(int position) {
        Topping t = data.get(position);
        String key = (t.getId() == null ? t.getName() : t.getId());
        return (key == null ? "" : key).hashCode();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(AdminItemToppingRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Topping t = data.get(position);
        h.b.tvName.setText(t.getName());
        h.b.tvPrice.setText(MoneyUtils.formatVnd((double) t.getPrice()));
        String cats = (t.getCategories() == null || t.getCategories().isEmpty()) ? "—"
                : TextUtils.join(", ", t.getCategories());
        h.b.tvCategories.setText(cats);

        // Load ảnh topping
        String imageUrl = t.getImageUrl();
        if (TextUtils.isEmpty(imageUrl)) {
            h.b.toppingImage.setImageResource(R.drawable.milktea);
        } else {
            ImageLoader.load(
                    h.b.toppingImage,
                    imageUrl,
                    R.drawable.milktea
            );
        }

        // Click vào item để xem chi tiết và chỉnh sửa
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(t);
        });
        
        // Nút Delete
        h.b.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(t);
        });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final AdminItemToppingRowBinding b;
        VH(AdminItemToppingRowBinding b) { super(b.getRoot()); this.b = b; }
    }
}
