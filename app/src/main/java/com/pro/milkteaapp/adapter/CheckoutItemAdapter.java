package com.pro.milkteaapp.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pro.milkteaapp.R;
import com.pro.milkteaapp.databinding.ItemCheckoutRowBinding;
import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.models.ComboCartItem;
import com.pro.milkteaapp.models.Product;
import com.pro.milkteaapp.models.SelectedTopping;
import com.pro.milkteaapp.utils.ImageLoader;
import com.pro.milkteaapp.utils.ImageUtils;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.util.List;
import java.util.Locale;

public class CheckoutItemAdapter extends RecyclerView.Adapter<CheckoutItemAdapter.VH> {

    private static final int TYPE_PRODUCT = 0;
    private static final int TYPE_COMBO = 1;

    private final Context ctx;
    private final List<CartItem> items;
    private final List<ComboCartItem> comboItems;
    private final int totalCount;

    public CheckoutItemAdapter(Context ctx, List<CartItem> items, List<ComboCartItem> comboItems) {
        this.ctx = ctx;
        this.items = items != null ? items : new java.util.ArrayList<>();
        this.comboItems = comboItems != null ? comboItems : new java.util.ArrayList<>();
        this.totalCount = this.items.size() + this.comboItems.size();
        setHasStableIds(true);
    }

    @Override
    public int getItemViewType(int position) {
        if (position < items.size()) {
            return TYPE_PRODUCT;
        }
        return TYPE_COMBO;
    }

    // ID ổn định
    @Override
    public long getItemId(int position) {
        if (position < items.size()) {
            CartItem ci = items.get(position);
            StringBuilder key = new StringBuilder();
            key.append(ci.getProductId()).append("|")
                    .append(ci.getSize()).append("|")
                    .append(ci.getSugar()).append("|")
                    .append(ci.getIce()).append("|")
                    .append(ci.getNote()).append("|");
            List<SelectedTopping> ts = ci.getToppings();
            if (ts != null) for (SelectedTopping t : ts) key.append(t.id).append(",");
            return key.toString().replace("null","_").hashCode();
        } else {
            int comboPos = position - items.size();
            ComboCartItem combo = comboItems.get(comboPos);
            return (combo.getComboId() != null ? combo.getComboId() : "combo_" + comboPos).hashCode();
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemCheckoutRowBinding.inflate(LayoutInflater.from(ctx), parent, false));
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        if (position < items.size()) {
            // Product item
            bindProductItem(h, items.get(position));
        } else {
            // Combo item
            int comboPos = position - items.size();
            bindComboItem(h, comboItems.get(comboPos));
        }
    }

    private void bindProductItem(@NonNull VH h, CartItem ci) {
        Product p = ci.getMilkTea();

        // Tên
        h.b.tvName.setText(p.getName());

        // Hàng 1: Size • Đường • Đá
        String size = ci.getSize();
        String sugar = ci.getSugar();
        String ice = ci.getIce();

        StringBuilder line1 = new StringBuilder();
        if (!TextUtils.isEmpty(size)) {
            line1.append(String.format(Locale.getDefault(), "Size %s", size));
        }
        if (!TextUtils.isEmpty(sugar)) {
            if (line1.length() > 0) line1.append(" • ");
            line1.append(sugar);
        }
        if (!TextUtils.isEmpty(ice)) {
            if (line1.length() > 0) line1.append(" • ");
            line1.append(ice);
        }
        h.b.tvLine1.setText(line1.toString());
        h.b.tvLine1.setVisibility(line1.length() > 0 ? View.VISIBLE : View.GONE);

        // Hàng 2: Topping
        String toppingLabel = ci.getToppingsLabel();
        if (!TextUtils.isEmpty(toppingLabel)) {
            h.b.tvToppings.setText(h.itemView.getContext().getString(R.string.toppings) + " " + toppingLabel);
            h.b.tvToppings.setVisibility(View.VISIBLE);
        } else {
            h.b.tvToppings.setVisibility(View.GONE);
        }

        // Ghi chú
        String note = ci.getNote();
        if (!TextUtils.isEmpty(note)) {
            h.b.tvNote.setText(h.itemView.getContext().getString(R.string.note, note));
            h.b.tvNote.setVisibility(View.VISIBLE);
        } else {
            h.b.tvNote.setVisibility(View.GONE);
        }

        // Đơn giá × SL và Thành tiền
        long unit = ci.getUnitPrice();
        int qty = ci.getQuantity();
        h.b.tvUnitQty.setText(String.format(Locale.getDefault(), "%s × %d", MoneyUtils.formatVnd((double) unit), qty));
        h.b.tvLineTotal.setText(MoneyUtils.formatVnd((double) ci.getTotalPrice()));

        // Ảnh
        String imageName = p.getImageUrl();
        ImageLoader.load(h.b.img, imageName, com.pro.milkteaapp.R.drawable.milktea);
    }

    private void bindComboItem(@NonNull VH h, ComboCartItem comboItem) {
        com.pro.milkteaapp.models.Combo combo = comboItem.getCombo();

        // Tên
        h.b.tvName.setText(combo != null ? combo.getName() : h.itemView.getContext().getString(R.string.combo_label));

        // Combo - không có size/topping/sugar/ice
        h.b.tvLine1.setText(h.itemView.getContext().getString(R.string.combo_label));
        h.b.tvLine1.setVisibility(View.VISIBLE);
        h.b.tvToppings.setVisibility(View.GONE);
        h.b.tvNote.setVisibility(View.GONE);

        // Đơn giá × SL và Thành tiền
        long unit = comboItem.getUnitPrice();
        int qty = comboItem.getQuantity();
        h.b.tvUnitQty.setText(String.format(Locale.getDefault(), "%s × %d", MoneyUtils.formatVnd((double) unit), qty));
        h.b.tvLineTotal.setText(MoneyUtils.formatVnd((double) comboItem.getTotalPrice()));

        // Ảnh
        String imageName = comboItem.getImageUrl();
        ImageLoader.load(h.b.img, imageName, com.pro.milkteaapp.R.drawable.milktea);
    }

    @Override public int getItemCount() { return totalCount; }

    static class VH extends RecyclerView.ViewHolder {
        final ItemCheckoutRowBinding b;
        VH(ItemCheckoutRowBinding b) { super(b.getRoot()); this.b = b; }
    }
}
