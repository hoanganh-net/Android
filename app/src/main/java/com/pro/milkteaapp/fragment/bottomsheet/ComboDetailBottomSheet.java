package com.pro.milkteaapp.fragment.bottomsheet;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.ComboProductOptionAdapter;
import com.pro.milkteaapp.models.Combo;
import com.pro.milkteaapp.models.ComboProductOption;
import com.pro.milkteaapp.models.Products;
import com.pro.milkteaapp.models.SelectedTopping;
import com.pro.milkteaapp.repository.ProductsRepository;
import com.pro.milkteaapp.utils.ImageLoader;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.util.ArrayList;
import java.util.List;

public class ComboDetailBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_COMBO = "arg_combo";

    public interface Listener {
        void onAddToCart(Combo combo, int qty, java.util.List<com.pro.milkteaapp.models.ComboProductOption> productOptions);
    }

    private Listener listener;
    private Combo combo;

    private ImageView comboImage;
    private TextView nameTextView, priceTextView, descriptionTextView, quantityTextView, totalPriceTextView;
    private MaterialButton decrementButton, incrementButton, addToCartButton;
    private RecyclerView rvProducts;
    private ComboProductOptionAdapter productsAdapter;
    private ProductsRepository productsRepository;

    private int quantity = 1;

    public static ComboDetailBottomSheet newInstance(@NonNull Combo combo) {
        Bundle b = new Bundle();
        b.putSerializable(ARG_COMBO, combo);
        ComboDetailBottomSheet f = new ComboDetailBottomSheet();
        f.setArguments(b);
        return f;
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    @Override
    public int getTheme() {
        return R.style.AppBottomSheetDialogTheme;
    }

    @Override
    public void onStart() {
        super.onStart();
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        if (dialog != null) {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setFitToContents(true);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.bottomsheet_combo_detail, container, false);
        comboImage = v.findViewById(R.id.comboImage);
        nameTextView = v.findViewById(R.id.nameTextView);
        priceTextView = v.findViewById(R.id.priceTextView);
        descriptionTextView = v.findViewById(R.id.descriptionTextView);
        quantityTextView = v.findViewById(R.id.quantityTextView);
        totalPriceTextView = v.findViewById(R.id.totalPriceTextView);
        decrementButton = v.findViewById(R.id.decrementButton);
        incrementButton = v.findViewById(R.id.incrementButton);
        addToCartButton = v.findViewById(R.id.addToCartButton);
        rvProducts = v.findViewById(R.id.rvProducts);
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        // Combo
        if (getArguments() != null) {
            Object o = getArguments().getSerializable(ARG_COMBO);
            if (o instanceof Combo) combo = (Combo) o;
        }
        if (combo == null) {
            dismiss();
            return;
        }

        // Bind text
        nameTextView.setText(combo.getName());
        priceTextView.setText(MoneyUtils.formatVnd(combo.getPrice() != null ? combo.getPrice() : 0.0));
        if (!TextUtils.isEmpty(combo.getDescription())) {
            descriptionTextView.setText(combo.getDescription());
            descriptionTextView.setVisibility(View.VISIBLE);
        } else {
            descriptionTextView.setVisibility(View.GONE);
        }

        // Image
        ImageLoader.load(comboImage, combo.getImageUrl(), R.drawable.milktea);

        // Setup products adapter
        productsRepository = new ProductsRepository();
        productsAdapter = new ComboProductOptionAdapter();
        // Set callback để cập nhật giá khi topping thay đổi
        productsAdapter.setOnToppingChangeListener(() -> updateTotal());
        rvProducts.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvProducts.setAdapter(productsAdapter);
        rvProducts.setNestedScrollingEnabled(false);
        
        // Load products in combo
        loadProductsInCombo();

        // Quantity
        quantityTextView.setText(String.valueOf(quantity));
        updateTotal();

        // Buttons
        decrementButton.setOnClickListener(v1 -> {
            if (quantity > 1) {
                quantity--;
                quantityTextView.setText(String.valueOf(quantity));
                updateTotal();
            }
        });

        incrementButton.setOnClickListener(v12 -> {
            quantity++;
            quantityTextView.setText(String.valueOf(quantity));
            updateTotal();
        });

        addToCartButton.setOnClickListener(v13 -> {
            if (listener != null && combo != null && productsAdapter != null) {
                // Lấy danh sách ComboProductOption từ adapter
                java.util.List<com.pro.milkteaapp.models.ComboProductOption> productOptions = 
                        productsAdapter.getProductOptions();
                listener.onAddToCart(combo, quantity, productOptions);
            }
            dismiss();
        });
    }

    private void loadProductsInCombo() {
        if (combo == null || productsRepository == null) return;
        
        List<String> productIds = combo.getProductIds();
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        
        List<Products> loadedProducts = new ArrayList<>();
        final int[] loadedCount = {0};
        
        for (String productId : productIds) {
            productsRepository.fetchProductById(productId, new ProductsRepository.OnProductListener() {
                @Override
                public void onProductReceived(@NonNull Products product) {
                    loadedProducts.add(product);
                    loadedCount[0]++;
                    
                    if (loadedCount[0] == productIds.size()) {
                        // Tất cả sản phẩm đã load xong
                        if (productsAdapter != null) {
                            productsAdapter.submitProducts(loadedProducts);
                        }
                    }
                }
                
                @Override
                public void onError(@NonNull Exception error) {
                    loadedCount[0]++;
                    // Bỏ qua lỗi, tiếp tục load các sản phẩm khác
                    if (loadedCount[0] == productIds.size() && productsAdapter != null) {
                        productsAdapter.submitProducts(loadedProducts);
                    }
                }
            });
        }
    }

    private void updateTotal() {
        if (combo == null) return;
        double price = combo.getPrice() != null ? combo.getPrice() : 0.0;
        
        // Phụ thu theo size của từng sản phẩm và cộng giá toppings
        if (productsAdapter != null) {
            List<com.pro.milkteaapp.models.ComboProductOption> productOptions = 
                    productsAdapter.getProductOptions();
            if (productOptions != null) {
                for (com.pro.milkteaapp.models.ComboProductOption option : productOptions) {
                    if (option != null) {
                        // Phụ thu theo size
                        String size = option.getSize();
                        String normalizedSize = size != null ? size.trim().toLowerCase() : "";
                        if ("vừa".equals(normalizedSize) || "medium".equals(normalizedSize)) {
                            price += 3000;
                        } else if ("lớn".equals(normalizedSize) || "large".equals(normalizedSize)) {
                            price += 6000;
                        }
                        
                        // Cộng giá toppings
                        if (option.getToppings() != null) {
                            for (com.pro.milkteaapp.models.SelectedTopping topping : option.getToppings()) {
                                if (topping != null && topping.price > 0) {
                                    price += topping.price;
                                }
                            }
                        }
                    }
                }
            }
        }
        
        double total = price * quantity;
        totalPriceTextView.setText(MoneyUtils.formatVnd(total));
    }
}

