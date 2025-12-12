package com.pro.milkteaapp.activity.admin;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.activity.MainActivity;
import com.pro.milkteaapp.models.Voucher;
import com.pro.milkteaapp.repository.VouchersRepository;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Activity để thêm/sửa voucher
 */
public class VoucherEditorActivity extends AppCompatActivity {

    public static final String EXTRA_VOUCHER_ID = "voucherId";
    public static final String EXTRA_PRESET_TYPE = "presetType";
    public static final String EXTRA_IS_POINT_REDEMPTION = "isPointRedemption";

    // Views
    private TextInputEditText edtCode;
    private TextInputEditText edtAmount;
    private TextInputEditText edtMin;
    private TextInputEditText edtPointsRequired;
    private SwitchMaterial swActive;
    private MaterialButton btnTypeOrderFixed;
    private MaterialButton btnTypeShipFixed;
    private MaterialButton btnSelectDateRange;
    private TextView tvSelectedDateRange;
    private Button btnCancel;
    private Button btnComplete;
    private ProgressBar progress;
    private TextInputLayout tilPointsRequired;

    private boolean isEditMode = false;
    private String voucherId;
    private String presetType;
    private boolean isPointRedemption;
    private final String[] type = new String[]{"ORDER_FIXED"};
    private Date selectedDateFrom, selectedDateTo;
    private VouchersRepository vouchersRepository;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Chặn non-admin
        if (!new SessionManager(this).isAdmin()) {
            Toast.makeText(this, "Bạn không có quyền truy cập (admin-only)", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }

        // Xác định mode
        voucherId = getIntent().getStringExtra(EXTRA_VOUCHER_ID);
        presetType = getIntent().getStringExtra(EXTRA_PRESET_TYPE);
        isPointRedemption = getIntent().getBooleanExtra(EXTRA_IS_POINT_REDEMPTION, false);
        isEditMode = voucherId != null && !voucherId.trim().isEmpty();
        setContentView(R.layout.admin_activity_edit_voucher);

        // Bind views
        bindViews();

        // Repository
        vouchersRepository = new VouchersRepository();

        // Setup listeners
        setupListeners();

        // Load voucher nếu edit mode
        if (isEditMode) {
            loadVoucher(voucherId);
        } else {
            // Set giá trị mặc định cho add mode
            populateDefaults();
        }

        // Update toolbar title
        updateToolbarTitle();
    }

