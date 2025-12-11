package com.pro.milkteaapp.activity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.databinding.UserActivityLoyaltyStatusBinding;
import com.pro.milkteaapp.fragment.bottomsheet.LoyaltyTierBottomSheet;
import com.pro.milkteaapp.repository.UsersRepository;
import com.pro.milkteaapp.utils.LoyaltyPolicy;

import java.util.List;

/**
 * LoyaltyStatusActivity – đồng bộ với LoyaltyTierBottomSheet
 * Chuẩn hoá USRxxxxx và giảm 15% cho hạng Vàng.
 */
public class LoyaltyStatusActivity extends AppCompatActivity {

    private UserActivityLoyaltyStatusBinding b;
    private UsersRepository usersRepository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = UserActivityLoyaltyStatusBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        setSupportActionBar(b.toolbar);
        b.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        // Set title vào TextView custom để căn giữa
        android.widget.TextView titleView = b.toolbar.findViewById(R.id.toolbarTitle);
        if (titleView != null) {
            titleView.setText("THÀNH VIÊN");
        } else {
            b.toolbar.setTitle("THÀNH VIÊN");
        }

        usersRepository = new UsersRepository();

        b.btnViewAllTiers.setOnClickListener(v -> {
            LoyaltyTierBottomSheet sheet = new LoyaltyTierBottomSheet();
            sheet.show(getSupportFragmentManager(), "LoyaltyTierBottomSheet");
        });

        loadUser();
    }
    

    @SuppressLint("SetTextI18n")
    private void loadUser() {
        String docId = null;
        try {
            docId = new SessionManager(getApplicationContext()).getUid();
        } catch (Throwable e) {
            android.util.Log.w("LoyaltyStatus", "Failed to get UID from SessionManager: " + e.getMessage());
        }

        if (TextUtils.isEmpty(docId)) {
            Toast.makeText(this, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        b.progress.setVisibility(View.VISIBLE);

        String finalDocId = docId;
        usersRepository.fetchUserById(docId, new UsersRepository.OnUserListener() {
            @Override
            public void onUserReceived(@NonNull com.pro.milkteaapp.models.User user) {
                b.progress.setVisibility(View.GONE);

                long points = user.getLoyaltyPoints();
                String tier = user.getLoyaltyTier();
                if (tier == null || tier.trim().isEmpty()) {
                    tier = LoyaltyPolicy.tierForPoints(points);
                    // Đồng bộ tier nếu chưa có
                    usersRepository.syncLoyaltyTier(finalDocId, new UsersRepository.OnUserUpdateListener() {
                        @Override
                        public void onSuccess() {
                            // Tier đã được đồng bộ
                        }

                        @Override
                        public void onError(@NonNull Exception error) {
                            // Log silently
                        }
                    });
                }

                b.tvCurrentTier.setText(tier);
                b.tvCurrentPoints.setText(String.valueOf(points));

                List<String> benefits = LoyaltyPolicy.benefitsForTier(tier);
                b.tvBenefits.setText("• " + android.text.TextUtils.join("\n• ", benefits));

                LoyaltyPolicy.NextTierInfo nxt = LoyaltyPolicy.nextTier(points);
                if (nxt.targetTier == null) {
                    b.tvNextTier.setText("Bạn đang ở hạng cao nhất.");
                    b.tvNextBenefits.setText("• " + android.text.TextUtils.join("\n• ",
                            LoyaltyPolicy.benefitsForTier(tier)));
                } else {
                    b.tvNextTier.setText("Còn " + nxt.remainingPoints + " điểm để lên " + nxt.targetTier);
                    List<String> nextBenefits = (nxt.nextTierBenefits != null && !nxt.nextTierBenefits.isEmpty())
                            ? nxt.nextTierBenefits
                            : LoyaltyPolicy.benefitsForTier(nxt.targetTier);
                    b.tvNextBenefits.setText("• " + android.text.TextUtils.join("\n• ", nextBenefits));
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                b.progress.setVisibility(View.GONE);
                Toast.makeText(LoyaltyStatusActivity.this, "Lỗi: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
