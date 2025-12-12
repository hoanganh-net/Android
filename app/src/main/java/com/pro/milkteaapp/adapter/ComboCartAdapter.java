package com.pro.milkteaapp.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.ComboCartItem;
import com.pro.milkteaapp.models.ComboProductOption;
import com.pro.milkteaapp.utils.ImageLoader;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.util.ArrayList;
import java.util.List;

public class ComboCartAdapter extends RecyclerView.Adapter<ComboCartAdapter.ViewHolder> {

    public interface OnItemActionClickListener {
        void onItemRemoved(int position);
        void onQuantityIncreased(int position);
        void onQuantityDecreased(int position);
    }

    private final Context context;
    private final List<ComboCartItem> comboCartItems;
    private final OnItemActionClickListener listener;

    public ComboCartAdapter(Context context, List<ComboCartItem> comboCartItems, OnItemActionClickListener listener) {
        this.context = context;
        this.comboCartItems = comboCartItems != null ? comboCartItems : new ArrayList<>();
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= comboCartItems.size()) return position;
        ComboCartItem item = comboCartItems.get(position);
        return item.getComboId() != null ? item.getComboId().hashCode() : position;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_cart, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            if (payloads.contains("QTY_TOTAL")) {
                if (position < 0 || position >= comboCartItems.size()) return;
                ComboCartItem item = comboCartItems.get(position);
                h.cartItemQuantity.setText(String.valueOf(item.getQuantity()));
                h.cartItemTotal.setText(MoneyUtils.formatVnd(item.getTotalPrice()));
                return;
            }
        }
        super.onBindViewHolder(h, position, payloads);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        if (position < 0 || position >= comboCartItems.size()) return;
        ComboCartItem item = comboCartItems.get(position);
        com.pro.milkteaapp.models.Combo combo = item.getCombo();

        // ===== ẢNH =====
        String imageSource = item.getImageUrl();
        ImageLoader.load(h.cartItemImage, imageSource, R.drawable.milktea);

        // ===== TÊN =====
        h.cartItemName.setText(combo != null ? combo.getName() : "Combo");

        // ===== COMBO - Hiển thị chi tiết các sản phẩm trong combo =====
        List<ComboProductOption> productOptions = item.getProductOptions();
        if (productOptions != null && !productOptions.isEmpty()) {
            StringBuilder detailText = new StringBuilder();
            for (int i = 0; i < productOptions.size(); i++) {
                ComboProductOption option = productOptions.get(i);
                detailText.append(option.getProductName());
                
                // Thêm size, đường, topping
                List<String> options = new ArrayList<>();
                if (!TextUtils.isEmpty(option.getSize())) options.add("Size: " + option.getSize());
                if (!TextUtils.isEmpty(option.getSugar())) options.add("Đường: " + option.getSugar());
                if (option.getToppings() != null && !option.getToppings().isEmpty()) {
                    StringBuilder toppingStr = new StringBuilder("Topping: ");
                    for (int j = 0; j < option.getToppings().size(); j++) {
                        toppingStr.append(option.getToppings().get(j).name());
                        if (j < option.getToppings().size() - 1) toppingStr.append(", ");
                    }
                    options.add(toppingStr.toString());
                }
                
                if (!options.isEmpty()) {
                    detailText.append(" (").append(android.text.TextUtils.join(" • ", options)).append(")");
                }
                
                if (i < productOptions.size() - 1) detailText.append("\n");
            }
            h.cartItemSize.setText(detailText.toString());
            h.cartItemSize.setVisibility(View.VISIBLE);
        } else {
            h.cartItemSize.setText("Combo");
            h.cartItemSize.setVisibility(View.VISIBLE);
        }
        h.cartItemTopping.setVisibility(View.GONE);
        h.cartItemNote.setVisibility(View.GONE);

        // ===== SỐ LƯỢNG + THÀNH TIỀN =====
        h.cartItemQuantity.setText(String.valueOf(item.getQuantity()));
        h.cartItemTotal.setText(MoneyUtils.formatVnd(item.getTotalPrice()));

        // ===== ACTIONS =====
        h.incrementButton.setOnClickListener(v -> {
            if (listener != null) listener.onQuantityIncreased(h.getBindingAdapterPosition());
        });
        h.decrementButton.setOnClickListener(v -> {
            if (listener != null) listener.onQuantityDecreased(h.getBindingAdapterPosition());
        });
        h.deleteButton.setOnClickListener(v -> {
            if (listener != null) listener.onItemRemoved(h.getBindingAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return comboCartItems != null ? comboCartItems.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        ImageView cartItemImage, incrementButton, decrementButton, deleteButton;
        TextView cartItemName, cartItemSize, cartItemTopping, cartItemNote, cartItemQuantity, cartItemTotal;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cartItemImage   = itemView.findViewById(R.id.cartItemImage);
            cartItemName    = itemView.findViewById(R.id.cartItemName);
            cartItemSize    = itemView.findViewById(R.id.cartItemSize);
            cartItemTopping = itemView.findViewById(R.id.cartItemTopping);
            cartItemNote    = itemView.findViewById(R.id.cartItemNote);
            cartItemQuantity= itemView.findViewById(R.id.cartItemQuantity);
            cartItemTotal   = itemView.findViewById(R.id.cartItemTotal);
            incrementButton = itemView.findViewById(R.id.incrementButton);
            decrementButton = itemView.findViewById(R.id.decrementButton);
            deleteButton    = itemView.findViewById(R.id.deleteButton);
        }
    }
}

