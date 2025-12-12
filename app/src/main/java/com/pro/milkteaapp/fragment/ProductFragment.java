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
import com.pro.milkteaapp.adapter.ProductSectionAdapter;
import com.pro.milkteaapp.databinding.FragmentProductBinding;
import com.pro.milkteaapp.fragment.bottomsheet.ProductDetailBottomSheet;
import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.models.Product;
import com.pro.milkteaapp.repository.CategoriesRepository;
import com.pro.milkteaapp.repository.ProductRepository;
import com.pro.milkteaapp.utils.ProductSearchHelper;
import com.pro.milkteaapp.utils.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProductFragment extends Fragment implements ProductSectionAdapter.OnProductClick {

    private static final String ALL = "Tất cả";

    private FragmentProductBinding binding;
    private ProductSectionAdapter adapter;

    private final List<Product> allProduct = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();
    private final Map<String, Integer> headerPositions = new HashMap<>();

    private ProductRepository productsRepository;
    private CategoriesRepository categoriesRepository;
    private ListenerRegistration subProduct;

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

        productsRepository = new ProductRepository();
        categoriesRepository = new CategoriesRepository();
        adapter = new ProductSectionAdapter(this);

        GridLayoutManager glm = new GridLayoutManager(requireContext(), 2);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                int viewType = adapter.getItemViewType(position);
                return (viewType == ProductSectionAdapter.TYPE_HEADER) ? 2 : 1;
            }
        });
        binding.recyclerView.setLayoutManager(glm);
        binding.recyclerView.setHasFixedSize(true);
        binding.recyclerView.setAdapter(adapter);

        appBar = binding.appbar;

        setupFabCart();
        setupSearchListener();
        setupDrawer();

        loadCategoriesThenListenProduct();
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

    private void loadCategoriesThenListenProduct() {
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
                listenProductRealtime();
            }

            @Override
            public void onError(@NonNull Exception error) {
                Log.w("Firestore", "Load categories failed: " + error.getMessage());
                listenProductRealtime();
            }
        });
    }

    private void listenProductRealtime() {
        if (subProduct != null) { subProduct.remove(); subProduct = null; }
        setLoading(true); setEmpty(false);
        subProduct = productsRepository.listenAllProduct(new ProductRepository.OnProductListener() {
            @Override
            public void onProductReceived(@NonNull List<Product> products) {
                if (!isAdded() || binding == null) return;
                setLoading(false);
                allProduct.clear();
                allProduct.addAll(products);
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


    private void ensureCategoriesIfEmpty() {
        if (!categories.isEmpty()) return;
        LinkedHashMap<String, Boolean> seen = new LinkedHashMap<>();
        for (Product p : allProduct) {
            String cat = StringUtils.normalize(p.getCategory());
            if (!cat.isEmpty() && !seen.containsKey(cat)) seen.put(cat, true);
        }
        categories.addAll(seen.keySet());
    }

    private void rebuildSectionsAndShow(@Nullable String filterQuery) {
        List<ProductSectionAdapter.Row> rows = ProductSearchHelper.rebuildSections(
                allProduct,
                categories,
                filterQuery,
                headerPositions
        );
        adapter.submitRows(rows);
        setEmpty(rows.isEmpty());
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
        
        List<Product> filtered = ProductSearchHelper.filterByCategory(allProduct, category);
        rebuildSectionsWithProduct(filtered, query);
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
        
        // Add categories (use helper to order categories)
        List<String> orderedCats = getOrderedCategories();
        for (int i = 0; i < orderedCats.size(); i++) {
            String cat = orderedCats.get(i);
            if (cat.equals(ALL)) continue; // Skip "Tất cả" as it's already added
            
            int menuId = android.view.Menu.FIRST + i;
            menu.add(android.view.Menu.NONE, menuId, android.view.Menu.NONE, cat)
                .setCheckable(true)
                .setChecked(selectedCategory.equals(cat));
        }
    }
    
    @NonNull
    private List<String> getOrderedCategories() {
        List<String> preferred = java.util.List.of("Trà sữa", "Trà", "Trái cây tươi", "Cà Phê");
        List<String> result = new ArrayList<>();
        result.add(ALL);
        if (categories != null) {
            for (String p : preferred) {
                for (String s : categories) {
                    if (StringUtils.normalize(s).equals(StringUtils.normalize(p))) {
                        result.add(s);
                        break;
                    }
                }
            }
            for (String s : categories) {
                boolean already = false;
                for (String r : result) {
                    if (StringUtils.normalize(r).equals(StringUtils.normalize(s))) {
                        already = true;
                        break;
                    }
                }
                if (!already) result.add(s);
            }
        }
        return result;
    }
    
    private void filterByCategory(@NonNull String category) {
        if (category.equals(ALL)) {
            rebuildSectionsAndShow(null);
            return;
        }
        
        // Filter products by category
        List<Product> filtered = ProductSearchHelper.filterByCategory(allProduct, category);
        
        // Rebuild sections with filtered products
        rebuildSectionsWithProduct(filtered, null);
    }
    
    private void rebuildSectionsWithProduct(@NonNull List<Product> productsToShow, @Nullable String filterQuery) {
        List<ProductSectionAdapter.Row> rows = ProductSearchHelper.rebuildSectionsWithProducts(
                productsToShow,
                categories,
                filterQuery,
                headerPositions
        );
        adapter.submitRows(rows);
        setEmpty(rows.isEmpty());
    }

    @Override
    public void onDestroyView() {
        if (subProduct != null) { subProduct.remove(); subProduct = null; }
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onClick(Product milkTea) {
        if (!isAdded() || milkTea == null || TextUtils.isEmpty(milkTea.getId())) return;

        ProductDetailBottomSheet sheet = ProductDetailBottomSheet.newInstance(milkTea);
        sheet.setListener((p, qty, size, sugar, ice, note, toppings) -> {
            CartItem newItem = new CartItem(p, qty, size, sugar, ice, note, toppings);

            com.pro.milkteaapp.utils.CartManager cartManager = com.pro.milkteaapp.utils.CartManager.getInstance();
            int existed = cartManager.findCartItem(newItem);
            if (existed >= 0) {
                cartManager.getCartItems().get(existed).increaseQuantity(qty);
            } else {
                cartManager.addCartItem(newItem);
            }

            String label = newItem.getToppingsLabel();
            Toast.makeText(requireContext(),
                    "Đã thêm " + qty + " " + p.getName() + " (" + size + ("".equals(label) ? "" : ", " + label) + ")",
                    Toast.LENGTH_SHORT).show();
        });
        sheet.show(getParentFragmentManager(), "product_detail_sheet");
    }

}
