package com.pro.milkteaapp.fragment;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.firestore.ListenerRegistration;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.ProductsSectionAdapter;
import com.pro.milkteaapp.databinding.FragmentProductBinding;
import com.pro.milkteaapp.fragment.bottomsheet.ProductDetailBottomSheet;
import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.models.Products;
import com.pro.milkteaapp.repository.CategoriesRepository;
import com.pro.milkteaapp.repository.ProductsRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProductFragment extends Fragment implements ProductsSectionAdapter.OnProductClick {

    private static final String ALL = "Tất cả";

    private FragmentProductBinding binding;
    private ProductsSectionAdapter adapter;

    private final List<Products> allProducts = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();
    private final Map<String, Integer> headerPositions = new HashMap<>();

    private ProductsRepository productsRepository;
    private CategoriesRepository categoriesRepository;
    private ListenerRegistration subProducts;

    private AppBarLayout appBar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProductBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        productsRepository = new ProductsRepository();
        categoriesRepository = new CategoriesRepository();
        adapter = new ProductsSectionAdapter(this);

        GridLayoutManager glm = new GridLayoutManager(getContext(), 2);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                int viewType = adapter.getItemViewType(position);
                return (viewType == ProductsSectionAdapter.TYPE_HEADER) ? 2 : 1;
            }
        });
        binding.recyclerView.setLayoutManager(glm);
        binding.recyclerView.setHasFixedSize(true);
        binding.recyclerView.setAdapter(adapter);

        appBar = binding.appbar;

        setupFabCart();
        setupSearchListener();
        setupDrawer();

        loadCategoriesThenListenProducts();
    }

    private void scrollToCategorySmooth(@NonNull String category, int topOffsetPx) {
        if (binding == null) return;
        Integer pos = headerPositions.get(category);
        if (pos == null) { Log.w("UI", "header not found for " + category); return; }
        binding.recyclerView.post(() -> {
            if (binding.recyclerView.getLayoutManager() == null) return;
            androidx.recyclerview.widget.LinearSmoothScroller scroller =
                    new androidx.recyclerview.widget.LinearSmoothScroller(requireContext()) {
                        @Override protected int getVerticalSnapPreference() { return SNAP_TO_START; }
                        @Override public int calculateDyToMakeVisible(View view, int snapPreference) {
                            int dy = super.calculateDyToMakeVisible(view, snapPreference);
                            return dy - topOffsetPx;
                        }
                    };
            scroller.setTargetPosition(pos);
            binding.recyclerView.getLayoutManager().startSmoothScroll(scroller);
        });
    }
    private int dpToPx() { return Math.round(8 * getResources().getDisplayMetrics().density); }

    private void loadCategoriesThenListenProducts() {
        setLoading(true);
        categories.clear();
        categoriesRepository.fetchActiveCategories(new CategoriesRepository.OnCategoriesListener() {
            @Override
            public void onCategoriesReceived(@NonNull List<com.pro.milkteaapp.models.Category> categoryList) {
                for (com.pro.milkteaapp.models.Category cat : categoryList) {
                    String name = cat.getName();
                    if (!TextUtils.isEmpty(name)) categories.add(name.trim());
                }
                updateDrawerMenu();
                listenProductsRealtime();
            }

            @Override
            public void onError(@NonNull Exception error) {
                Log.w("Firestore", "Load categories failed: " + error.getMessage());
                listenProductsRealtime();
            }
        });
    }

    private void listenProductsRealtime() {
        if (subProducts != null) { subProducts.remove(); subProducts = null; }
        setLoading(true); setEmpty(false);
        subProducts = productsRepository.listenAllProducts(new ProductsRepository.OnProductsListener() {
            @Override
            public void onProductsReceived(@NonNull List<Products> products) {
                if (!isAdded() || binding == null) return;
                setLoading(false);
                allProducts.clear();
                allProducts.addAll(products);
                ensureCategoriesIfEmpty();
                updateDrawerMenu();
                
                // Apply current filter
                if (selectedCategory.equals(ALL)) {
                    rebuildSectionsAndShow(null);
                } else {
                    filterByCategory(selectedCategory);
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (!isAdded() || binding == null) return;
                Log.e("Firestore", "Listen products failed", error);
                setLoading(false);
                adapter.submitRows(new ArrayList<>());
                setEmpty(true);
            }
        });
    }

    // Method này không còn cần thiết vì Repository đã parse sẵn
    // Giữ lại để tương thích nếu có code khác gọi
    @Deprecated
    private void fillProductsFromSnapshot(@Nullable Object snapshot) {
        // Không còn sử dụng, logic đã được chuyển vào Repository
    }

    private void ensureCategoriesIfEmpty() {
        if (!categories.isEmpty()) return;
        LinkedHashMap<String, Boolean> seen = new LinkedHashMap<>();
        for (Products p : allProducts) {
            String cat = norm(safe(p.getCategory()));
            if (!cat.isEmpty() && !seen.containsKey(cat)) seen.put(cat, true);
        }
        categories.addAll(seen.keySet());
    }

    private void rebuildSectionsAndShow(@Nullable String filterQuery) {
        String q = filterQuery == null ? "" : filterQuery.trim().toLowerCase(Locale.getDefault());
        List<ProductsSectionAdapter.Row> rows = new ArrayList<>();
        headerPositions.clear();

        List<String> orderedCats = orderCategories(categories);

        for (String cat : orderedCats) {
            String catKey = norm(cat);
            List<Products> underCat = new ArrayList<>();
            for (Products p : allProducts) {
                String pCatKey = norm(p.getCategory());
                if (pCatKey.equals(catKey)) {
                    if (q.isEmpty() || matchesQuery(p, q)) underCat.add(p);
                }
            }
            if (underCat.isEmpty()) continue;
            headerPositions.put(cat, rows.size());
            rows.add(ProductsSectionAdapter.Row.header(cat));
            for (Products p : underCat) rows.add(ProductsSectionAdapter.Row.product(p));
        }
        adapter.submitRows(rows);
        setEmpty(rows.isEmpty());
    }

    private boolean matchesQuery(Products p, String q) {
        String name = safe(p.getName()).toLowerCase(Locale.getDefault());
        String cat  = safe(p.getCategory()).toLowerCase(Locale.getDefault());
        String sname= safe(p.getSearchableName()).toLowerCase(Locale.getDefault());
        return name.contains(q) || cat.contains(q) || sname.contains(q);
    }

    private List<String> orderCategories(List<String> src) {
        List<String> preferred = java.util.List.of("Trà sữa", "Trà", "Trái cây tươi", "Cà Phê");
        List<String> result = new ArrayList<>();
        result.add(ALL);
        if (src != null) {
            for (String p : preferred)
                for (String s : src)
                    if (norm(s).equals(norm(p))) { result.add(s); break; }
            for (String s : src) {
                boolean already = false;
                for (String r : result) if (norm(r).equals(norm(s))) { already = true; break; }
                if (!already) result.add(s);
            }
        }
        return result;
    }


    private void setupSearchListener() {
        if (binding == null) return;
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s == null ? "" : s.toString();
                if (selectedCategory.equals(ALL)) {
                    rebuildSectionsAndShow(query);
                } else {
                    filterByCategoryWithQuery(selectedCategory, query);
                }
            }
        });
    }
    
    private void filterByCategoryWithQuery(@NonNull String category, @NonNull String query) {
        if (category.equals(ALL)) {
            rebuildSectionsAndShow(query);
            return;
        }
        
        String catKey = norm(category);
        List<Products> filtered = new ArrayList<>();
        for (Products p : allProducts) {
            String pCatKey = norm(p.getCategory());
            if (pCatKey.equals(catKey)) {
                filtered.add(p);
            }
        }
        
        rebuildSectionsWithProducts(filtered, query);
    }

    private void setLoading(boolean show) { if (binding != null) binding.loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE); }
    private void setEmpty(boolean show)   { if (binding != null) binding.emptyState.setVisibility(show ? View.VISIBLE : View.GONE); }

    private void setupFabCart() {
        if (binding == null) return;
        binding.fabCart.setOnClickListener(v -> {
            if (!isAdded()) return;
            if (requireActivity() instanceof com.pro.milkteaapp.activity.MainActivity) {
                ((com.pro.milkteaapp.activity.MainActivity) requireActivity()).openCartTab();
            }
        });
    }

    private String selectedCategory = ALL;
    
    private void setupDrawer() {
        if (binding == null) return;
        
        // Setup toolbar menu click để mở drawer
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_drawer) {
                if (binding.drawerLayout != null) {
                    binding.drawerLayout.openDrawer(androidx.core.view.GravityCompat.END);
                }
                return true;
            }
            return false;
        });
        
        // Setup drawer menu click
        if (binding.navigationView != null) {
            binding.navigationView.setNavigationItemSelectedListener(item -> {
                int itemId = item.getItemId();
                String category = item.getTitle().toString();
                
                // Update selected category
                selectedCategory = category;
                
                // Filter products
                filterByCategory(category);
                
                // Close drawer
                if (binding.drawerLayout != null) {
                    binding.drawerLayout.closeDrawer(androidx.core.view.GravityCompat.END);
                }
                
                return true;
            });
        }
    }
    
    private void updateDrawerMenu() {
        if (binding == null || binding.navigationView == null) return;
        
        android.view.Menu menu = binding.navigationView.getMenu();
        menu.clear();
        
        // Add "Tất cả" first
        menu.add(android.view.Menu.NONE, R.id.menu_all, android.view.Menu.NONE, ALL)
            .setCheckable(true)
            .setChecked(selectedCategory.equals(ALL));
        
        // Add categories
        List<String> orderedCats = orderCategories(categories);
        for (int i = 0; i < orderedCats.size(); i++) {
            String cat = orderedCats.get(i);
            if (cat.equals(ALL)) continue; // Skip "Tất cả" as it's already added
            
            int menuId = android.view.Menu.FIRST + i;
            menu.add(android.view.Menu.NONE, menuId, android.view.Menu.NONE, cat)
                .setCheckable(true)
                .setChecked(selectedCategory.equals(cat));
        }
    }
    
    private void filterByCategory(@NonNull String category) {
        if (category.equals(ALL)) {
            rebuildSectionsAndShow(null);
            return;
        }
        
        // Filter products by category
        String catKey = norm(category);
        List<Products> filtered = new ArrayList<>();
        for (Products p : allProducts) {
            String pCatKey = norm(p.getCategory());
            if (pCatKey.equals(catKey)) {
                filtered.add(p);
            }
        }
        
        // Rebuild sections with filtered products
        rebuildSectionsWithProducts(filtered, null);
    }
    
    private void rebuildSectionsWithProducts(@NonNull List<Products> productsToShow, @Nullable String filterQuery) {
        String q = filterQuery == null ? "" : filterQuery.trim().toLowerCase(Locale.getDefault());
        List<ProductsSectionAdapter.Row> rows = new ArrayList<>();
        headerPositions.clear();
        
        // Group by category (use normalized key for matching, but original name for display)
        Map<String, List<Products>> categoryMap = new LinkedHashMap<>();
        Map<String, String> normalizedToOriginal = new HashMap<>();
        
        // Build mapping from normalized to original category names
        for (String originalCat : categories) {
            String normalized = norm(originalCat);
            normalizedToOriginal.put(normalized, originalCat);
        }
        
        for (Products p : productsToShow) {
            String catNormalized = norm(safe(p.getCategory()));
            if (catNormalized.isEmpty()) catNormalized = "khác";
            
            // Get original category name (with proper casing)
            String catOriginal = normalizedToOriginal.get(catNormalized);
            if (catOriginal == null) {
                // If not found in categories list, use the product's category as-is or "Khác"
                catOriginal = safe(p.getCategory());
                if (catOriginal.isEmpty()) catOriginal = "Khác";
            }
            
            if (q.isEmpty() || matchesQuery(p, q)) {
                categoryMap.putIfAbsent(catOriginal, new ArrayList<>());
                categoryMap.get(catOriginal).add(p);
            }
        }
        
        // Build rows using original category names
        for (Map.Entry<String, List<Products>> entry : categoryMap.entrySet()) {
            String cat = entry.getKey();
            List<Products> products = entry.getValue();
            if (products.isEmpty()) continue;
            
            headerPositions.put(cat, rows.size());
            rows.add(ProductsSectionAdapter.Row.header(cat));
            for (Products p : products) {
                rows.add(ProductsSectionAdapter.Row.product(p));
            }
        }
        
        adapter.submitRows(rows);
        setEmpty(rows.isEmpty());
    }

    @Override
    public void onDestroyView() {
        if (subProducts != null) { subProducts.remove(); subProducts = null; }
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onClick(Products milkTea) {
        if (!isAdded() || milkTea == null || TextUtils.isEmpty(milkTea.getId())) return;

        ProductDetailBottomSheet sheet = ProductDetailBottomSheet.newInstance(milkTea);
        sheet.setListener((p, qty, size, sugar, ice, note, toppings) -> {
            CartItem newItem = new CartItem(p, qty, size, sugar, ice, note, toppings);

            int existed = -1;
            for (int i = 0; i < CartFragment.cartItems.size(); i++) {
                if (newItem.equals(CartFragment.cartItems.get(i))) { existed = i; break; }
            }
            if (existed >= 0) {
                CartFragment.cartItems.get(existed).increaseQuantity(qty);
            } else {
                CartFragment.cartItems.add(newItem);
            }

            String label = newItem.getToppingsLabel();
            Toast.makeText(requireContext(),
                    "Đã thêm " + qty + " " + p.getName() + " (" + size + ("".equals(label) ? "" : ", " + label) + ")",
                    Toast.LENGTH_SHORT).show();
        });
        sheet.show(getParentFragmentManager(), "product_detail_sheet");
    }

    // ===== Combo Section =====
    private static String safe(String s){ return s == null ? "" : s; }
    private static String norm(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.getDefault()); }
}
