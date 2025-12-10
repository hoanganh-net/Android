package com.pro.milkteaapp.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.ComboProductOption;
import com.pro.milkteaapp.models.Products;
import com.pro.milkteaapp.models.SelectedTopping;
import com.pro.milkteaapp.models.Topping;
import com.pro.milkteaapp.repository.ToppingsRepository;

import java.util.ArrayList;
import java.util.List;

public class ComboProductOptionAdapter extends RecyclerView.Adapter<ComboProductOptionAdapter.VH> {

    public interface OnToppingChangeListener {
        void onToppingChanged();
    }

    private final List<Products> products = new ArrayList<>();
    private final List<ComboProductOption> productOptions = new ArrayList<>();
    private final ToppingsRepository toppingsRepository = new ToppingsRepository();
    private OnToppingChangeListener onToppingChangeListener;

    public void submitProducts(@NonNull List<Products> productsList) {
        products.clear();
        products.addAll(productsList);
        
        // Khởi tạo productOptions nếu chưa có
        if (productOptions.size() != products.size()) {
            productOptions.clear();
            for (Products p : products) {
                ComboProductOption option = new ComboProductOption(
                    p.getId(),
                    p.getName()
                );
                productOptions.add(option);
            }
        }
        
        notifyDataSetChanged();
    }

    public List<ComboProductOption> getProductOptions() {
        return new ArrayList<>(productOptions);
    }

