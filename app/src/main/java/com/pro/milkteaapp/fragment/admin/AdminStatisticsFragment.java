package com.pro.milkteaapp.fragment.admin;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.activity.admin.AdminMainActivity;
import com.pro.milkteaapp.adapter.admin.AdminStatisticAdapter;
import com.pro.milkteaapp.models.Order;
import com.pro.milkteaapp.repository.OrdersRepository;
import com.pro.milkteaapp.repository.UsersRepository;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Fragment thống kê dành cho Admin
 * - Cho phép lọc theo ngày / tuần / tháng / tất cả
 * - Cho phép lọc theo người dùng
 * - Tính doanh thu chỉ từ đơn FINISHED
 */
public class AdminStatisticsFragment extends Fragment
        implements AdminMainActivity.ScrollToTop, AdminMainActivity.SupportsRefresh {

    private SwipeRefreshLayout swipe;
    private RecyclerView recyclerView;
    private View loading, empty;
    private Spinner spinnerUser;

    private AdminStatisticAdapter adapter;
    private final List<String> data = new ArrayList<>();

    private TextView kpi1Title, kpi1Value;
    private TextView kpi2Title, kpi2Value;
    private TextView kpi3Title, kpi3Value;
    private TextView kpi4Title, kpi4Value;

    private OrdersRepository ordersRepository;
    private UsersRepository usersRepository;
    private final List<String> userIds = new ArrayList<>();
    private String selectedUserId = "ALL";
    private int selectedTimeType = 0; // 0=day, 1=week, 2=month, 3=all
    
    // Drawer and views
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private View basicView;
    private View advancedView;
    
    // Advanced statistics
    private Button btnSelectDateRange;
    private TextView tvSelectedDateRange;
    private RecyclerView recyclerAdvanced;
    private AdminStatisticAdapter adapterAdvanced;
    private TextView kpi1TitleAdvanced, kpi1ValueAdvanced;
    private TextView kpi2TitleAdvanced, kpi2ValueAdvanced;
    private TextView kpi3TitleAdvanced, kpi3ValueAdvanced;
    private TextView kpi4TitleAdvanced, kpi4ValueAdvanced;
    private Date selectedDateFrom;
    private Date selectedDateTo;
    private boolean isAdvancedMode = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.admin_fragment_statistics, container, false);

        ordersRepository = new OrdersRepository();
        usersRepository = new UsersRepository();
        swipe = v.findViewById(R.id.swipeRefresh);
        recyclerView = v.findViewById(R.id.recycler);
        loading = v.findViewById(R.id.loadingState);
        empty = v.findViewById(R.id.emptyState);
        Spinner spinnerTime = v.findViewById(R.id.spinnerTime);
        spinnerUser = v.findViewById(R.id.spinnerUser);

        // KPI binding
        GridLayout grid = v.findViewById(R.id.kpiGrid);
        View k1 = grid.getChildAt(0);
        View k2 = grid.getChildAt(1);
        View k3 = grid.getChildAt(2);
        View k4 = grid.getChildAt(3);

        kpi1Title = k1.findViewById(R.id.tvKpiTitle);
        kpi1Value = k1.findViewById(R.id.tvKpiValue);
        kpi2Title = k2.findViewById(R.id.tvKpiTitle);
        kpi2Value = k2.findViewById(R.id.tvKpiValue);
        kpi3Title = k3.findViewById(R.id.tvKpiTitle);
        kpi3Value = k3.findViewById(R.id.tvKpiValue);
        kpi4Title = k4.findViewById(R.id.tvKpiTitle);
        kpi4Value = k4.findViewById(R.id.tvKpiValue);

        // Recycler setup
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        adapter = new AdminStatisticAdapter();
        recyclerView.setAdapter(adapter);

        // Spinner thời gian
        ArrayAdapter<String> timeAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                Arrays.asList("Hôm nay", "Tuần này", "Tháng này", "Tất cả"));
        spinnerTime.setAdapter(timeAdapter);
        spinnerTime.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedTimeType = position;
                refresh();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Spinner người dùng
        loadUsers();

        // Setup drawer
        drawerLayout = v.findViewById(R.id.drawerLayout);
        navigationView = v.findViewById(R.id.navigationView);
        setupDrawer();
        
        // Setup views
        basicView = v.findViewById(R.id.basicView);
        advancedView = v.findViewById(R.id.advancedView);
        setupAdvancedView(v);

        swipe.setOnRefreshListener(this::refresh);
        showLoading(true);
        refresh();

        return v;
    }
    
    private void setupDrawer() {
        if (navigationView == null) return;
        
        navigationView.setNavigationItemSelectedListener(item -> {
            // Đóng drawer
            if (drawerLayout != null) {
                drawerLayout.closeDrawer(GravityCompat.START);
            }
            
            int itemId = item.getItemId();
            
            if (itemId == R.id.menu_basic) {
                switchToBasic();
            } else if (itemId == R.id.menu_advanced) {
                switchToAdvanced();
            }
            
            // Cập nhật checked state
            navigationView.setCheckedItem(item.getItemId());
            
            return true;
        });
        
        // Set default checked
        navigationView.setCheckedItem(R.id.menu_basic);
    }
    
    /**
     * Mở drawer từ bên ngoài (ví dụ từ toolbar)
     */
    public void openDrawer() {
        if (drawerLayout != null) {
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }
    
    /**
     * Đóng drawer
     */
    public void closeDrawer() {
        if (drawerLayout != null) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }
    
    /**
     * Kiểm tra drawer có đang mở không
     */
    public boolean isDrawerOpen() {
        return drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START);
    }
    
    private void switchToBasic() {
        isAdvancedMode = false;
        if (basicView != null) basicView.setVisibility(View.VISIBLE);
        if (advancedView != null) advancedView.setVisibility(View.GONE);
        refresh();
    }
    
    private void switchToAdvanced() {
        isAdvancedMode = true;
        if (basicView != null) basicView.setVisibility(View.GONE);
        if (advancedView != null) advancedView.setVisibility(View.VISIBLE);
        
        // Nếu đã có ngày được chọn, tự động load
        if (selectedDateFrom != null && selectedDateTo != null) {
            loadAdvancedStatistics();
        } else {
            // Reset KPI và data khi chưa chọn ngày
            resetAdvancedStatistics();
            // Hide empty state initially
            toggleEmpty(false);
        }
    }
    
    private void resetAdvancedStatistics() {
        if (kpi1TitleAdvanced != null) {
            kpi1TitleAdvanced.setText("Doanh thu");
            kpi1ValueAdvanced.setText("--");
            kpi2TitleAdvanced.setText("Đơn hoàn thành");
            kpi2ValueAdvanced.setText("--");
            kpi3TitleAdvanced.setText("Tỉ lệ huỷ đơn");
            kpi3ValueAdvanced.setText("--");
            kpi4TitleAdvanced.setText("TB/đơn hoàn thành");
            kpi4ValueAdvanced.setText("--");
        }
        
        if (adapterAdvanced != null) {
            adapterAdvanced.setItems(new ArrayList<>());
        }
    }
    
    private void setupAdvancedView(View rootView) {
        // Find views from included layout
        View advancedRoot = rootView.findViewById(R.id.advancedView);
        if (advancedRoot == null) return;
        
        btnSelectDateRange = advancedRoot.findViewById(R.id.btnSelectDateRange);
        tvSelectedDateRange = advancedRoot.findViewById(R.id.tvSelectedDateRange);
        Button btnLoad = advancedRoot.findViewById(R.id.btnLoadStatistics);
        recyclerAdvanced = advancedRoot.findViewById(R.id.recyclerAdvanced);
        
        // Setup date range picker button
        if (btnSelectDateRange != null) {
            btnSelectDateRange.setOnClickListener(v -> showDateRangePicker());
        }
        
        // Set default dates (today for both)
        Calendar cal = Calendar.getInstance();
        selectedDateFrom = cal.getTime();
        selectedDateTo = cal.getTime();
        updateDateDisplay();
        
        // Setup recycler for advanced
        if (recyclerAdvanced != null) {
            recyclerAdvanced.setLayoutManager(new LinearLayoutManager(requireContext()));
            recyclerAdvanced.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
            adapterAdvanced = new AdminStatisticAdapter();
            recyclerAdvanced.setAdapter(adapterAdvanced);
        }
        
        // Setup KPI for advanced
        GridLayout gridAdvanced = advancedRoot.findViewById(R.id.kpiGridAdvanced);
        if (gridAdvanced != null) {
            View k1 = gridAdvanced.getChildAt(0);
            View k2 = gridAdvanced.getChildAt(1);
            View k3 = gridAdvanced.getChildAt(2);
            View k4 = gridAdvanced.getChildAt(3);
            
            kpi1TitleAdvanced = k1.findViewById(R.id.tvKpiTitle);
            kpi1ValueAdvanced = k1.findViewById(R.id.tvKpiValue);
            kpi2TitleAdvanced = k2.findViewById(R.id.tvKpiTitle);
            kpi2ValueAdvanced = k2.findViewById(R.id.tvKpiValue);
            kpi3TitleAdvanced = k3.findViewById(R.id.tvKpiTitle);
            kpi3ValueAdvanced = k3.findViewById(R.id.tvKpiValue);
            kpi4TitleAdvanced = k4.findViewById(R.id.tvKpiTitle);
            kpi4ValueAdvanced = k4.findViewById(R.id.tvKpiValue);
        }
        
        // Setup load button
        if (btnLoad != null) {
            btnLoad.setOnClickListener(v -> loadAdvancedStatistics());
        }
        
        // Setup "Hôm nay" button
        Button btnToday = advancedRoot.findViewById(R.id.btnToday);
        if (btnToday != null) {
            btnToday.setOnClickListener(v -> {
                Calendar todayCal = Calendar.getInstance();
                selectedDateFrom = todayCal.getTime();
                selectedDateTo = todayCal.getTime();
                updateDateDisplay();
                loadAdvancedStatistics();
            });
        }
    }
    
    private void showDateRangePicker() {
        // Create MaterialDatePicker for range selection
        MaterialDatePicker.Builder<androidx.core.util.Pair<Long, Long>> builder = 
            MaterialDatePicker.Builder.dateRangePicker();
        
        // Set title
        builder.setTitleText("Chọn khoảng thời gian");
        
        // Set constraints: max date is today, allow past dates but block future dates
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 23);
        today.set(Calendar.MINUTE, 59);
        today.set(Calendar.SECOND, 59);
        today.set(Calendar.MILLISECOND, 999);
        
        CalendarConstraints.Builder constraintsBuilder = new CalendarConstraints.Builder();
        constraintsBuilder.setEnd(today.getTimeInMillis());
        // Don't set validator - setEnd() alone blocks future dates and allows all past dates
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
        
        // Handle positive button click
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
        
        // Show the date picker
        datePicker.show(getParentFragmentManager(), "DATE_RANGE_PICKER");
    }
    
    private void updateDateDisplay() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        
        if (tvSelectedDateRange != null) {
            if (selectedDateFrom != null && selectedDateTo != null) {
                String dateRange = sdf.format(selectedDateFrom) + " - " + sdf.format(selectedDateTo);
                tvSelectedDateRange.setText(dateRange);
                tvSelectedDateRange.setTextColor(getResources().getColor(R.color.black, null));
            } else {
                tvSelectedDateRange.setText("Chưa chọn ngày");
                tvSelectedDateRange.setTextColor(getResources().getColor(R.color.gray_600, null));
            }
        }
    }
    
    private void loadAdvancedStatistics() {
        if (selectedDateFrom == null || selectedDateTo == null) {
            if (isAdded() && getContext() != null) {
                Toast.makeText(getContext(), "Vui lòng chọn đầy đủ từ ngày và đến ngày", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        
        // Validate: ngày đến phải >= ngày từ
        Calendar fromCal = Calendar.getInstance();
        fromCal.setTime(selectedDateFrom);
        fromCal.set(Calendar.HOUR_OF_DAY, 0);
        fromCal.set(Calendar.MINUTE, 0);
        fromCal.set(Calendar.SECOND, 0);
        fromCal.set(Calendar.MILLISECOND, 0);
        
        Calendar toCal = Calendar.getInstance();
        toCal.setTime(selectedDateTo);
        toCal.set(Calendar.HOUR_OF_DAY, 0);
        toCal.set(Calendar.MINUTE, 0);
        toCal.set(Calendar.SECOND, 0);
        toCal.set(Calendar.MILLISECOND, 0);
        
        if (toCal.before(fromCal)) {
            if (isAdded() && getContext() != null) {
                Toast.makeText(getContext(), "Ngày đến phải lớn hơn hoặc bằng ngày từ", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        
        showLoading(true);
        swipe.setRefreshing(true);
        
        // Get start of from date
        Date startDate = fromCal.getTime();
        
        // Get end of to date
        toCal.set(Calendar.HOUR_OF_DAY, 23);
        toCal.set(Calendar.MINUTE, 59);
        toCal.set(Calendar.SECOND, 59);
        toCal.set(Calendar.MILLISECOND, 999);
        Date endDate = toCal.getTime();
        
        ordersRepository.fetchOrdersByDateRange(startDate, endDate, "ALL", new OrdersRepository.OnOrdersListener() {
            @Override
            public void onOrdersReceived(@NonNull List<Order> orders) {
                calculateAndDisplayAdvancedStatistics(orders);
            }
            
            @Override
            public void onError(@NonNull Exception error) {
                swipe.setRefreshing(false);
                showLoading(false);
                if (isAdded() && getContext() != null) {
                    Toast.makeText(getContext(), "Lỗi tải dữ liệu: " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
    }
    
    @SuppressLint("SetTextI18n")
    private void calculateAndDisplayAdvancedStatistics(@NonNull List<Order> orders) {
        double totalRevenue = 0;
        int totalOrders = orders.size();
        int finishedOrders = 0;
        int cancelledOrders = 0;
        
        for (Order order : orders) {
            if (order == null) continue;
            
            String status = order.getStatus();
            if ("CANCELLED".equals(status)) {
                cancelledOrders++;
            } else if ("FINISHED".equals(status)) {
                finishedOrders++;
                double total = order.getTotal();
                if (total > 0) {
                    totalRevenue += total;
                } else {
                    double finalTotal = order.getFinalTotal();
                    if (finalTotal > 0) {
                        totalRevenue += finalTotal;
                    }
                }
            }
        }
        
        double cancelRate = (totalOrders == 0) ? 0 : (cancelledOrders * 100.0 / totalOrders);
        double avgPerOrder = (finishedOrders == 0) ? 0 : (totalRevenue / finishedOrders);
        
        NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        
        if (kpi1TitleAdvanced != null) {
            kpi1TitleAdvanced.setText("Doanh thu");
            kpi1ValueAdvanced.setText(nf.format(totalRevenue));
            kpi2TitleAdvanced.setText("Đơn hoàn thành");
            kpi2ValueAdvanced.setText(String.valueOf(finishedOrders));
            kpi3TitleAdvanced.setText("Tỉ lệ huỷ đơn");
            kpi3ValueAdvanced.setText(String.format(Locale.getDefault(), "%.1f%%", cancelRate));
            kpi4TitleAdvanced.setText("TB/đơn hoàn thành");
            kpi4ValueAdvanced.setText(nf.format(avgPerOrder));
        }
        
        List<String> advancedData = new ArrayList<>();
        if (selectedDateFrom != null && selectedDateTo != null) {
            if (selectedDateFrom.equals(selectedDateTo)) {
                advancedData.add("Ngày thống kê: " + sdf.format(selectedDateFrom));
            } else {
                advancedData.add("Khoảng thời gian: " + sdf.format(selectedDateFrom) + " - " + sdf.format(selectedDateTo));
            }
        }
        advancedData.add("Tổng đơn: " + totalOrders);
        advancedData.add("Hoàn thành: " + finishedOrders);
        advancedData.add("Huỷ: " + cancelledOrders);
        advancedData.add("Doanh thu: " + nf.format(totalRevenue));
        advancedData.add("TB/đơn hoàn thành: " + nf.format(avgPerOrder));
        
        if (adapterAdvanced != null) {
            adapterAdvanced.setItems(advancedData);
        }
        
        swipe.setRefreshing(false);
        showLoading(false);
        toggleEmpty(advancedData.isEmpty());
    }

    /** Load danh sách user */
    private void loadUsers() {
        usersRepository.fetchAllUsers(new UsersRepository.OnUsersListener() {
            @Override
            public void onUsersReceived(@NonNull List<com.pro.milkteaapp.models.User> users) {
                List<String> names = new ArrayList<>();
                userIds.clear();
                names.add("Tất cả người dùng");
                userIds.add("ALL");
                for (com.pro.milkteaapp.models.User user : users) {
                    String name = user.getFullName() != null ? user.getFullName() : user.getDisplayName();
                    if (name == null || name.trim().isEmpty()) {
                        name = user.getEmail() != null ? user.getEmail() : user.getUid();
                    }
                    names.add(name);
                    userIds.add(user.getUid());
                }

                ArrayAdapter<String> userAdapter = new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_spinner_dropdown_item, names);
                spinnerUser.setAdapter(userAdapter);
                spinnerUser.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                        selectedUserId = userIds.get(pos);
                        refresh();
                    }
                    @Override public void onNothingSelected(AdapterView<?> parent) {}
                });
            }

            @Override
            public void onError(@NonNull Exception error) {
                // Log error silently
            }
        });
    }

    @Override
    public void scrollToTop() {
        if (recyclerView != null) {
            recyclerView.smoothScrollToPosition(0);
        }
    }
    
    @SuppressLint("SetTextI18n")
    @Override
    public void refresh() {
        if (isAdvancedMode) {
            // In advanced mode, reload if dates are selected
            if (selectedDateFrom != null && selectedDateTo != null) {
                loadAdvancedStatistics();
            }
            return;
        }
        
        swipe.setRefreshing(true);
        showLoading(true);

        Date startDate = getStartDate(selectedTimeType);
        Date now = new Date();

        ordersRepository.fetchOrdersByDateRange(startDate, now, selectedUserId, new OrdersRepository.OnOrdersListener() {
            @Override
            public void onOrdersReceived(@NonNull List<Order> orders) {
                double totalRevenue = 0;
                int totalOrders = orders.size();
                int finishedOrders = 0;
                int cancelledOrders = 0;

                for (Order order : orders) {
                    if (order == null) continue;

                    String status = order.getStatus();
                    if ("CANCELLED".equals(status)) {
                        cancelledOrders++;
                    } else if ("FINISHED".equals(status)) {
                        finishedOrders++;
                        // getTotal() và getFinalTotal() trả về double (primitive), không thể null
                        double total = order.getTotal();
                        if (total > 0) {
                            totalRevenue += total;
                        } else {
                            double finalTotal = order.getFinalTotal();
                            if (finalTotal > 0) {
                                totalRevenue += finalTotal;
                            }
                        }
                    }
                }

                double cancelRate = (totalOrders == 0) ? 0 : (cancelledOrders * 100.0 / totalOrders);
                double avgPerOrder = (finishedOrders == 0) ? 0 : (totalRevenue / finishedOrders);

                NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
                kpi1Title.setText("Doanh thu");
                kpi1Value.setText(nf.format(totalRevenue));
                kpi2Title.setText("Đơn hoàn thành");
                kpi2Value.setText(String.valueOf(finishedOrders));
                kpi3Title.setText("Tỉ lệ huỷ đơn");
                kpi3Value.setText(String.format(Locale.getDefault(), "%.1f%%", cancelRate));
                kpi4Title.setText("TB/đơn hoàn thành");
                kpi4Value.setText(nf.format(avgPerOrder));

                data.clear();
                data.add("Khoảng thời gian: " + getTimeLabel(selectedTimeType));
                data.add("Tổng đơn: " + totalOrders);
                data.add("Hoàn thành: " + finishedOrders);
                data.add("Huỷ: " + cancelledOrders);
                data.add("Doanh thu: " + nf.format(totalRevenue));
                data.add("TB/đơn hoàn thành: " + nf.format(avgPerOrder));

                adapter.setItems(data);

                swipe.setRefreshing(false);
                showLoading(false);
                toggleEmpty(data.isEmpty());
            }

            @Override
            public void onError(@NonNull Exception error) {
                swipe.setRefreshing(false);
                showLoading(false);
                toggleEmpty(true);
                if (isAdded() && getContext() != null) {
                    Toast.makeText(getContext(), "Lỗi tải dữ liệu: " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /** Xác định thời gian bắt đầu */
    private Date getStartDate(int type) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        switch (type) {
            case 1: cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek()); break; // Tuần này
            case 2: cal.set(Calendar.DAY_OF_MONTH, 1); break; // Tháng này
            case 3: cal.setTimeInMillis(0); break; // Tất cả
        }
        return cal.getTime();
    }

    private String getTimeLabel(int type) {
        return switch (type) {
            case 0 -> "Hôm nay";
            case 1 -> "Tuần này";
            case 2 -> "Tháng này";
            default -> "Tất cả thời gian";
        };
    }

    private void showLoading(boolean show) {
        loading.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void toggleEmpty(boolean isEmpty) {
        if (isAdvancedMode) {
            // Handle empty state for advanced view
            if (advancedView != null) {
                View emptyAdvanced = advancedView.findViewById(R.id.emptyStateAdvanced);
                if (emptyAdvanced != null) {
                    emptyAdvanced.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                }
                
                // Hide/show recycler and KPI grid
                if (recyclerAdvanced != null) {
                    recyclerAdvanced.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                }
                View kpiGridAdvanced = advancedView.findViewById(R.id.kpiGridAdvanced);
                if (kpiGridAdvanced != null) {
                    kpiGridAdvanced.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                }
            }
        } else {
            // Handle empty state for basic view
            if (empty != null) {
                empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            }
            if (recyclerView != null) {
                recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            }
        }
    }
    public void onResume() {
        super.onResume();
    }
}
