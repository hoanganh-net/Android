package com.pro.milkteaapp.fragment.bottomsheet;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.repository.UsersRepository;
import com.pro.milkteaapp.utils.LoyaltyPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * BottomSheet hiển thị trạng thái & quyền lợi Loyalty (RULE RỜI).
 * - Dùng USRxxxxx từ SessionManager để load Firestore.
 * - Vàng giảm 15%.
 */
public class LoyaltyTierBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "LoyaltyTierBottomSheet";
    private static final String ARG_TIER = "arg_tier";
    private static final String ARG_POINTS = "arg_points";

    public static LoyaltyTierBottomSheet newInstance(@NonNull String tier, long points) {
        LoyaltyTierBottomSheet s = new LoyaltyTierBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_TIER, tier);
        args.putLong(ARG_POINTS, points);
        s.setArguments(args);
        return s;
    }

    public static void show(@NonNull androidx.fragment.app.FragmentManager fm) {
        LoyaltyTierBottomSheet s = new LoyaltyTierBottomSheet();
        s.show(fm, TAG);
    }

    private TextView tvCurrentTier, tvCurrentPoints, tvNextInfo;
    private TextView tvBenefitsCurrent, tvBenefitsNext;
    private TextView tvRuleUnrank, tvRuleBronze, tvRuleSilver, tvRuleGold, tvRuleDiamond;

    private long initialPoints = 0L;
    private boolean hasInitial = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            String initialTier = args.getString(ARG_TIER);
            initialPoints = args.getLong(ARG_POINTS, 0L);
            hasInitial = !TextUtils.isEmpty(initialTier);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inf.inflate(R.layout.bottomsheet_loyalty_tiers, container, false);

        tvCurrentTier     = v.findViewById(R.id.tvCurrentTier);
        tvCurrentPoints   = v.findViewById(R.id.tvCurrentPoints);
        tvNextInfo        = v.findViewById(R.id.tvNextInfo);
        tvBenefitsCurrent = v.findViewById(R.id.tvBenefitsCurrent);
        tvBenefitsNext    = v.findViewById(R.id.tvBenefitsNext);

        tvRuleUnrank = v.findViewById(R.id.tvRuleUnrank);
        tvRuleBronze = v.findViewById(R.id.tvRuleBronze);
        tvRuleSilver = v.findViewById(R.id.tvRuleSilver);
        tvRuleGold   = v.findViewById(R.id.tvRuleGold);
        tvRuleDiamond = v.findViewById(R.id.tvRuleDiamond);

        renderStaticRules();

        if (hasInitial) {
            String t = LoyaltyPolicy.tierForPoints(initialPoints);
            applyUI(t, initialPoints);
        } else {
            loadAndRenderUser();
        }
        return v;
    }

    /** Quy tắc điểm - hạng */
    @SuppressLint("SetTextI18n")
    private void renderStaticRules() {
        String unrank = "0–" + LoyaltyPolicy.UNRANK_MAX + ": Chưa xếp hạng";
        String bronze = LoyaltyPolicy.BRONZE_MIN + "–" + LoyaltyPolicy.BRONZE_MAX + ": Đồng (giảm 5%)";
        String silver = LoyaltyPolicy.SILVER_MIN + "–" + LoyaltyPolicy.SILVER_MAX + ": Bạc (giảm 10%)";
        String gold   = LoyaltyPolicy.GOLD_MIN + "–" + LoyaltyPolicy.GOLD_MAX + ": Vàng (giảm 15%)";
        String diamond = "≥" + LoyaltyPolicy.DIAMOND_MIN + ": Kim Cương (giảm 20%)";

        tvRuleUnrank.setText(unrank);
        tvRuleBronze.setText(bronze);
        tvRuleSilver.setText(silver);
        tvRuleGold.setText(gold);
        
        // Hiển thị Diamond tier
        TextView tvRuleDiamond = getView() != null ? getView().findViewById(R.id.tvRuleDiamond) : null;
        if (tvRuleDiamond != null) {
            tvRuleDiamond.setText(diamond);
        }
    }

    /** Load user theo docId USRxxxxx */
    private void loadAndRenderUser() {
        String docId = null;
        try {
            Context ctx = requireContext().getApplicationContext();
            docId = new SessionManager(ctx).getUid();
        } catch (Throwable ignored) {}

        if (TextUtils.isEmpty(docId)) {
            applyUI("Chưa xếp hạng", 0);
            return;
        }

        UsersRepository usersRepository = new UsersRepository();
        usersRepository.fetchUserById(docId, new UsersRepository.OnUserListener() {
            @Override
            public void onUserReceived(@NonNull com.pro.milkteaapp.models.User user) {
                long points = user.getLoyaltyPoints();
                String tier = user.getLoyaltyTier();
                if (TextUtils.isEmpty(tier)) {
                    tier = LoyaltyPolicy.tierForPoints(points);
                }
                applyUI(tier, points);
            }

            @Override
            public void onError(@NonNull Exception error) {
                applyUI("Chưa xếp hạng", 0);
            }
        });
    }

    @SuppressLint("SetTextI18n")
    private void applyUI(@NonNull String currentTier, long points) {
        tvCurrentTier.setText(currentTier);
        tvCurrentPoints.setText(points + " điểm tích lũy");

        LoyaltyPolicy.NextTierInfo next = LoyaltyPolicy.nextTier(points);
        if (next.targetTier == null) {
            tvNextInfo.setText("Bạn đang ở hạng cao nhất.");
            // Ẩn phần "Quyền lợi khi lên hạng kế tiếp" khi đã ở hạng cao nhất
            View benefitsNextLabel = getView() != null ? getView().findViewById(R.id.tvBenefitsNextLabel) : null;
            if (benefitsNextLabel != null) {
                benefitsNextLabel.setVisibility(android.view.View.GONE);
            }
            tvBenefitsNext.setVisibility(android.view.View.GONE);
            // Ẩn divider trước phần "Quyền lợi khi lên hạng"
            View dividerBeforeNext = getView() != null ? getView().findViewById(R.id.dividerBeforeNext) : null;
            if (dividerBeforeNext != null) {
                dividerBeforeNext.setVisibility(android.view.View.GONE);
            }
        } else {
            tvNextInfo.setText("Còn " + next.remainingPoints + " điểm để lên " + next.targetTier);
            // Hiển thị phần "Quyền lợi khi lên hạng kế tiếp"
            View benefitsNextLabel = getView() != null ? getView().findViewById(R.id.tvBenefitsNextLabel) : null;
            if (benefitsNextLabel != null) {
                benefitsNextLabel.setVisibility(android.view.View.VISIBLE);
            }
            tvBenefitsNext.setVisibility(android.view.View.VISIBLE);
            View dividerBeforeNext = getView() != null ? getView().findViewById(R.id.dividerBeforeNext) : null;
            if (dividerBeforeNext != null) {
                dividerBeforeNext.setVisibility(android.view.View.VISIBLE);
            }
        }

        List<String> curBenefits = LoyaltyPolicy.benefitsForTier(currentTier);
        tvBenefitsCurrent.setText("• " + joinLines(curBenefits));

        List<String> nextBenefits;
        if (next.targetTier == null) {
            nextBenefits = new ArrayList<>(); // Không hiển thị gì khi đã ở hạng cao nhất
        } else if (!next.nextTierBenefits.isEmpty()) {
            nextBenefits = next.nextTierBenefits;
        } else {
            nextBenefits = LoyaltyPolicy.benefitsForTier(next.targetTier);
        }
        tvBenefitsNext.setText("• " + joinLines(nextBenefits));
    }

    private String joinLines(@Nullable List<String> arr) {
        if (arr == null || arr.isEmpty()) return "-";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.size(); i++) {
            sb.append(arr.get(i));
            if (i < arr.size() - 1) sb.append("\n• ");
        }
        return sb.toString();
    }
}
