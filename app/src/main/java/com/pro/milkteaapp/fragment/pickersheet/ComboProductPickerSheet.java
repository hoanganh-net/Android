package com.pro.milkteaapp.fragment.pickersheet;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.widget.ArrayAdapter;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.pro.milkteaapp.adapter.ProductSelectAdapter;
import com.pro.milkteaapp.databinding.BottomsheetComboProductPickerBinding;
import com.pro.milkteaapp.models.Category;
import com.pro.milkteaapp.models.Product;
import com.pro.milkteaapp.repository.CategoriesRepository;
import com.pro.milkteaapp.repository.ProductRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * BottomSheet để chọn sản phẩm cho Combo
 * Chỉ hiển thị sản phẩm active
 * Hỗ trợ chọn nhiều sản phẩm
 */
public class ComboProductPickerSheet extends BottomSheetDialogFragment {

    public interface OnProductSelectedListener {
        void onProductSelected(@NonNull List<Product> selectedProduct);
    }

    private BottomsheetComboProductPickerBinding binding;
    private OnProductSelectedListener listener;
    private ProductRepository productsRepository;
    private CategoriesRepository categoriesRepository;
    private ProductSelectAdapter adapter;
    private final Set<String> selectedProductIds = new HashSet<>();
    private final List<Product> allProduct = new ArrayList<>();
    private final List<Product> filteredProduct = new ArrayList<>();
    private final List<Product> selectedProduct = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();
    private ArrayAdapter<String> categoryAdapter;
    private String selectedCategory = "Tất cả";

    public void setListener(OnProductSelectedListener listener) {
        this.listener = listener;
    }

    /**
     * Set các sản phẩm đã được chọn trước đó (dùng khi edit combo)
     */
    public void setPreselectedProductIds(@NonNull List<String> productIds) {
        selectedProductIds.clear();
        selectedProductIds.addAll(productIds);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomsheetComboProductPickerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        productsRepository = new ProductRepository();
        categoriesRepository = new CategoriesRepository();

        // Setup RecyclerView
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ProductSelectAdapter(requireContext());
        adapter.setToggleListener((product, isSelected) -> {
            String productId = product.getId();
            if (isSelected) {
                selectedProductIds.add(productId);
                if (!selectedProduct.contains(product)) {
                    selectedProduct.add(product);
                }
            } else {
                selectedProductIds.remove(productId);
                selectedProduct.removeIf(p -> productId.equals(p.getId()));
            }
            updateSelectedCount();
        });
        binding.recyclerView.setAdapter(adapter);

        // Setup Category Dropdown
        categories.add("Tất cả");
        categoryAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, categories);
        binding.actCategory.setAdapter(categoryAdapter);
        binding.actCategory.setText("Tất cả", false);
        binding.actCategory.setOnItemClickListener((parent, itemView, position, id) -> {
            String category = categories.get(position);
            selectedCategory = category;
            filterProductByCategory(category);
        });

        // Nút xác nhận
        binding.btnConfirm.setOnClickListener(v -> {
            if (selectedProduct.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng chọn ít nhất một sản phẩm", Toast.LENGTH_SHORT).show();
                return;
            }
            if (listener != null) {
                listener.onProductSelected(new ArrayList<>(selectedProduct));
            }
            dismiss();
        });

        // Nút hủy
        binding.btnCancel.setOnClickListener(v -> dismiss());

        // Load categories và sản phẩm
        loadCategories();
        loadActiveProduct();
    }

    private void loadCategories() {
        categoriesRepository.fetchActiveCategories(new CategoriesRepository.OnCategoriesListener() {
            @Override
            public void onCategoriesReceived(@NonNull List<Category> categoryList) {
                categories.clear();
                categories.add("Tất cả");
                for (Category cat : categoryList) {
                    if (cat.getName() != null && !cat.getName().trim().isEmpty()) {
                        categories.add(cat.getName());
                    }
                }
                if (categoryAdapter != null) {
                    categoryAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                // Không hiển thị lỗi, chỉ dùng "Tất cả"
            }
        });
    }

    private void loadActiveProduct() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.recyclerView.setVisibility(View.GONE);

        productsRepository.fetchProductByStatus(
                com.pro.milkteaapp.constants.FirestoreConstants.STATUS_ACTIVE,
                new ProductRepository.OnProductListener() {
                    @Override
                    public void onProductReceived(@NonNull List<Product> products) {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.recyclerView.setVisibility(View.VISIBLE);

                        allProduct.clear();
                        allProduct.addAll(products);

                        // Cập nhật selectedProduct dựa trên selectedProductIds
                        selectedProduct.clear();
                        for (Product product : products) {
                            if (selectedProductIds.contains(product.getId())) {
                                selectedProduct.add(product);
                            }
                        }

                        // Filter theo category hiện tại
                        filterProductByCategory(selectedCategory);
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(requireContext(), "Lỗi tải sản phẩm: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void filterProductByCategory(String category) {
        if (category == null || category.equals("Tất cả")) {
            filteredProduct.clear();
            filteredProduct.addAll(allProduct);
        } else {
            filteredProduct.clear();
            for (Product product : allProduct) {
                String productCategory = product.getCategory();
                if (category.equals(productCategory)) {
                    filteredProduct.add(product);
                }
            }
        }

        adapter.submitList(filteredProduct);
        adapter.setSelectedProductIds(selectedProductIds);
        updateSelectedCount();
    }


    @SuppressLint("SetTextI18n")
    private void updateSelectedCount() {
        int count = selectedProduct.size();
        binding.tvSelectedCount.setText("Đã chọn: " + count + " sản phẩm");
        binding.btnConfirm.setEnabled(count > 0);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Set chiều cao bottom sheet = 8/10 màn hình
        View view = getDialog() != null ? getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet) : null;
        if (view != null) {
            view.getLayoutParams().height = (int) (getResources().getDisplayMetrics().heightPixels * 0.8f);
            view.requestLayout();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

