package com.pro.milkteaapp.fragment.pickersheet;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.Voucher;
import com.pro.milkteaapp.repository.VouchersRepository;
import com.pro.milkteaapp.utils.MoneyUtils;
import com.pro.milkteaapp.utils.VoucherUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bottom sheet để chọn voucher đã đổi từ reward points
 */
public class RewardVoucherPickerSheet extends BottomSheetDialogFragment {

    // ===== Callback =====
    public interface Listener { void onPicked(@Nullable Voucher v); }
    private Listener listener;
    public void setListener(@NonNull Listener l){ this.listener = l; }

    // ===== Args =====
    private static final String ARG_SUBTOTAL = "subtotal";
    private static final String ARG_PAYMENT  = "payment";
    private static final String ARG_USERID   = "userId";

    /** Tạo instance kèm điều kiện lọc (subtotal, payment, userId). */
    public static RewardVoucherPickerSheet newInstance(long subtotal, @Nullable String payment, @Nullable String userId){
        RewardVoucherPickerSheet f = new RewardVoucherPickerSheet();
        Bundle b = new Bundle();
        b.putLong(ARG_SUBTOTAL, subtotal);
        if (payment != null) b.putString(ARG_PAYMENT, payment);
        if (userId != null) b.putString(ARG_USERID, userId);
        f.setArguments(b);
        return f;
    }

    // ===== UI =====
    private RecyclerView rv;
    private ChipGroup chipGroup;
    private TextView tvEmpty;

    // ===== Data =====
    private final List<Voucher> all = new ArrayList<>();
    private final List<Voucher> shown = new ArrayList<>();
    private final Adapter adapter = new Adapter();

    // ===== Filters =====
    private long subtotal = 0L;
    @Nullable private String payment;
    @Nullable private String userId;

    // ===== Repository =====
    private VouchersRepository vouchersRepository;
    private ListenerRegistration reg;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        View v = inflater.inflate(R.layout.bottomsheet_voucher_picker, container, false);
        rv = v.findViewById(R.id.rv);
        chipGroup = v.findViewById(R.id.chipGroupType);
        tvEmpty = v.findViewById(R.id.tvEmpty);

