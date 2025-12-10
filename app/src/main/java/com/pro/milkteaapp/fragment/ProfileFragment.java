package com.pro.milkteaapp.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import com.google.android.material.navigation.NavigationView;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.android.material.card.MaterialCardView;
import com.pro.milkteaapp.models.User;
import com.pro.milkteaapp.repository.UsersRepository;

import android.widget.ImageView;
import android.widget.TextView;

import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.activity.AddressActivity;
import com.pro.milkteaapp.activity.ChangePasswordActivity;
import com.pro.milkteaapp.activity.EditProfileActivity;
import com.pro.milkteaapp.activity.LoginActivity;
import com.pro.milkteaapp.activity.OrderHistoryActivity;
import com.pro.milkteaapp.activity.admin.AdminMainActivity;
import com.pro.milkteaapp.activity.LoyaltyStatusActivity;
import com.pro.milkteaapp.databinding.ActivityProfileBinding;
import com.pro.milkteaapp.utils.ImageLoader;
import com.pro.milkteaapp.utils.TierStyleUtils;
import com.pro.milkteaapp.utils.LoyaltyPolicy;

public class ProfileFragment extends Fragment {

    private ActivityProfileBinding binding;
    private FirebaseAuth auth;
    private UsersRepository usersRepository;
    private SessionManager session;
    private ListenerRegistration profileListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = ActivityProfileBinding.inflate(inflater, container, false);

        // Xóa nút back - không cần navigation icon

        auth = FirebaseAuth.getInstance();
        // Vẫn cần cho realtime listener
        FirebaseFirestore db = FirebaseFirestore.getInstance(); // Vẫn cần cho realtime listener
        usersRepository = new UsersRepository();
        session = new SessionManager(requireContext());

        if (auth.getCurrentUser() == null) {
            gotoLogin();
            return binding.getRoot();
        }

        // Nút chức năng chính
        binding.orderHistoryLayout.setOnClickListener(
                v -> startActivity(new Intent(requireContext(), OrderHistoryActivity.class)));
        binding.btnEditProfile.setOnClickListener(
                v -> startActivity(new Intent(requireContext(), EditProfileActivity.class)));
        binding.btnChangePassword.setOnClickListener(
                v -> startActivity(new Intent(requireContext(), ChangePasswordActivity.class)));
        binding.btnManageAddress.setOnClickListener(
                v -> startActivity(new Intent(requireContext(), AddressActivity.class)));


        binding.btnAdminPanel.setOnClickListener(
                v -> startActivity(new Intent(requireContext(), AdminMainActivity.class)));

        binding.btnLogout.setOnClickListener(v -> {
            try { auth.signOut(); } catch (Throwable ignored) {}
            session.clear();
            Toast.makeText(requireContext(), getString(R.string.logged_out_successfully), Toast.LENGTH_SHORT).show();
            gotoLogin();
        });

        // ✅ Toàn bộ card loyalty có thể click được
        View.OnClickListener openLoyaltyStatus = v -> 
                startActivity(new Intent(requireContext(), LoyaltyStatusActivity.class));
        
        binding.cardLoyalty.setOnClickListener(openLoyaltyStatus);
        binding.btnViewMember.setOnClickListener(openLoyaltyStatus);

        // ❌ Bỏ click cho badge và lottie để tránh conflict
        View vBadge = binding.getRoot().findViewById(R.id.tvTierBadge);
        if (vBadge != null) {
            vBadge.setClickable(false);
            vBadge.setFocusable(false);
        }
        View vLottie = binding.getRoot().findViewById(R.id.lottieCrown);
        if (vLottie != null) {
            vLottie.setClickable(false);
            vLottie.setFocusable(false);
        }

