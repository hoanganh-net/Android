package com.pro.milkteaapp.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.adapter.RedeemableVoucherAdapter;
import com.pro.milkteaapp.databinding.UserActivityRedeemVoucherBinding;
import com.pro.milkteaapp.models.Voucher;
import com.pro.milkteaapp.repository.RewardPointsRepository;
import com.pro.milkteaapp.repository.VouchersRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity để user đổi voucher bằng reward points
 */
public class RedeemVoucherActivity extends AppCompatActivity {

    private UserActivityRedeemVoucherBinding binding;
    private RedeemableVoucherAdapter adapter;
    private RewardPointsRepository rewardRepo;
    private VouchersRepository vouchersRepo;
    private SessionManager session;
    
    private long currentRewardPoints = 0L;
    private final List<Voucher> redeemableVouchers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = UserActivityRedeemVoucherBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        rewardRepo = new RewardPointsRepository();
        vouchersRepo = new VouchersRepository();
        session = new SessionManager(this);

        setupToolbar();
        setupRecyclerView();
        loadRewardPoints();
        loadRedeemableVouchers();
    }

    private android.widget.TextView customTitleView;
    
    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        // Set title vào TextView custom để căn giữa
        android.widget.TextView titleView = binding.toolbar.findViewById(R.id.toolbarTitle);
        if (titleView != null) {
            titleView.setText("ĐỔI VOUCHER BẰNG ĐIỂM");
        } else {
            binding.toolbar.setTitle("ĐỔI VOUCHER BẰNG ĐIỂM");
        }
    }
    

    private void setupRecyclerView() {
        adapter = new RedeemableVoucherAdapter(redeemableVouchers, currentRewardPoints, 
                (voucher, pointsRequired) -> {
            if (currentRewardPoints < pointsRequired) {
                Toast.makeText(this, "Bạn không đủ điểm để đổi voucher này", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Xác nhận đổi voucher
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Xác nhận đổi voucher")
                    .setMessage("Bạn có chắc muốn đổi voucher \"" + voucher.getCode() + 
                            "\" với giá " + pointsRequired + " điểm?")
                    .setPositiveButton("Đổi", (d, w) -> redeemVoucher(voucher, pointsRequired))
                    .setNegativeButton("Hủy", null)
                    .show();
        });
        
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);
    }

    private void loadRewardPoints() {
        String userId = session.getUid();
        if (TextUtils.isEmpty(userId)) {
            Toast.makeText(this, "Lỗi: Không lấy được thông tin người dùng", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        rewardRepo.getRewardPoints(userId, new RewardPointsRepository.OnRewardPointsListener() {
            @Override
            public void onSuccess(long totalPoints) {
                currentRewardPoints = totalPoints;
                binding.tvRewardPoints.setText("Điểm đổi quà: " + totalPoints);
                if (adapter != null) {
                    adapter.updateRewardPoints(totalPoints);
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                Toast.makeText(RedeemVoucherActivity.this, 
                        "Lỗi tải điểm: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadRedeemableVouchers() {
        vouchersRepo.fetchActiveVouchers(new VouchersRepository.OnVouchersListener() {
            @Override
            public void onVouchersReceived(@NonNull List<Voucher> vouchers) {
                redeemableVouchers.clear();
                // Chỉ hiển thị các voucher có thể đổi bằng điểm (có field pointsRequired > 0)
                for (Voucher v : vouchers) {
                    if (v.getPointsRequired() != null && v.getPointsRequired() > 0) {
                        redeemableVouchers.add(v);
                    }
                }
                if (adapter != null) {
                    adapter.submitList(redeemableVouchers);
                }
                checkEmptyState();
            }

            @Override
            public void onError(@NonNull Exception error) {
                Toast.makeText(RedeemVoucherActivity.this, 
                        "Lỗi tải voucher: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                checkEmptyState();
            }
        });
    }

    private void redeemVoucher(@NonNull Voucher voucher, long pointsRequired) {
        String userId = session.getUid();
        if (TextUtils.isEmpty(userId)) {
            Toast.makeText(this, "Lỗi: Không lấy được thông tin người dùng", Toast.LENGTH_SHORT).show();
            return;
        }

        rewardRepo.redeemRewardPoints(userId, pointsRequired, voucher.getId(), 
                new RewardPointsRepository.OnRewardPointsListener() {
            @Override
            public void onSuccess(long remainingPoints) {
                currentRewardPoints = remainingPoints;
                binding.tvRewardPoints.setText("Điểm đổi quà: " + remainingPoints);
                if (adapter != null) {
                    adapter.updateRewardPoints(remainingPoints);
                }
                Toast.makeText(RedeemVoucherActivity.this, 
                        "Đổi voucher thành công! Mã: " + voucher.getCode(), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(@NonNull Exception error) {
                Toast.makeText(RedeemVoucherActivity.this, 
                        "Lỗi đổi voucher: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkEmptyState() {
        boolean isEmpty = redeemableVouchers.isEmpty();
        binding.emptyView.setVisibility(isEmpty ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.recyclerView.setVisibility(isEmpty ? android.view.View.GONE : android.view.View.VISIBLE);
    }
}