    private void bindViews() {
        edtCode = findViewById(R.id.edtCode);
        edtAmount = findViewById(R.id.edtAmount);
        edtMin = findViewById(R.id.edtMin);
        edtPointsRequired = findViewById(R.id.edtPointsRequired);
        swActive = findViewById(R.id.swActive);
        btnTypeOrderFixed = findViewById(R.id.btnTypeOrderFixed);
        btnTypeShipFixed = findViewById(R.id.btnTypeShipFixed);
        btnSelectDateRange = findViewById(R.id.btnSelectDateRange);
        tvSelectedDateRange = findViewById(R.id.tvSelectedDateRange);
        btnCancel = findViewById(R.id.buttonCancel);
        btnComplete = findViewById(R.id.buttonComplete);
        progress = findViewById(R.id.progressBar);
        tilPointsRequired = findViewById(R.id.tilPointsRequired);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        }
    }

    private void updateToolbarTitle() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            String title = isEditMode ? "CHỈNH SỬA VOUCHER" : "THÊM VOUCHER MỚI";
            TextView titleView = toolbar.findViewById(R.id.toolbarTitle);
            if (titleView != null) {
                titleView.setText(title);
            } else {
                toolbar.setTitle(title);
            }
        }
    }

    private void setupListeners() {
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> finish()); // Hủy Thay Đổi: quay lại không lưu
        }

        if (btnComplete != null) {
            btnComplete.setOnClickListener(v -> saveVoucher()); // Hoàn Thành: lưu thay đổi
        }

        // Type selection
        if (btnTypeOrderFixed != null) {
            btnTypeOrderFixed.setOnClickListener(v -> {
                type[0] = "ORDER_FIXED";
                updateTypeButtons();
            });
        }

        if (btnTypeShipFixed != null) {
            btnTypeShipFixed.setOnClickListener(v -> {
                type[0] = "SHIPPING_FIXED";
                updateTypeButtons();
            });
        }

        // Date range picker
        if (btnSelectDateRange != null) {
            btnSelectDateRange.setOnClickListener(v -> showDateRangePicker());
        }

        // Show/hide points required field
        if (tilPointsRequired != null) {
            tilPointsRequired.setVisibility(isPointRedemption ? View.VISIBLE : View.GONE);
        }
    }

    private void updateTypeButtons() {
        boolean isOrderFixed = "ORDER_FIXED".equals(type[0]);
        
        if (btnTypeOrderFixed != null && btnTypeShipFixed != null) {
            if (isOrderFixed) {
                // OrderFixed được chọn
                btnTypeOrderFixed.setBackgroundTintList(androidx.core.content.ContextCompat.getColorStateList(this, R.color.primary));
                btnTypeOrderFixed.setTextColor(getResources().getColor(android.R.color.white, null));
                btnTypeOrderFixed.setStrokeWidth(0);
                
                // ShipFixed không được chọn
                btnTypeShipFixed.setBackgroundTintList(null);
                btnTypeShipFixed.setTextColor(getResources().getColor(R.color.primary, null));
                btnTypeShipFixed.setStrokeWidth(1);
            } else {
                // ShipFixed được chọn
                btnTypeShipFixed.setBackgroundTintList(androidx.core.content.ContextCompat.getColorStateList(this, R.color.primary));
                btnTypeShipFixed.setTextColor(getResources().getColor(android.R.color.white, null));
                btnTypeShipFixed.setStrokeWidth(0);
                
                // OrderFixed không được chọn
                btnTypeOrderFixed.setBackgroundTintList(null);
                btnTypeOrderFixed.setTextColor(getResources().getColor(R.color.primary, null));
                btnTypeOrderFixed.setStrokeWidth(1);
            }
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
        
        datePicker.show(getSupportFragmentManager(), "DATE_RANGE_PICKER");
    }

    @SuppressLint("SetTextI18n")
    private void updateDateDisplay() {
        if (tvSelectedDateRange != null) {
            if (selectedDateFrom != null && selectedDateTo != null) {
                String dateRange = dateFormat.format(selectedDateFrom) + " - " + dateFormat.format(selectedDateTo);
                tvSelectedDateRange.setText(dateRange);
                tvSelectedDateRange.setTextColor(getResources().getColor(android.R.color.black, null));
            } else {
                tvSelectedDateRange.setText("Chưa chọn");
                tvSelectedDateRange.setTextColor(getResources().getColor(android.R.color.darker_gray, null));
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void populateDefaults() {
        if (presetType != null) type[0] = presetType;
        if (edtAmount != null) edtAmount.setText("15000");
        if (edtMin != null) edtMin.setText("60000");
        if (edtPointsRequired != null && isPointRedemption) edtPointsRequired.setText("100");
        if (swActive != null) swActive.setChecked(true);
        
        // Set default dates to today
        Calendar cal = Calendar.getInstance();
        selectedDateFrom = cal.getTime();
        selectedDateTo = cal.getTime();
        updateDateDisplay();
        updateTypeButtons();
    }

    private void loadVoucher(String id) {
        setLoading(true);
        vouchersRepository.fetchVoucherById(id, new VouchersRepository.OnVoucherListener() {
            @Override
            public void onVoucherReceived(@NonNull Voucher voucher) {
                setLoading(false);
                
                type[0] = voucher.getType();
                if (edtCode != null) edtCode.setText(voucher.getCode());
                if (edtAmount != null) edtAmount.setText(String.valueOf(voucher.getAmount()));
                if (edtMin != null) edtMin.setText(String.valueOf(voucher.getMinOrder()));
                
                if (voucher.getStartAt() != null) {
                    selectedDateFrom = voucher.getStartAt().toDate();
                }
                if (voucher.getEndAt() != null) {
                    selectedDateTo = voucher.getEndAt().toDate();
                }
                updateDateDisplay();
                
                // Show points required field only if isPointRedemption is true (from Intent)
                // This ensures that when editing from VoucherManageFragment, field is hidden
                // even if the voucher has pointsRequired > 0
                if (tilPointsRequired != null) {
                    tilPointsRequired.setVisibility(isPointRedemption ? View.VISIBLE : View.GONE);
                }
                if (edtPointsRequired != null && voucher.getPointsRequired() != null && voucher.getPointsRequired() > 0) {
                    edtPointsRequired.setText(String.valueOf(voucher.getPointsRequired()));
                }
                if (swActive != null) swActive.setChecked(voucher.isActive());
                
                updateTypeButtons();
            }

            @Override
            public void onError(@NonNull Exception error) {
                setLoading(false);
                toast("Lỗi tải voucher: " + error.getMessage());
                finish();
            }
        });
    }

    private void saveVoucher() {
        clearErrors();

        String code = (edtCode != null && edtCode.getText() != null)
                ? edtCode.getText().toString().trim().toUpperCase(Locale.getDefault())
                : "";
        if (code.isEmpty()) {
            if (edtCode != null) {
                edtCode.setError("Bắt buộc");
                edtCode.requestFocus();
            }
            return;
        }

        long amount = parseLong(edtAmount);
        long min = parseLong(edtMin);
        long pointsRequired = parseLong(edtPointsRequired);

        if (amount <= 0) {
            if (edtAmount != null) {
                edtAmount.setError("Nhập số tiền > 0");
                edtAmount.requestFocus();
            }
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
            toast("Vui lòng chọn thời gian áp dụng");
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
            toast("Ngày kết thúc phải từ hôm nay trở đi");
            return;
        }

        // Convert selectedDateFrom to Timestamp
        Calendar fromCal = Calendar.getInstance();
        fromCal.setTime(selectedDateFrom);
        fromCal.set(Calendar.HOUR_OF_DAY, 0);
        fromCal.set(Calendar.MINUTE, 0);
        fromCal.set(Calendar.SECOND, 0);
        fromCal.set(Calendar.MILLISECOND, 0);
        Timestamp startTs = new Timestamp(fromCal.getTime());

        Timestamp endTs = new Timestamp(toCal.getTime());

        setLoading(true);

        Map<String, Object> data = new HashMap<>();
        data.put("code", code);
        data.put("type", type[0]);
        data.put("amount", amount);
        data.put("percent", 0);
        data.put("maxDiscount", null);
        data.put("minOrder", min);
        data.put("startAt", startTs);
        data.put("endAt", endTs);
        // perUserLimit: Giới hạn số lần mỗi user được SỬ DỤNG voucher (khi checkout)
        // Lưu ý: perUserLimit KHÔNG áp dụng khi ĐỔI voucher bằng điểm - voucher đổi điểm có thể đổi nhiều lần
        data.put("perUserLimit", 1);
        data.put("active", swActive != null && swActive.isChecked());
        data.put("allowedChannels", java.util.Arrays.asList("COD", "MOMO", "CARD"));
        data.put("description", "");
        data.put("pointsRequired", (pointsRequired > 0) ? pointsRequired : null);

        if (isEditMode) {
            // UPDATE
            vouchersRepository.updateVoucher(voucherId, data, new VouchersRepository.OnVoucherUpdateListener() {
                @Override
                public void onSuccess() {
                    setLoading(false);
                    toast("Đã cập nhật voucher");
                    setResult(RESULT_OK);
                    finish();
                }

                @Override
                public void onError(@NonNull Exception error) {
                    setLoading(false);
                    toast("Cập nhật thất bại: " + error.getMessage());
                }
            });
        } else {
            // CREATE
            vouchersRepository.createVoucher(data, new VouchersRepository.OnVoucherCreateListener() {
                @Override
                public void onSuccess(@NonNull String vId) {
                    setLoading(false);
                    toast("Đã thêm voucher");
                    setResult(RESULT_OK);
                    finish();
                }

                @Override
                public void onError(@NonNull Exception error) {
                    setLoading(false);
                    toast("Thêm thất bại: " + error.getMessage());
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

    private String safe(TextInputEditText e) {
        return e == null || e.getText() == null ? "" : e.getText().toString().trim();
    }

    private long parseLong(TextInputEditText e) {
        try {
            return Long.parseLong(safe(e));
        } catch (Exception ignore) {
            return 0L;
        }
    }

    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnComplete != null) btnComplete.setEnabled(!loading);
        if (btnCancel != null) btnCancel.setEnabled(!loading);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