        setupToolbar();
        setupDrawer();
        startProfileRealtime();
        return binding.getRoot();
    }
    
    private void setupToolbar() {
        if (getActivity() == null || binding == null) return;
        ((androidx.appcompat.app.AppCompatActivity) getActivity()).setSupportActionBar(binding.toolbar);
        
        // Setup drawer button click
        View btnDrawer = binding.getRoot().findViewById(R.id.btnDrawer);
        if (btnDrawer != null) {
            btnDrawer.setOnClickListener(v -> {
                if (binding.drawerLayout != null) {
                    binding.drawerLayout.openDrawer(GravityCompat.END);
                }
            });
        }
    }
    
    private void setupDrawer() {
        if (binding == null) return;
        
        // Setup drawer menu click
        if (binding.navigationView != null) {
            binding.navigationView.setNavigationItemSelectedListener(item -> {
                int itemId = item.getItemId();
                
                if (itemId == R.id.menu_profile_info) {
                    // Scroll to profile info section
                    if (binding.scroll != null) {
                        binding.scroll.post(() -> {
                            View contentGroup = binding.getRoot().findViewById(R.id.contentGroup);
                            if (contentGroup != null) {
                                binding.scroll.smoothScrollTo(0, contentGroup.getTop());
                            }
                        });
                    }
                } else if (itemId == R.id.menu_order_history) {
                    startActivity(new Intent(requireContext(), OrderHistoryActivity.class));
                } else if (itemId == R.id.menu_address) {
                    startActivity(new Intent(requireContext(), AddressActivity.class));
                } else if (itemId == R.id.menu_loyalty) {
                    startActivity(new Intent(requireContext(), LoyaltyStatusActivity.class));
                }
                
                if (binding.drawerLayout != null) {
                    binding.drawerLayout.closeDrawer(GravityCompat.END);
                }
                return true;
            });
        }
    }

    private void startProfileRealtime() {
        FirebaseUser fUser = auth.getCurrentUser();
        if (fUser == null) { gotoLogin(); return; }

        showLoading(true);
        stopProfileRealtime();

        String docId = session.getUid();
        if (!TextUtils.isEmpty(docId)) {
            profileListener = listenUserDoc(docId);
            return;
        }

        String email = fUser.getEmail();
        if (!TextUtils.isEmpty(email)) {
            usersRepository.fetchUserByEmail(email, new UsersRepository.OnUserListener() {
                @Override
                public void onUserReceived(@NonNull com.pro.milkteaapp.models.User user) {
                    showLoading(false);
                    session.saveUserFromFirestore(
                            user.getUid(),
                            user.getEmail(),
                            user.getFullName(),
                            user.getRole(),
                            user.getAvatar()
                    );
                    bindProfileFromUser(user);
                    profileListener = listenUserDoc(user.getUid());
                }

                @Override
                public void onError(@NonNull Exception error) {
                    showLoading(false);
                    Toast.makeText(requireContext(), "Lỗi tải hồ sơ: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            profileListener = listenUserDoc(fUser.getUid());
        }
    }

    private ListenerRegistration listenUserDoc(@NonNull String docId) {
        // Sử dụng UsersRepository để lắng nghe realtime user updates
        return usersRepository.listenUserById(docId, new UsersRepository.OnUserListener() {
            @Override
            public void onUserReceived(@NonNull User user) {
                if (!isAdded()) return;
                showLoading(false);
                bindProfileFromUser(user);
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (!isAdded()) return;
                showLoading(false);
                Toast.makeText(requireContext(), "Lỗi tải hồ sơ: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void stopProfileRealtime() {
        if (profileListener != null) {
            profileListener.remove();
            profileListener = null;
        }
    }

    private void bindProfileFromUser(@NonNull com.pro.milkteaapp.models.User user) {
        String fullName = user.getFullName() != null ? user.getFullName() : "";
        String email = user.getEmail() != null ? user.getEmail() : "";
        String role = user.getRole() != null ? user.getRole() : session.getRole();
        String phone = user.getPhone() != null ? user.getPhone() : "";
        String address = user.getAddress() != null ? user.getAddress() : "";
        String avatar = user.getAvatar();

        long loyaltyPoints = user.getLoyaltyPoints();
        String loyaltyTier = user.getLoyaltyTier();
        if (TextUtils.isEmpty(loyaltyTier)) {
            loyaltyTier = LoyaltyPolicy.tierForPoints(loyaltyPoints);
        }

        String userDocId = user.getUid();
        reconcileTierIfNeeded(userDocId, loyaltyPoints, loyaltyTier);

        binding.tvName.setText(TextUtils.isEmpty(fullName) ? getString(R.string.unknown) : fullName);
        binding.tvEmail.setText(TextUtils.isEmpty(email) ? getString(R.string.unknown) : email);
        binding.tvRole.setText(TextUtils.isEmpty(role) ? "user" : role);
        binding.tvPhone.setText(TextUtils.isEmpty(phone) ? getString(R.string.unknown) : phone);
        binding.tvAddress.setText(TextUtils.isEmpty(address) ? getString(R.string.unknown) : address);

        TextView tvBadge = binding.getRoot().findViewById(R.id.tvTierBadge);
        if (tvBadge != null) tvBadge.setText(loyaltyTier);
        binding.tvLoyaltyPoints.setText(String.valueOf(loyaltyPoints));
        
        // Hiển thị điểm đổi quà
        long rewardPoints = user.getRewardPoints();
        TextView tvRewardPoints = binding.getRoot().findViewById(R.id.tvRewardPoints);
        if (tvRewardPoints != null) {
            tvRewardPoints.setText(String.valueOf(rewardPoints));
        }
        
        // Setup nút đổi voucher
        setupRedeemButton();

        ImageLoader.load(binding.imgAvatar, avatar, R.drawable.ic_avatar_default);

        boolean isAdmin = "admin".equalsIgnoreCase(role);
        binding.btnAdminPanel.setVisibility(isAdmin ? View.VISIBLE : View.GONE);

        applyTierVisual(loyaltyTier);
    }

    private void bindProfile(@Nullable DocumentSnapshot snap) {
        String fullName = "";
        String email = "";
        String role = session.getRole();
        String phone = "";
        String address = "";
        String avatar = null;

        long loyaltyPoints = 0L;
        String loyaltyTier = "Chưa xếp hạng";

        String userDocId = snap != null ? snap.getId() : session.getUid();

        if (snap != null && snap.exists()) {
            if (snap.getString("fullName") != null) fullName = snap.getString("fullName");
            if (snap.getString("email") != null) email = snap.getString("email");
            if (snap.getString("role") != null) role = snap.getString("role");
            if (snap.getString("phone") != null) phone = snap.getString("phone");
            if (snap.getString("address") != null) address = snap.getString("address");
            if (snap.getString("avatar") != null) avatar = snap.getString("avatar");

            if (snap.contains("loyaltyPoints")) {
                Long lp = snap.getLong("loyaltyPoints");
                if (lp != null) loyaltyPoints = lp;
            }

            String tierInDoc = snap.getString("loyaltyTier");
            if (!TextUtils.isEmpty(tierInDoc)) {
                loyaltyTier = tierInDoc;
            } else {
                loyaltyTier = LoyaltyPolicy.tierForPoints(loyaltyPoints);
            }

            session.saveUserFromFirestore(
                    snap.getId(), email, fullName, role, avatar
            );
        }

        reconcileTierIfNeeded(userDocId, loyaltyPoints, loyaltyTier);

        binding.tvName.setText(TextUtils.isEmpty(fullName) ? getString(R.string.unknown) : fullName);
        binding.tvEmail.setText(TextUtils.isEmpty(email) ? getString(R.string.unknown) : email);
        binding.tvRole.setText(TextUtils.isEmpty(role) ? "user" : role);
        binding.tvPhone.setText(TextUtils.isEmpty(phone) ? getString(R.string.unknown) : phone);
        binding.tvAddress.setText(TextUtils.isEmpty(address) ? getString(R.string.unknown) : address);

        TextView tvBadge = binding.getRoot().findViewById(R.id.tvTierBadge);
        if (tvBadge != null) tvBadge.setText(loyaltyTier);
        binding.tvLoyaltyPoints.setText(String.valueOf(loyaltyPoints));
        
        // Hiển thị điểm đổi quà
        long rewardPoints = 0L;
        if (snap != null && snap.contains("rewardPoints")) {
            Long rp = snap.getLong("rewardPoints");
            if (rp != null) rewardPoints = rp;
        }
        TextView tvRewardPoints = binding.getRoot().findViewById(R.id.tvRewardPoints);
        if (tvRewardPoints != null) {
            tvRewardPoints.setText(String.valueOf(rewardPoints));
        }
        
        // Setup nút đổi voucher
        setupRedeemButton();

        ImageLoader.load(binding.imgAvatar, avatar, R.drawable.ic_avatar_default);

        boolean isAdmin = "admin".equalsIgnoreCase(role);
        binding.btnAdminPanel.setVisibility(isAdmin ? View.VISIBLE : View.GONE);

        applyTierVisual(loyaltyTier);
    }
    
    private void setupRedeemButton() {
        com.google.android.material.button.MaterialButton btnRedeem = 
                binding.getRoot().findViewById(R.id.btnRedeemVoucher);
        if (btnRedeem != null) {
            btnRedeem.setOnClickListener(v -> {
                // Mở màn hình đổi voucher
                startActivity(new Intent(requireContext(), 
                        com.pro.milkteaapp.activity.RedeemVoucherActivity.class));
            });
        }
    }

    private void reconcileTierIfNeeded(@Nullable String userDocId, long points, @Nullable String currentTier) {
        if (TextUtils.isEmpty(userDocId)) return;
        String expected = LoyaltyPolicy.tierForPoints(points);
        String cur = (currentTier == null || currentTier.isEmpty()) ? "" : currentTier.trim();
        // So sánh không phân biệt hoa thường và chuẩn hóa
        String normalizedExpected = expected != null ? expected.trim() : "";
        String normalizedCur = cur != null ? cur.trim() : "";
        if (!normalizedExpected.equalsIgnoreCase(normalizedCur)) {
            // Cập nhật tier ngay lập tức trong UI
            TextView tvBadge = binding.getRoot().findViewById(R.id.tvTierBadge);
            if (tvBadge != null) {
                tvBadge.setText(expected);
            }
            applyTierVisual(expected);
            
            // Sử dụng UsersRepository để đồng bộ tier trong Firestore
            if (usersRepository == null) {
                usersRepository = new UsersRepository();
            }
            usersRepository.syncLoyaltyTier(userDocId, new UsersRepository.OnUserUpdateListener() {
                @Override
                public void onSuccess() {
                    // Tier đã được đồng bộ thành công
                }

                @Override
                public void onError(@NonNull Exception error) {
                    // Log silently, không chặn UI
                    android.util.Log.w("ProfileFragment", "Lỗi đồng bộ tier: " + error.getMessage());
                }
            });
        }
    }

    private void applyTierVisual(@NonNull String tier) {
        MaterialCardView card = binding.cardLoyalty;
        TextView tvBadge = binding.getRoot().findViewById(R.id.tvTierBadge);

        TierStyleUtils.apply(requireContext(), tier, card, null, tvBadge, tvBadge);

        // Lottie chỉ để trang trí
        View vLottie = binding.getRoot().findViewById(R.id.lottieCrown);
        if (vLottie instanceof LottieAnimationView lottie) {
            int resId = getCrownResForTier(tier);
            lottie.cancelAnimation();
            if (resId != 0) {
                lottie.setAnimation(resId);
                lottie.setRepeatCount(LottieDrawable.INFINITE);
                lottie.playAnimation();
                lottie.setVisibility(View.VISIBLE);
            } else {
                lottie.setVisibility(View.GONE);
            }
        }
    }

    private int getCrownResForTier(@NonNull String tier) {
        if ("Kim Cương".equals(tier)) {
            return R.raw.crown_diamond;
        }
        String t = tier.trim().toLowerCase();
        return switch (t) {
            case "vàng", "gold" -> R.raw.crown_gold;
            case "bạc", "silver" -> R.raw.crown_diamond;
            case "đồng", "dong", "bronze" -> R.raw.crown_bronze;
            default -> 0;
        };
    }

    private void showLoading(boolean show) {
        if (binding == null) return;
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.contentGroup.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void gotoLogin() {
        if (getActivity() == null) return;
        Intent i = new Intent(requireContext(), LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        getActivity().finish();
    }

    @Override
    public void onResume() {
        super.onResume();
        startProfileRealtime(); // Tự reload mỗi lần quay lại
    }

    @Override
    public void onPause() {
        stopProfileRealtime();
        super.onPause();
    }
}
