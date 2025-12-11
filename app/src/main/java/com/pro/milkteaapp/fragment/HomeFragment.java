package com.pro.milkteaapp.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.airbnb.lottie.LottieAnimationView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.ComboAdapter;
import com.pro.milkteaapp.adapter.ProductSectionAdapter;
import com.pro.milkteaapp.adapter.ProductSmallAdapter;
import com.pro.milkteaapp.adapter.ReviewAdapter;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.constants.FirestoreConstants;
import com.pro.milkteaapp.fragment.bottomsheet.ComboDetailBottomSheet;
import com.pro.milkteaapp.models.Combo;
import com.pro.milkteaapp.models.ComboCartItem;
import com.pro.milkteaapp.models.Product;
import com.pro.milkteaapp.models.Review;
import com.pro.milkteaapp.repository.ComboRepository;
import com.pro.milkteaapp.repository.ProductRepository;
import com.pro.milkteaapp.repository.UsersRepository;
import com.pro.milkteaapp.utils.ImageLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class HomeFragment extends Fragment {

    private RecyclerView rvTopPrice, rvReviews, recyclerViewCombo;
    private LottieAnimationView lavSeeMore;   // nút animation đỏ
    private android.widget.TextView textGreeting;
    private de.hdodenhof.circleimageview.CircleImageView profileIcon;
    private android.view.View comboSection;

    private ProductSectionAdapter topPriceAdapter;
    private ReviewAdapter reviewAdapter;
    private ComboAdapter comboAdapter;

    private final List<Product> topProduct = new ArrayList<>();
    private final List<Review> reviews = new ArrayList<>();
    private final List<Combo> combos = new ArrayList<>();

    private ProductRepository productsRepository;
    private ComboRepository comboRepository;
    private UsersRepository usersRepository;
    private FirebaseFirestore db; // Vẫn cần cho query orders có rating
    private com.google.firebase.firestore.ListenerRegistration userListener;

    public HomeFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.user_fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        productsRepository = new ProductRepository();
        comboRepository = new ComboRepository();
        usersRepository = new UsersRepository();
        db = FirebaseFirestore.getInstance(); // Vẫn cần cho query orders có rating
        
        initViews(v);
        setupTopProduct();
        setupComboSection();
        setupReviewList();
        setupSeeMoreButton();   // bấm mũi tên đỏ → qua trang sản phẩm
        greetUser();
        setupProfileIconClickListener();
    }

    @Override
    public void onStart() {
        super.onStart();
        // Load reviews khi fragment start
        if (reviewAdapter != null && reviews.isEmpty()) {
            loadRecentOrderReviews();
        }
        // Reload featured products khi fragment start (giống AdminOrdersFragment)
        if (topPriceAdapter != null) {
            loadFeaturedProduct();
        }
        // Combo đã được load trong setupComboSection(), không cần reload ở đây
    }

    private void initViews(@NonNull View v) {
        rvTopPrice = v.findViewById(R.id.rvTopPrice);
        rvReviews = v.findViewById(R.id.rvReviews);
        lavSeeMore = v.findViewById(R.id.lavSeeMore);   // có trong XML mới
        textGreeting = v.findViewById(R.id.textGreeting);
        profileIcon = v.findViewById(R.id.profileIcon);
        recyclerViewCombo = v.findViewById(R.id.recyclerViewCombo);
        comboSection = v.findViewById(R.id.comboSection);
    }

    // ===== Combo Section =====
    private void setupComboSection() {
        if (recyclerViewCombo == null) {
            android.util.Log.e("HomeFragment", "recyclerViewCombo is null!");
            return;
        }
        // Đảm bảo combo section hiển thị ngay từ đầu (sẽ ẩn nếu không có data)
        if (comboSection != null) {
            comboSection.setVisibility(View.VISIBLE);
        }
        // Sử dụng GridLayoutManager 2 cột giống sản phẩm nổi bật
        GridLayoutManager glm = new GridLayoutManager(requireContext(), 2);
        recyclerViewCombo.setLayoutManager(glm);
        recyclerViewCombo.setHasFixedSize(false); // Không dùng fixed size với wrap_content
        recyclerViewCombo.setNestedScrollingEnabled(false);
        recyclerViewCombo.setOverScrollMode(View.OVER_SCROLL_NEVER);
        
        comboAdapter = new ComboAdapter(requireContext(), combo -> {
            if (combo != null) {
                ComboDetailBottomSheet sheet = ComboDetailBottomSheet.newInstance(combo);
                sheet.setListener((c, qty, productOptions) -> {
                    // Thêm combo vào giỏ hàng với các tùy chọn sản phẩm (mỗi sản phẩm có size riêng)
                    ComboCartItem newItem = new ComboCartItem(c, qty, productOptions);
                    
                    // Kiểm tra xem combo đã có trong giỏ chưa (theo comboId và size)
                    com.pro.milkteaapp.utils.CartManager cartManager = com.pro.milkteaapp.utils.CartManager.getInstance();
                    int found = cartManager.findComboCartItem(newItem);
                    if (found >= 0) {
                        cartManager.getComboCartItems().get(found).increaseQuantity(qty);
                    } else {
                        cartManager.addComboCartItem(newItem);
                    }
                    
                    android.widget.Toast.makeText(requireContext(),
                            "Đã thêm " + qty + " " + c.getName() + " vào giỏ hàng",
                            android.widget.Toast.LENGTH_SHORT).show();
                });
                sheet.show(getParentFragmentManager(), "combo_detail_sheet");
            }
        });
        recyclerViewCombo.setAdapter(comboAdapter);
        
        // Load combos ngay sau khi setup (giống sản phẩm nổi bật)
        loadCombos();
        
        android.util.Log.d("HomeFragment", "setupComboSection completed");
    }

    private void loadCombos() {
        if (comboRepository == null) {
            android.util.Log.e("HomeFragment", "comboRepository is null!");
            return;
        }
        if (comboAdapter == null) {
            android.util.Log.e("HomeFragment", "comboAdapter is null! setupComboSection() may not have been called.");
            return;
        }
        android.util.Log.d("HomeFragment", "Loading combos...");
        comboRepository.fetchActiveCombos(new ComboRepository.OnCombosListener() {
            @Override
            public void onCombosReceived(@NonNull List<Combo> comboList) {
                android.util.Log.d("HomeFragment", "Received " + comboList.size() + " combos");
                if (!isAdded() || getContext() == null) {
                    android.util.Log.w("HomeFragment", "Fragment not attached, ignoring combo update");
                    return;
                }
                combos.clear();
                combos.addAll(comboList);
                android.util.Log.d("HomeFragment", "Combos list size: " + combos.size());
                
                // Update UI (giống sản phẩm nổi bật) - đảm bảo chạy trên UI thread
                if (!isAdded() || getContext() == null) {
                    android.util.Log.w("HomeFragment", "Fragment not attached, skipping UI update");
                    return;
                }
                
                // Đảm bảo chạy trên UI thread
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (comboAdapter != null && recyclerViewCombo != null) {
                            android.util.Log.d("HomeFragment", "Submitting " + combos.size() + " combos to adapter");
                            comboAdapter.submitList(new ArrayList<>(combos));
                            android.util.Log.d("HomeFragment", "Adapter item count: " + comboAdapter.getItemCount());
                            // Force layout để đảm bảo RecyclerView được measure lại
                            recyclerViewCombo.post(() -> {
                                recyclerViewCombo.requestLayout();
                                android.util.Log.d("HomeFragment", "RecyclerView requested layout, visibility: " + recyclerViewCombo.getVisibility());
                            });
                        } else {
                            android.util.Log.e("HomeFragment", "comboAdapter or recyclerViewCombo is null!");
                        }
                        // Ẩn/hiện combo section
                        if (comboSection != null) {
                            comboSection.setVisibility(combos.isEmpty() ? View.GONE : View.VISIBLE);
                            android.util.Log.d("HomeFragment", "Combo section visibility: " + (combos.isEmpty() ? "GONE" : "VISIBLE"));
                        }
                    });
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                android.util.Log.e("HomeFragment", "Error loading combos: " + error.getMessage(), error);
                error.printStackTrace();
                // Vẫn hiển thị combo section ngay cả khi có lỗi, để user biết có section này
                // Chỉ ẩn nếu thực sự không có combo
                if (comboSection != null && combos.isEmpty()) {
                    comboSection.setVisibility(View.GONE);
                }
            }
        });
    }

    // ===== Sản phẩm nổi bật =====
    private void setupTopProduct() {
        // Sử dụng GridLayoutManager 2 cột giống ProductFragment
        GridLayoutManager glm = new GridLayoutManager(requireContext(), 2);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                // Tất cả items đều là product (không có header), nên span = 1
                return 1;
            }
        });
        rvTopPrice.setLayoutManager(glm);
        rvTopPrice.setHasFixedSize(true);
        rvTopPrice.setNestedScrollingEnabled(false);

        topPriceAdapter = new ProductSectionAdapter(new ProductSectionAdapter.OnProductClick() {
            @Override
            public void onClick(Product milkTea) {
                if (milkTea == null || !isAdded()) return;
                // Mở ProductDetailBottomSheet để đặt hàng
                com.pro.milkteaapp.fragment.bottomsheet.ProductDetailBottomSheet bottomSheet =
                        com.pro.milkteaapp.fragment.bottomsheet.ProductDetailBottomSheet.newInstance(milkTea);
                bottomSheet.setListener((product, qty, size, sugar, ice, note, toppings) -> {
                    // Thêm vào giỏ hàng (giống ProductFragment)
                    com.pro.milkteaapp.models.CartItem newItem = 
                            new com.pro.milkteaapp.models.CartItem(product, qty, size, sugar, ice, note, toppings);
                    
                    com.pro.milkteaapp.utils.CartManager cartManager = com.pro.milkteaapp.utils.CartManager.getInstance();
                    int existed = cartManager.findCartItem(newItem);
                    if (existed >= 0) {
                        cartManager.getCartItems().get(existed).increaseQuantity(qty);
                    } else {
                        cartManager.addCartItem(newItem);
                    }
                    
                    String label = newItem.getToppingsLabel();
                    android.widget.Toast.makeText(requireContext(),
                            "Đã thêm " + qty + " " + product.getName() + 
                            ("".equals(label) ? "" : " (" + label + ")"),
                            android.widget.Toast.LENGTH_SHORT).show();
                });
                bottomSheet.show(getParentFragmentManager(), "product_detail");
            }
        });
        rvTopPrice.setAdapter(topPriceAdapter);

        // Load featured products từ Firestore
        loadFeaturedProduct();
    }

    private void loadFeaturedProduct() {
        productsRepository.fetchFeaturedProduct(10, new ProductRepository.OnProductListener() {
            @Override
            public void onProductReceived(@NonNull List<Product> products) {
                android.util.Log.d("HomeFragment", "Loaded " + products.size() + " featured products");
                topProduct.clear();
                topProduct.addAll(products);
                
                // Chuyển đổi sang format của ProductSectionAdapter (list of Rows)
                List<ProductSectionAdapter.Row> rows = new ArrayList<>();
                for (Product p : topProduct) {
                    rows.add(ProductSectionAdapter.Row.product(p));
                }
                topPriceAdapter.submitRows(rows);
                
                // Nếu không có sản phẩm nổi bật, hiển thị demo data
                if (products.isEmpty()) {
                    android.util.Log.w("HomeFragment", "No featured products found, showing demo data");
                    loadDemoProduct();
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                android.util.Log.e("HomeFragment", "Error loading featured products: " + error.getMessage(), error);
                // Fallback: sử dụng demo data nếu load thất bại
                loadDemoProduct();
            }
        });
    }

    private void loadDemoProduct() {
        topProduct.clear();
        topProduct.add(new Product() {{
            setId("demo-5");
            setName("Trà Sữa Olong");
            setPrice(65000.0);
            setCategory("Trà Sữa");
            setImageUrl("milktea_olong");
            setDescription("Olong béo, hậu ngọt.");
            setSearchableName("tra sua oolong");
        }});
        topProduct.add(new Product() {{
            setId("demo-1");
            setName("Trà Sữa Trân Châu");
            setPrice(50000.0);
            setCategory("Trà Sữa");
            setImageUrl("milktea");
            setDescription("Truyền thống, đậm vị.");
            setSearchableName("tra sua tran chau");
        }});
        topProduct.add(new Product() {{
            setId("demo-2");
            setName("Trà Sữa Matcha");
            setPrice(46000.0);
            setCategory("Trà Sữa");
            setImageUrl("milktea_matcha");
            setDescription("Matcha thơm nhẹ, không ngấy.");
            setSearchableName("tra sua matcha");
        }});
        topProduct.add(new Product() {{
            setId("demo-3");
            setName("Trà Sữa Caramel");
            setPrice(48000.0);
            setCategory("Trà Sữa");
            setImageUrl("milktea_caramel");
            setDescription("Thơm caramel béo ngậy.");
            setSearchableName("tra sua caramel");
        }});
        topProduct.add(new Product() {{
            setId("demo-4");
            setName("Trà Sữa Socola");
            setPrice(42000.0);
            setCategory("Trà Sữa");
            setImageUrl("milktea_chocolate");
            setDescription("Đậm vị cacao.");
            setSearchableName("tra sua socola");
        }});
        
        // Chuyển đổi sang format của ProductSectionAdapter
        List<ProductSectionAdapter.Row> rows = new ArrayList<>();
        for (Product p : topProduct) {
            rows.add(ProductSectionAdapter.Row.product(p));
        }
        topPriceAdapter.submitRows(rows);
    }

    // ===== Reviews =====
    private void setupReviewList() {
        rvReviews.setLayoutManager(
                new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        rvReviews.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rvReviews.addItemDecoration(new SpacingDecoration(dp(8)));

        reviewAdapter = new ReviewAdapter(reviews);
        rvReviews.setAdapter(reviewAdapter);

        loadRecentOrderReviews();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void loadRecentOrderReviews() {
        final int LIMIT = 20;

        // Note: Vẫn giữ query trực tiếp vì cần DocumentSnapshot để enrich user info
        // Có thể refactor sau để OrdersRepository trả về Order objects đã được enrich
        Query q = db.collection(FirestoreConstants.COLLECTION_ORDERS)
                .whereGreaterThan("rating", 0)
                .orderBy("rating", Query.Direction.DESCENDING)
                .orderBy("ratedAt", Query.Direction.DESCENDING)
                .limit(LIMIT);

        q.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) return;

            List<DocumentSnapshot> docs = task.getResult().getDocuments();
            reviews.clear();

            // build list ban đầu
            for (DocumentSnapshot d : docs) {
                Number ratingNum = (Number) d.get("rating");
                if (ratingNum == null) continue;

                double stars = ratingNum.doubleValue();
                String comment = s(d.get("review"));
                reviews.add(new Review("Người dùng", comment, null, stars));
            }
            reviewAdapter.notifyDataSetChanged();

            // enrich tên user
            int idx = 0;
            for (DocumentSnapshot d : docs) {
                Number ratingNum = (Number) d.get("rating");
                if (ratingNum == null) continue;

                final int position = idx;
                final String comment = s(d.get("review"));
                final double stars = ((Number) Objects.requireNonNull(d.get("rating"))).doubleValue();
                final String userId = s(d.get("userId"));

                if (!TextUtils.isEmpty(userId)) {
                    usersRepository.fetchUserById(userId, new UsersRepository.OnUserListener() {
                        @Override
                        public void onUserReceived(@NonNull com.pro.milkteaapp.models.User user) {
                            String fullName = user.getFullName();
                            String email = user.getEmail();
                            String avatar = user.getAvatar();

                            String finalName = firstNonEmpty(
                                    fullName, emailPrefix(email), "Người dùng"
                            );

                            if (position >= 0 && position < reviews.size()) {
                                Review r = new Review(finalName, comment, avatar, stars);
                                reviews.set(position, r);
                                reviewAdapter.notifyItemChanged(position);
                            }
                        }

                        @Override
                        public void onError(@NonNull Exception error) {
                            // Nếu không load được user, giữ nguyên "Người dùng"
                        }
                    });
                }
                idx++;
            }
        });
    }

    // ===== Pull to refresh =====
    @SuppressLint("NotifyDataSetChanged")

    // ===== Nút Lottie đỏ “Xem thêm sản phẩm” =====
    private void setupSeeMoreButton() {
        if (lavSeeMore == null) return;
        lavSeeMore.setOnClickListener(v -> openProductTab());
    }

    private void openProductTab() {
        if (requireActivity() instanceof com.pro.milkteaapp.activity.MainActivity) {
            ((com.pro.milkteaapp.activity.MainActivity) requireActivity()).openProductsTab();
        }
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    // ===== Decor cho RecyclerView ngang =====
    public static class SpacingDecoration extends RecyclerView.ItemDecoration {
        private final int space;

        public SpacingDecoration(int space) {
            this.space = space;
        }

        @Override
        public void getItemOffsets(@NonNull android.graphics.Rect outRect,
                                   @NonNull View view,
                                   @NonNull RecyclerView parent,
                                   @NonNull RecyclerView.State state) {
            outRect.set(space, space, space, space);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // không còn lavCart nên không cần cancel animation
    }

    // ===== helpers =====
    private static String s(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String emailPrefix(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private static String firstNonEmpty(String... arr) {
        if (arr == null) return null;
        for (String s : arr) if (s != null && !s.trim().isEmpty()) return s.trim();
        return null;
    }

    /**
     * Chào user và hiển thị avatar
     */
    @SuppressLint("SetTextI18n")
    private void greetUser() {
        if (textGreeting == null || profileIcon == null) return;

        SessionManager session = new SessionManager(requireContext());
        String cachedName = session.getDisplayName();
        String cachedAvatar = session.getAvatar();
        String docId = session.getUid();

        // Hiển thị tên ngay từ session hoặc fallback
        String displayName = cachedName;
        if (TextUtils.isEmpty(displayName)) {
            // Fallback từ FirebaseAuth
            com.google.firebase.auth.FirebaseUser fUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (fUser != null) {
                displayName = fUser.getDisplayName();
                if (TextUtils.isEmpty(displayName)) {
                    displayName = fUser.getEmail();
                }
            }
            if (TextUtils.isEmpty(displayName)) {
                displayName = "Người dùng";
            }
        }
        textGreeting.setText(getString(R.string.greeting_with_name, displayName));

        // Avatar từ session (nếu có) → hiển thị ngay
        ImageLoader.load(profileIcon, cachedAvatar, R.drawable.ic_avatar_default);

        // Nếu session đã có docId → nghe realtime
        if (!TextUtils.isEmpty(docId)) {
            listenUserAvatarAndNameRealtime(docId, session);
            return;
        }

        // Nếu chưa có docId → query theo email lần đầu
        com.google.firebase.auth.FirebaseUser fUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (fUser != null) {
            String email = fUser.getEmail();
            if (!TextUtils.isEmpty(email)) {
                usersRepository.fetchUserByEmail(email, new UsersRepository.OnUserListener() {
                    @Override
                    public void onUserReceived(@NonNull com.pro.milkteaapp.models.User user) {
                        if (!isAdded() || textGreeting == null || profileIcon == null) return;
                        String id = user.getUid();
                        String fullName = user.getFullName();
                        String role = user.getRole();
                        String avatar = user.getAvatar();

                        // Lưu lại toàn bộ vào session
                        session.saveUserFromFirestore(id, email, fullName, role, avatar);

                        // Bind UI
                        String display = !TextUtils.isEmpty(fullName) ? fullName : email;
                        textGreeting.setText(getString(R.string.greeting_with_name, display));
                        ImageLoader.load(profileIcon, avatar, R.drawable.ic_avatar_default);

                        // Nghe realtime
                        listenUserAvatarAndNameRealtime(id, session);
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        android.util.Log.w("HomeFragment", "Failed to fetch user by email", error);
                    }
                });
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void listenUserAvatarAndNameRealtime(@NonNull String docId, @NonNull SessionManager session) {
        if (userListener != null) userListener.remove();
        userListener = usersRepository.listenUserById(docId, new UsersRepository.OnUserListener() {
            @Override
            public void onUserReceived(@NonNull com.pro.milkteaapp.models.User user) {
                if (!isAdded() || textGreeting == null || profileIcon == null) return;
                
                String fullName = user.getFullName() != null ? user.getFullName() : "";
                String avatar = user.getAvatar() != null ? user.getAvatar() : "";
                String email = user.getEmail() != null ? user.getEmail() : "";
                String role = user.getRole() != null ? user.getRole() : "";

                // Cập nhật UI
                String display = !TextUtils.isEmpty(fullName)
                        ? fullName
                        : (!TextUtils.isEmpty(email) ? email : "Người dùng");
                textGreeting.setText("Xin chào, " + display + "!");
                ImageLoader.load(profileIcon, avatar, R.drawable.ic_avatar_default);

                // Cập nhật session
                session.saveUserFromFirestore(docId, email, fullName, role, avatar);
            }

            @Override
            public void onError(@NonNull Exception error) {
                // Log error silently
            }
        });
    }

    private void setupProfileIconClickListener() {
        if (profileIcon == null) return;
        profileIcon.setOnClickListener(v -> {
            if (!isAdded()) return;
            // Chuyển bottom navigation sang tab profile
            if (requireActivity() instanceof com.pro.milkteaapp.activity.MainActivity) {
                ((com.pro.milkteaapp.activity.MainActivity) requireActivity())
                        .selectBottomNav(R.id.navigation_profile);
            }
        });
    }

    @Override
    public void onDestroyView() {
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
        super.onDestroyView();
    }
}
