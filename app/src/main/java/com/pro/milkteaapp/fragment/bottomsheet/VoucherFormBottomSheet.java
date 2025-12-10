package com.pro.milkteaapp.fragment.bottomsheet;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.Voucher;
import com.pro.milkteaapp.repository.VouchersRepository;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class VoucherFormBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_VOUCHER = "arg_voucher";
    private static final String ARG_PRESET_TYPE = "arg_preset_type";
    private static final String ARG_IS_POINT_REDEMPTION = "arg_is_point_redemption";

    public interface Listener {
    }

    private Voucher editingVoucher;
    private String presetType;
    private boolean isPointRedemption;

    private View rootView;
    private TextInputEditText edtCode, edtAmount, edtMin, edtPointsRequired;
    private SwitchMaterial swActive;
    private MaterialButton btnOrderFixed, btnShipFixed, btnSelectDateRange;
    private TextView tvSelectedDateRange;
    private final String[] type = new String[]{"ORDER_FIXED"};

    private Date selectedDateFrom, selectedDateTo;
    private VouchersRepository vouchersRepository;

    public static VoucherFormBottomSheet newInstance(@Nullable Voucher voucher, @Nullable String presetType, boolean isPointRedemption) {
        VoucherFormBottomSheet sheet = new VoucherFormBottomSheet();
        Bundle args = new Bundle();
        if (voucher != null) {
            args.putSerializable(ARG_VOUCHER, voucher);
        }
        if (presetType != null) {
            args.putString(ARG_PRESET_TYPE, presetType);
        }
        args.putBoolean(ARG_IS_POINT_REDEMPTION, isPointRedemption);
        sheet.setArguments(args);
        return sheet;
    }

    public void setListener() {
    }

    @Override
    public int getTheme() {
        return R.style.AppBottomSheetDialogTheme;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            editingVoucher = (Voucher) getArguments().getSerializable(ARG_VOUCHER);
            presetType = getArguments().getString(ARG_PRESET_TYPE);
            isPointRedemption = getArguments().getBoolean(ARG_IS_POINT_REDEMPTION, false);
        }
        vouchersRepository = new VouchersRepository();
    }

    @Override
    public void onStart() {
        super.onStart();
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        if (dialog != null) {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                int toolbarHeight = (int) (getResources().getDisplayMetrics().density * 56);
                int statusBarHeight = 0;
                @SuppressLint({"InternalInsetResource", "DiscouragedApi"}) int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
                if (resourceId > 0) {
                    statusBarHeight = getResources().getDimensionPixelSize(resourceId);
                }
                int peekHeight = screenHeight - toolbarHeight - statusBarHeight;
                
                behavior.setPeekHeight(peekHeight);
                behavior.setFitToContents(false);
                behavior.setSkipCollapsed(false);
                behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.bottomsheet_voucher_form, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        LinearLayout formContainer = rootView.findViewById(R.id.formContainer);
        
        View formContent = LayoutInflater.from(requireContext()).inflate(
                isPointRedemption ? R.layout.admin_voucher_form_point_redemption : R.layout.admin_voucher_form_simple,
                formContainer, false
        );
        formContainer.addView(formContent);

        setupViews(formContent);
        setupListeners();
        populateForm();
    }

    @SuppressLint("SetTextI18n")
    private void setupViews(View form) {
        TextInputLayout tilPointsRequired = form.findViewById(R.id.tilPointsRequired);

        edtCode = form.findViewById(R.id.edtCode);
        edtAmount = form.findViewById(R.id.edtAmount);
        edtMin = form.findViewById(R.id.edtMin);
        edtPointsRequired = form.findViewById(R.id.edtPointsRequired);
        swActive = form.findViewById(R.id.swActive);
        btnOrderFixed = form.findViewById(R.id.btnTypeOrderFixed);
        btnShipFixed = form.findViewById(R.id.btnTypeShipFixed);
        btnSelectDateRange = form.findViewById(R.id.btnSelectDateRange);
        tvSelectedDateRange = form.findViewById(R.id.tvSelectedDateRange);

        TextView tvTitle = rootView.findViewById(R.id.tvTitle);
        if (editingVoucher != null) {
            tvTitle.setText("Sửa " + editingVoucher.getCode());
        } else {
            tvTitle.setText(isPointRedemption ? "Thêm voucher đổi điểm" : "Thêm voucher");
        }

        // Show points required field if point redemption
        if (tilPointsRequired != null) {
            tilPointsRequired.setVisibility(isPointRedemption ? View.VISIBLE : View.GONE);
        }
    }

    private void setupListeners() {
        rootView.findViewById(R.id.btnClose).setOnClickListener(v -> dismiss());
        rootView.findViewById(R.id.btnCancel).setOnClickListener(v -> dismiss());
        rootView.findViewById(R.id.btnSave).setOnClickListener(v -> saveVoucher());

        // Type selection - only 2 types
        btnOrderFixed.setOnClickListener(v -> {
            type[0] = "ORDER_FIXED";
            updateTypeButtons();
        });
        btnShipFixed.setOnClickListener(v -> {
            type[0] = "SHIPPING_FIXED";
            updateTypeButtons();
        });

        // Date range picker
        btnSelectDateRange.setOnClickListener(v -> showDateRangePicker());
    }

    private void updateTypeButtons() {
        boolean isOrderFixed = "ORDER_FIXED".equals(type[0]);
        
        // Nút được chọn: sáng lên (filled button với background primary)
        // Nút không được chọn: outlined button (không có background)
        if (isOrderFixed) {
            // OrderFixed được chọn - sáng lên
            btnOrderFixed.setBackgroundTintList(androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.primary));
            btnOrderFixed.setTextColor(getResources().getColor(android.R.color.white, null));
            btnOrderFixed.setStrokeWidth(0);
            
            // ShipFixed không được chọn - outlined
            btnShipFixed.setBackgroundTintList(null);
            btnShipFixed.setTextColor(getResources().getColor(R.color.primary, null));
            btnShipFixed.setStrokeWidth(1);
        } else {
            // ShipFixed được chọn - sáng lên
            btnShipFixed.setBackgroundTintList(androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.primary));
            btnShipFixed.setTextColor(getResources().getColor(android.R.color.white, null));
            btnShipFixed.setStrokeWidth(0);
            
            // OrderFixed không được chọn - outlined
            btnOrderFixed.setBackgroundTintList(null);
            btnOrderFixed.setTextColor(getResources().getColor(R.color.primary, null));
            btnOrderFixed.setStrokeWidth(1);
        }
    }

    private void showDateRangePicker() {
        MaterialDatePicker.Builder<androidx.core.util.Pair<Long, Long>> builder = 
            MaterialDatePicker.Builder.dateRangePicker();
        
        builder.setTitleText("Chọn thời gian áp dụng");
        
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 23);
        today.set(Calendar.MINUTE, 59);
        today.set(Calendar.SECOND, 59);
        today.set(Calendar.MILLISECOND, 999);
        
        CalendarConstraints.Builder constraintsBuilder = new CalendarConstraints.Builder();
        constraintsBuilder.setEnd(today.getTimeInMillis());
        builder.setCalendarConstraints(constraintsBuilder.build());
        
        // Set initial selection if dates are already selected
        if (selectedDateFrom != null && selectedDateTo != null) {
            Calendar fromCal = Calendar.getInstance();
            fromCal.setTime(selectedDateFrom);
            fromCal.set(Calendar.HOUR_OF_DAY, 0);
            fromCal.set(Calendar.MINUTE, 0);
            fromCal.set(Calendar.SECOND, 0);
            fromCal.set(Calendar.MILLISECOND, 0);
            
            Calendar toCal = Calendar.getInstance();
            toCal.setTime(selectedDateTo);
            toCal.set(Calendar.HOUR_OF_DAY, 23);
            toCal.set(Calendar.MINUTE, 59);
            toCal.set(Calendar.SECOND, 59);
            toCal.set(Calendar.MILLISECOND, 999);
            
            androidx.core.util.Pair<Long, Long> selection = 
                new androidx.core.util.Pair<>(fromCal.getTimeInMillis(), toCal.getTimeInMillis());
            builder.setSelection(selection);
        }
        
        MaterialDatePicker<androidx.core.util.Pair<Long, Long>> datePicker = builder.build();
        
        datePicker.addOnPositiveButtonClickListener(selection -> {
            if (selection != null && selection.first != null && selection.second != null) {
                Calendar fromCal = Calendar.getInstance();
                fromCal.setTimeInMillis(selection.first);
                fromCal.set(Calendar.HOUR_OF_DAY, 0);
                fromCal.set(Calendar.MINUTE, 0);
                fromCal.set(Calendar.SECOND, 0);
                fromCal.set(Calendar.MILLISECOND, 0);
                
                Calendar toCal = Calendar.getInstance();
                toCal.setTimeInMillis(selection.second);
                toCal.set(Calendar.HOUR_OF_DAY, 23);
                toCal.set(Calendar.MINUTE, 59);
                toCal.set(Calendar.SECOND, 59);
                toCal.set(Calendar.MILLISECOND, 999);
                
                selectedDateFrom = fromCal.getTime();
                selectedDateTo = toCal.getTime();
                
                updateDateDisplay();
            }
        });
        
        datePicker.show(getParentFragmentManager(), "DATE_RANGE_PICKER");
    }

    @SuppressLint("SetTextI18n")
    private void updateDateDisplay() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        
        if (tvSelectedDateRange != null) {
            if (selectedDateFrom != null && selectedDateTo != null) {
                String dateRange = sdf.format(selectedDateFrom) + " - " + sdf.format(selectedDateTo);
                tvSelectedDateRange.setText(dateRange);
                tvSelectedDateRange.setTextColor(getResources().getColor(android.R.color.black, null));
            } else {
                tvSelectedDateRange.setText("Chưa chọn");
                tvSelectedDateRange.setTextColor(getResources().getColor(android.R.color.darker_gray, null));
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void populateForm() {
        if (editingVoucher != null) {
            type[0] = editingVoucher.getType();
            edtCode.setText(editingVoucher.getCode());
            edtAmount.setText(String.valueOf(editingVoucher.getAmount()));
            edtMin.setText(String.valueOf(editingVoucher.getMinOrder()));
            
            if (editingVoucher.getStartAt() != null) {
                selectedDateFrom = editingVoucher.getStartAt().toDate();
            }
            if (editingVoucher.getEndAt() != null) {
                selectedDateTo = editingVoucher.getEndAt().toDate();
            }
            updateDateDisplay();
            
            if (editingVoucher.getPointsRequired() != null && editingVoucher.getPointsRequired() > 0 && edtPointsRequired != null) {
                edtPointsRequired.setText(String.valueOf(editingVoucher.getPointsRequired()));
            }
            swActive.setChecked(editingVoucher.isActive());
        } else {
            if (presetType != null) type[0] = presetType;
            if (edtAmount != null) edtAmount.setText("15000");
            if (edtMin != null) edtMin.setText("60000");
            if (edtPointsRequired != null && isPointRedemption) edtPointsRequired.setText("100");
            swActive.setChecked(true);
            
            // Set default dates to today
            Calendar cal = Calendar.getInstance();
            selectedDateFrom = cal.getTime();
            selectedDateTo = cal.getTime();
            updateDateDisplay();
        }
        updateTypeButtons();
    }

    private void saveVoucher() {
        clearErrors();

        String code = safe(edtCode).toUpperCase(Locale.getDefault());
        if (code.isEmpty()) {
            edtCode.setError("Bắt buộc");
            edtCode.requestFocus();
            return;
        }

        long amount = parseLong(edtAmount);
        long min = parseLong(edtMin);
        long pointsRequired = parseLong(edtPointsRequired);

        if (amount <= 0) {
            edtAmount.setError("Nhập số tiền > 0");
            edtAmount.requestFocus();
            return;
        }

        if (pointsRequired < 0) {
            if (edtPointsRequired != null) {
                edtPointsRequired.setError("Số điểm không được âm");
                edtPointsRequired.requestFocus();
            }
            return;
        }

        if (isPointRedemption && pointsRequired <= 0) {
            if (edtPointsRequired != null) {
                edtPointsRequired.setError("Voucher đổi điểm cần số điểm > 0");
                edtPointsRequired.requestFocus();
            }
            return;
        }

        if (selectedDateFrom == null || selectedDateTo == null) {
            Toast.makeText(requireContext(), "Vui lòng chọn thời gian áp dụng", Toast.LENGTH_SHORT).show();
            return;
        }

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar toCal = Calendar.getInstance();
        toCal.setTime(selectedDateTo);
        toCal.set(Calendar.HOUR_OF_DAY, 23);
        toCal.set(Calendar.MINUTE, 59);
        toCal.set(Calendar.SECOND, 59);
        toCal.set(Calendar.MILLISECOND, 999);

        if (toCal.before(today)) {
            Toast.makeText(requireContext(), "Ngày kết thúc phải từ hôm nay trở đi", Toast.LENGTH_SHORT).show();
            return;
        }

        com.google.firebase.Timestamp startTs = (editingVoucher == null || editingVoucher.getStartAt() == null)
                ? com.google.firebase.Timestamp.now() : editingVoucher.getStartAt();

        Calendar fromCal = Calendar.getInstance();
        fromCal.setTime(selectedDateFrom);
        fromCal.set(Calendar.HOUR_OF_DAY, 0);
        fromCal.set(Calendar.MINUTE, 0);
        fromCal.set(Calendar.SECOND, 0);
        fromCal.set(Calendar.MILLISECOND, 0);

        com.google.firebase.Timestamp endTs = new com.google.firebase.Timestamp(toCal.getTime());

        Map<String, Object> data = new HashMap<>();
        data.put("code", code);
        data.put("type", type[0]);
        data.put("amount", amount);
        data.put("percent", 0);
        data.put("maxDiscount", null);
        data.put("minOrder", min);
        data.put("startAt", startTs);
        data.put("endAt", endTs);
        data.put("perUserLimit", 1);
        data.put("active", swActive.isChecked());
        data.put("allowedChannels", java.util.Arrays.asList("COD", "MOMO", "CARD"));
        data.put("description", "");
        data.put("pointsRequired", (pointsRequired > 0) ? pointsRequired : null);

        if (editingVoucher == null) {
            vouchersRepository.createVoucher(data, new VouchersRepository.OnVoucherCreateListener() {
                @Override
                public void onSuccess(@NonNull String voucherId) {
                    Toast.makeText(requireContext(), "Thêm voucher thành công!", Toast.LENGTH_SHORT).show();
                    dismiss();
                }

                @Override
                public void onError(@NonNull Exception error) {
                    Toast.makeText(requireContext(), "Lỗi: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            vouchersRepository.updateVoucher(editingVoucher.getId(), data, new VouchersRepository.OnVoucherUpdateListener() {
                @Override
                public void onSuccess() {
                    Toast.makeText(requireContext(), "Cập nhật voucher thành công!", Toast.LENGTH_SHORT).show();
                    dismiss();
                }

                @Override
                public void onError(@NonNull Exception error) {
                    Toast.makeText(requireContext(), "Lỗi: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void clearErrors() {
        if (edtCode != null) edtCode.setError(null);
        if (edtAmount != null) edtAmount.setError(null);
        if (edtMin != null) edtMin.setError(null);
        if (edtPointsRequired != null) edtPointsRequired.setError(null);
    }

    private String safe(@Nullable TextInputEditText e) {
        return e == null || e.getText() == null ? "" : e.getText().toString().trim();
    }

    private long parseLong(@Nullable TextInputEditText e) {
        try {
            return Long.parseLong(safe(e));
        } catch (Exception ignore) {
            return 0L;
        }
    }
}
