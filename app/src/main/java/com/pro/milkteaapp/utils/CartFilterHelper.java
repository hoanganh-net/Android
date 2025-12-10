package com.pro.milkteaapp.utils;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ConcatAdapter;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.CartAdapter;
import com.pro.milkteaapp.adapter.ComboCartAdapter;

/**
 * Helper class để xử lý filter logic cho CartFragment.
 * Tách logic filter ra khỏi Fragment để giảm độ phức tạp.
 */
public class CartFilterHelper {
    
    private String selectedFilter;
    private final Context context;
    private final CartAdapter adapter;
    private final ComboCartAdapter comboAdapter;
    
    public CartFilterHelper(@NonNull Context context, 
                           @NonNull CartAdapter adapter, 
                           @NonNull ComboCartAdapter comboAdapter) {
        this.context = context;
        this.adapter = adapter;
        this.comboAdapter = comboAdapter;
        this.selectedFilter = context.getString(R.string.filter_all);
    }
    
    /**
     * Set filter và trả về ConcatAdapter tương ứng.
     */
    @NonNull
    public ConcatAdapter getFilteredAdapter(@NonNull String filter) {
        this.selectedFilter = filter;
        return buildConcatAdapter();
    }
    
    /**
     * Get current filter.
     */
    @NonNull
    public String getSelectedFilter() {
        return selectedFilter;
    }
    
    /**
     * Build ConcatAdapter dựa trên filter hiện tại.
     */
    @NonNull
    private ConcatAdapter buildConcatAdapter() {
        String filterProducts = context.getString(R.string.filter_products);
        String filterCombo = context.getString(R.string.filter_combo);
        
        if (filterProducts.equals(selectedFilter)) {
            // Chỉ hiển thị sản phẩm thường
            return new ConcatAdapter(adapter);
        } else if (filterCombo.equals(selectedFilter)) {
            // Chỉ hiển thị combo
            return new ConcatAdapter(comboAdapter);
        } else {
            // Hiển thị tất cả
            return new ConcatAdapter(adapter, comboAdapter);
        }
    }
}