        vouchersRepository = new VouchersRepository();

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        Bundle args = getArguments();
        if (args != null){
            subtotal = args.getLong(ARG_SUBTOTAL, 0L);
            payment  = args.getString(ARG_PAYMENT, null);
            userId   = args.getString(ARG_USERID, null);
        } else {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser()!=null) userId = auth.getCurrentUser().getUid();
        }

        // Lắng nghe thay đổi chip để lọc
        chipGroup.setOnCheckedStateChangeListener((g, ids)-> applyFilter());

        return v;
    }

    @Override public void onStart() {
        super.onStart();
        View view = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (view != null) {
            view.getLayoutParams().height = (int) (getResources().getDisplayMetrics().heightPixels * 0.66f);
            view.requestLayout();
        }
    }

    @Override public void onResume() {
        super.onResume();
        loadRedeemedVouchers();
    }

    @Override public void onPause() {
        super.onPause();
        if (reg != null) { reg.remove(); reg = null; }
    }

    /**
     * Load các voucher đã đổi của user từ rewardRedemptions
     */
    private void loadRedeemedVouchers() {
        if (userId == null || userId.isEmpty()) {
            all.clear();
            applyFilter();
            return;
        }

        // Lấy danh sách rewardRedemptions của user
        db.collection("users").document(userId)
                .collection("rewardRedemptions")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<String> voucherIds = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String voucherId = doc.getString("voucherId");
                        if (voucherId != null && !voucherId.isEmpty()) {
                            voucherIds.add(voucherId);
                        }
                    }

                    if (voucherIds.isEmpty()) {
                        all.clear();
                        applyFilter();
                        return;
                    }

                    // Load chi tiết các voucher
                    loadVoucherDetails(voucherIds);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Lỗi tải voucher đổi quà: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    all.clear();
                    applyFilter();
                });
    }

    /**
     * Load chi tiết các voucher từ danh sách voucherIds
     */
    private void loadVoucherDetails(List<String> voucherIds) {
        all.clear();
        
        // Load từng voucher
        final int[] loadedCount = {0};
        final int totalCount = voucherIds.size();
        
        for (String voucherId : voucherIds) {
            vouchersRepository.fetchVoucherById(voucherId, new VouchersRepository.OnVoucherListener() {
                @Override
                public void onVoucherReceived(@NonNull Voucher voucher) {
                    // Kiểm tra điều kiện: thời hạn, min order, kênh thanh toán
                    if (!VoucherUtils.isInRangeNow(voucher)) {
                        loadedCount[0]++;
                        if (loadedCount[0] == totalCount) applyFilter();
                        return;
                    }

                    // Kiểm tra minOrder
                    if (voucher.getMinOrder() > 0 && subtotal < voucher.getMinOrder()) {
                        loadedCount[0]++;
                        if (loadedCount[0] == totalCount) applyFilter();
                        return;
                    }

                    // Kiểm tra kênh thanh toán
                    if (payment != null && voucher.getAllowedChannels() != null && !voucher.getAllowedChannels().isEmpty()) {
                        boolean ok = false;
                        for (String ch : voucher.getAllowedChannels()) {
                            if (payment.equalsIgnoreCase(ch)) {
                                ok = true;
                                break;
                            }
                        }
                        if (!ok) {
                            loadedCount[0]++;
                            if (loadedCount[0] == totalCount) applyFilter();
                            return;
                        }
                    }

                    // Kiểm tra active
                    if (!voucher.isActive()) {
                        loadedCount[0]++;
                        if (loadedCount[0] == totalCount) applyFilter();
                        return;
                    }

                    all.add(voucher);
                    loadedCount[0]++;
                    if (loadedCount[0] == totalCount) applyFilter();
                }

                @Override
                public void onError(@NonNull Exception error) {
                    loadedCount[0]++;
                    if (loadedCount[0] == totalCount) applyFilter();
                }
            });
        }
    }

    /** Áp dụng lọc theo chip loại */
    private void applyFilter(){
        String type = null;
        int checked = chipGroup.getCheckedChipId();
        if(checked == R.id.chipOrderFixed) type = "ORDER_FIXED";
        else if(checked == R.id.chipOrderPercent) type = "ORDER_PERCENT";
        else if(checked == R.id.chipShippingFixed) type = "SHIPPING_FIXED";

        shown.clear();
        for(Voucher v : all){
            if(type==null || type.equals(v.getType())) shown.add(v);
        }
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    /** Cập nhật trạng thái empty */
    private void updateEmptyState() {
        if (tvEmpty == null) return;
        if (shown.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rv.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rv.setVisibility(View.VISIBLE);
        }
    }

    // ===== Adapter hiển thị picker_voucher.xml =====
    class Adapter extends RecyclerView.Adapter<VH>{
        private final SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        @Override public int getItemCount(){ return shown.size(); }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vt){
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.picker_voucher, p, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos){
            Voucher v = shown.get(pos);

            // Mã
            h.tvCode.setText(nz(v.getCode()));

            // Ưu đãi
            h.tvBenefit.setText(buildBenefit(v));

            // Hạn sử dụng
            Timestamp end = v.getEndAt();
            h.tvExpiry.setText(end == null ? "Không giới hạn" : "Đến " + df.format(end.toDate()));

            // Min order
            long min = nzl(v.getMinOrder());
            h.tvMin.setText(min > 0 ? "Từ " + MoneyUtils.formatVnd(min) : "Không yêu cầu");

            // Kênh thanh toán (chips)
            bindChannels(h.chipsChannels, v.getAllowedChannels());

            // Giới hạn / KH
            long perUser = nzl(v.getPerUserLimit());
            h.tvLimit.setText(perUser > 0 ? ("Mỗi khách hàng " + perUser + " lần") : "Không giới hạn");

            // Nút chọn
            h.btnPick.setOnClickListener(x -> {
                if (listener != null) listener.onPicked(v);
                dismiss();
            });
        }

        private String buildBenefit(@NonNull Voucher v){
            String type = nz(v.getType());
            if ("ORDER_FIXED".equals(type)) {
                return "Giảm " + MoneyUtils.formatVnd(nzl(v.getAmount()));
            }
            if ("ORDER_PERCENT".equals(type)) {
                long percent = nzl(v.getPercent());
                long max = nzl(v.getMaxDiscount());
                String s = "Giảm " + percent + "% tổng đơn";
                if (max > 0) s += " (tối đa " + MoneyUtils.formatVnd(max) + ")";
                return s;
            }
            if ("SHIPPING_FIXED".equals(type)) {
                return "Giảm phí ship " + MoneyUtils.formatVnd(nzl(v.getAmount()));
            }
            return "Ưu đãi";
        }

        private void bindChannels(@NonNull ChipGroup group, @Nullable List<String> channels){
            group.removeAllViews();
            if (channels == null || channels.isEmpty()){
                Chip chip = new Chip(group.getContext(), null, com.google.android.material.R.attr.chipStyle);
                chip.setText("Tất cả");
                chip.setCheckable(false);
                group.addView(chip);
                return;
            }
            for (String ch : channels){
                Chip chip = new Chip(group.getContext(), null, com.google.android.material.R.attr.chipStyle);
                chip.setText(ch);
                chip.setCheckable(false);
                group.addView(chip);
            }
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvCode, tvBenefit, tvExpiry, tvMin, tvLimit;
        View btnPick;
        ChipGroup chipsChannels;

        VH(@NonNull View v){
            super(v);
            tvCode   = v.findViewById(R.id.tvCode);
            tvBenefit= v.findViewById(R.id.tvBenefit);
            tvExpiry = v.findViewById(R.id.tvExpiry);
            tvMin    = v.findViewById(R.id.tvMin);
            tvLimit  = v.findViewById(R.id.tvLimit);
            chipsChannels = v.findViewById(R.id.chipsChannels);
            btnPick  = v.findViewById(R.id.btnPick);
        }
    }

    // ===== Utils =====
    private static String nz(@Nullable String s){ return s == null ? "" : s; }
    private static long nzl(@Nullable Number n){ return n == null ? 0L : n.longValue(); }
}