    public void setOnToppingChangeListener(OnToppingChangeListener listener) {
        this.onToppingChangeListener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_combo_product_option, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        if (position < 0 || position >= products.size()) return;
        
        Products product = products.get(position);
        ComboProductOption option = position < productOptions.size() 
            ? productOptions.get(position) 
            : new ComboProductOption(product.getId(), product.getName());
        
        h.bind(product, option, position);
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    class VH extends RecyclerView.ViewHolder {
        private final TextView tvProductName;
        private final RadioGroup rgSize, rgSugar;
        private final RecyclerView rvToppings;
        private final TextView tvToppingsEmpty;
        private ToppingSelectAdapter toppingAdapter;
        private final List<Topping> availableToppings = new ArrayList<>();
        private final List<SelectedTopping> selectedToppings = new ArrayList<>();
        private View layoutSugar0, layoutSugar25, layoutSugar50, layoutSugar75, layoutSugar100;

        VH(@NonNull View v) {
            super(v);
            tvProductName = v.findViewById(R.id.tvProductName);
            rgSize = v.findViewById(R.id.rgSize);
            rgSugar = v.findViewById(R.id.rgSugar);
            rvToppings = v.findViewById(R.id.rvToppings);
            tvToppingsEmpty = v.findViewById(R.id.tvToppingsEmpty);
            
            // Find layout containers for sugar options
            layoutSugar0 = v.findViewById(R.id.layoutSugar0);
            layoutSugar25 = v.findViewById(R.id.layoutSugar25);
            layoutSugar50 = v.findViewById(R.id.layoutSugar50);
            layoutSugar75 = v.findViewById(R.id.layoutSugar75);
            layoutSugar100 = v.findViewById(R.id.layoutSugar100);
            
            // Set click listeners for sugar layouts
            if (layoutSugar0 != null) layoutSugar0.setOnClickListener(view -> rgSugar.check(R.id.radioSugar0));
            if (layoutSugar25 != null) layoutSugar25.setOnClickListener(view -> rgSugar.check(R.id.radioSugar25));
            if (layoutSugar50 != null) layoutSugar50.setOnClickListener(view -> rgSugar.check(R.id.radioSugar50));
            if (layoutSugar75 != null) layoutSugar75.setOnClickListener(view -> rgSugar.check(R.id.radioSugar75));
            if (layoutSugar100 != null) layoutSugar100.setOnClickListener(view -> rgSugar.check(R.id.radioSugar100));
            
            rvToppings.setLayoutManager(new LinearLayoutManager(v.getContext()));
            toppingAdapter = new ToppingSelectAdapter((topping, checked) -> {
                if (checked) {
                    boolean exists = false;
                    for (SelectedTopping st : selectedToppings) {
                        if (TextUtils.equals(st.id, topping.getId())) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        selectedToppings.add(new SelectedTopping(
                            topping.getId(),
                            topping.getName(),
                            topping.getPrice()
                        ));
                    }
                } else {
                    for (int i = 0; i < selectedToppings.size(); i++) {
                        if (TextUtils.equals(selectedToppings.get(i).id, topping.getId())) {
                            selectedToppings.remove(i);
                            break;
                        }
                    }
                }
                updateProductOption();
            });
            rvToppings.setAdapter(toppingAdapter);
        }

        void bind(@NonNull Products product, @NonNull ComboProductOption option, int position) {
            savedPosition = position;
            tvProductName.setText(product.getName());
            
            // Clear listeners trước để tránh trigger khi set checked state
            rgSize.setOnCheckedChangeListener(null);
            rgSugar.setOnCheckedChangeListener(null);
            
            // Set size - mặc định là Nhỏ
            String size = option.getSize();
            if (TextUtils.isEmpty(size)) {
                rgSize.check(R.id.radioSizeSmall);
                if (savedPosition >= 0 && savedPosition < productOptions.size()) {
                    productOptions.get(savedPosition).setSize("Nhỏ");
                }
            } else {
                String normalizedSize = normalize(size);
                if ("vừa".equals(normalizedSize) || "medium".equals(normalizedSize)) {
                    rgSize.check(R.id.radioSizeMedium);
                } else if ("lớn".equals(normalizedSize) || "large".equals(normalizedSize)) {
                    rgSize.check(R.id.radioSizeLarge);
                } else {
                    rgSize.check(R.id.radioSizeSmall);
                }
            }
            
            // Set sugar - mặc định là 100%
            String sugar = option.getSugar();
            if (TextUtils.isEmpty(sugar)) {
                rgSugar.check(R.id.radioSugar100); // 100%
                // Cập nhật option để lưu giá trị mặc định
                if (savedPosition >= 0 && savedPosition < productOptions.size()) {
                    productOptions.get(savedPosition).setSugar("100%");
                }
            } else {
                switch (sugar) {
                    case "0%": rgSugar.check(R.id.radioSugar0); break;
                    case "25%": rgSugar.check(R.id.radioSugar25); break;
                    case "50%": rgSugar.check(R.id.radioSugar50); break;
                    case "75%": rgSugar.check(R.id.radioSugar75); break;
                    case "100%": rgSugar.check(R.id.radioSugar100); break;
                    default: 
                        rgSugar.check(R.id.radioSugar100); // Mặc định 100%
                        if (savedPosition >= 0 && savedPosition < productOptions.size()) {
                            productOptions.get(savedPosition).setSugar("100%");
                        }
                        break;
                }
            }
            
            // Load toppings
            selectedToppings.clear();
            selectedToppings.addAll(option.getToppings());
            loadToppingsForProduct(product);
            
            // Set listeners sau khi đã set checked state
            rgSize.setOnCheckedChangeListener((group, checkedId) -> {
                String selectedSize = getSelectedSize(checkedId);
                if (savedPosition >= 0 && savedPosition < productOptions.size()) {
                    productOptions.get(savedPosition).setSize(selectedSize);
                    // Thông báo cho listener khi size thay đổi để cập nhật giá
                    if (onToppingChangeListener != null) {
                        onToppingChangeListener.onToppingChanged();
                    }
                }
            });
            
            rgSugar.setOnCheckedChangeListener((group, checkedId) -> {
                String selectedSugar = getSelectedSugar(checkedId);
                if (savedPosition >= 0 && savedPosition < productOptions.size()) {
                    productOptions.get(savedPosition).setSugar(selectedSugar);
                }
            });
        }

        private String getSelectedSize(int checkedId) {
            if (checkedId == R.id.radioSizeSmall) return "Nhỏ";
            if (checkedId == R.id.radioSizeLarge) return "Lớn";
            return "Vừa";
        }

        private String normalize(String v) {
            return v == null ? "" : v.trim().toLowerCase(java.util.Locale.getDefault());
        }

        private String getSelectedSugar(int checkedId) {
            if (checkedId == R.id.radioSugar0) return "0%";
            if (checkedId == R.id.radioSugar25) return "25%";
            if (checkedId == R.id.radioSugar50) return "50%";
            if (checkedId == R.id.radioSugar75) return "75%";
            if (checkedId == R.id.radioSugar100) return "100%";
            return "0%";
        }


        private void loadToppingsForProduct(@NonNull Products product) {
            String category = product.getCategory();
            if (!TextUtils.isEmpty(category)) {
                toppingsRepository.fetchToppingsByCategory(category, new ToppingsRepository.OnToppingsListener() {
                    @Override
                    public void onToppingsReceived(@NonNull List<Topping> toppings) {
                        availableToppings.clear();
                        availableToppings.addAll(toppings);
                        tvToppingsEmpty.setVisibility(availableToppings.isEmpty() ? View.VISIBLE : View.GONE);
                        rvToppings.setVisibility(availableToppings.isEmpty() ? View.GONE : View.VISIBLE);
                        toppingAdapter.submitList(availableToppings, selectedToppings);
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        tvToppingsEmpty.setVisibility(View.VISIBLE);
                        rvToppings.setVisibility(View.GONE);
                    }
                });
            } else {
                toppingsRepository.fetchActiveToppings(new ToppingsRepository.OnToppingsListener() {
                    @Override
                    public void onToppingsReceived(@NonNull List<Topping> toppings) {
                        availableToppings.clear();
                        availableToppings.addAll(toppings);
                        tvToppingsEmpty.setVisibility(availableToppings.isEmpty() ? View.VISIBLE : View.GONE);
                        rvToppings.setVisibility(availableToppings.isEmpty() ? View.GONE : View.VISIBLE);
                        toppingAdapter.submitList(availableToppings, selectedToppings);
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        tvToppingsEmpty.setVisibility(View.VISIBLE);
                        rvToppings.setVisibility(View.GONE);
                    }
                });
            }
        }

        private void updateProductOption() {
            if (savedPosition >= 0 && savedPosition < productOptions.size()) {
                ComboProductOption option = productOptions.get(savedPosition);
                option.setToppings(new ArrayList<>(selectedToppings));
                
                // Thông báo cho listener khi topping thay đổi
                if (onToppingChangeListener != null) {
                    onToppingChangeListener.onToppingChanged();
                }
            }
        }
        
        // Helper để lưu position khi bind
        private int savedPosition = -1;
    }
}

