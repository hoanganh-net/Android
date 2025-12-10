package com.pro.milkteaapp.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.pro.milkteaapp.adapter.ProductSectionAdapter;
import com.pro.milkteaapp.models.Product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Helper class để xử lý search và filter logic cho ProductFragment.
 * Tách logic search/filter ra khỏi Fragment để giảm độ phức tạp.
 */
public class ProductSearchHelper {
    
    private static final String ALL = "Tất cả";
    
    /**
     * Rebuild sections với filter query.
     */
    @NonNull
    public static List<ProductSectionAdapter.Row> rebuildSections(
            @NonNull List<Product> allProducts,
            @NonNull List<String> categories,
            @Nullable String filterQuery,
            @NonNull Map<String, Integer> headerPositions) {
        
        String q = filterQuery == null ? "" : filterQuery.trim().toLowerCase(Locale.getDefault());
        List<ProductSectionAdapter.Row> rows = new ArrayList<>();
        headerPositions.clear();
        
        List<String> orderedCats = orderCategories(categories);
        
        for (String cat : orderedCats) {
            String catKey = normalize(cat);
            List<Product> underCat = new ArrayList<>();
            for (Product p : allProducts) {
                String pCatKey = normalize(p.getCategory());
                if (pCatKey.equals(catKey)) {
                    if (q.isEmpty() || matchesQuery(p, q)) {
                        underCat.add(p);
                    }
                }
            }
            if (underCat.isEmpty()) continue;
            headerPositions.put(cat, rows.size());
            rows.add(ProductSectionAdapter.Row.header(cat));
            for (Product p : underCat) {
                rows.add(ProductSectionAdapter.Row.product(p));
            }
        }
        
        return rows;
    }
    
    /**
     * Rebuild sections với filtered products và query.
     */
    @NonNull
    public static List<ProductSectionAdapter.Row> rebuildSectionsWithProducts(
            @NonNull List<Product> productsToShow,
            @NonNull List<String> categories,
            @Nullable String filterQuery,
            @NonNull Map<String, Integer> headerPositions) {
        
        String q = filterQuery == null ? "" : filterQuery.trim().toLowerCase(Locale.getDefault());
        List<ProductSectionAdapter.Row> rows = new ArrayList<>();
        headerPositions.clear();
        
        // Group by category
        Map<String, List<Product>> categoryMap = new LinkedHashMap<>();
        Map<String, String> normalizedToOriginal = new HashMap<>();
        
        // Build mapping from normalized to original category names
        for (String originalCat : categories) {
            String normalized = normalize(originalCat);
            normalizedToOriginal.put(normalized, originalCat);
        }
        
        for (Product p : productsToShow) {
            String catNormalized = normalize(safe(p.getCategory()));
            if (catNormalized.isEmpty()) catNormalized = "khác";
            
            // Get original category name
            String catOriginal = normalizedToOriginal.get(catNormalized);
            if (catOriginal == null) {
                catOriginal = safe(p.getCategory());
                if (catOriginal.isEmpty()) catOriginal = "Khác";
            }
            
            if (q.isEmpty() || matchesQuery(p, q)) {
                categoryMap.putIfAbsent(catOriginal, new ArrayList<>());
                categoryMap.get(catOriginal).add(p);
            }
        }
        
        // Build rows using original category names
        for (Map.Entry<String, List<Product>> entry : categoryMap.entrySet()) {
            String cat = entry.getKey();
            List<Product> products = entry.getValue();
            if (products.isEmpty()) continue;
            
            headerPositions.put(cat, rows.size());
            rows.add(ProductSectionAdapter.Row.header(cat));
            for (Product p : products) {
                rows.add(ProductSectionAdapter.Row.product(p));
            }
        }
        
        return rows;
    }
    
    /**
     * Filter products by category.
     */
    @NonNull
    public static List<Product> filterByCategory(
            @NonNull List<Product> allProducts,
            @NonNull String category) {
        
        if (ALL.equals(category)) {
            return new ArrayList<>(allProducts);
        }
        
        String catKey = normalize(category);
        List<Product> filtered = new ArrayList<>();
        for (Product p : allProducts) {
            String pCatKey = normalize(p.getCategory());
            if (pCatKey.equals(catKey)) {
                filtered.add(p);
            }
        }
        return filtered;
    }
    
    /**
     * Check if product matches query.
     */
    private static boolean matchesQuery(@NonNull Product p, @NonNull String q) {
        String name = safe(p.getName()).toLowerCase(Locale.getDefault());
        String cat = safe(p.getCategory()).toLowerCase(Locale.getDefault());
        String sname = safe(p.getSearchableName()).toLowerCase(Locale.getDefault());
        return name.contains(q) || cat.contains(q) || sname.contains(q);
    }
    
    /**
     * Order categories with preferred order.
     */
    @NonNull
    private static List<String> orderCategories(@NonNull List<String> src) {
        List<String> preferred = java.util.List.of("Trà sữa", "Trà", "Trái cây tươi", "Cà Phê");
        List<String> result = new ArrayList<>();
        result.add(ALL);
        if (src != null) {
            for (String p : preferred) {
                for (String s : src) {
                    if (normalize(s).equals(normalize(p))) {
                        result.add(s);
                        break;
                    }
                }
            }
            for (String s : src) {
                boolean already = false;
                for (String r : result) {
                    if (normalize(r).equals(normalize(s))) {
                        already = true;
                        break;
                    }
                }
                if (!already) result.add(s);
            }
        }
        return result;
    }
    
    @NonNull
    private static String safe(@Nullable String s) {
        return s == null ? "" : s;
    }
    
    @NonNull
    private static String normalize(@Nullable String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.getDefault());
    }
}

