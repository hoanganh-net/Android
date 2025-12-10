package com.pro.milkteaapp.fragment.admin.management;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.ListenerRegistration;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.VoucherAdapter;
import com.pro.milkteaapp.models.Voucher;
import com.pro.milkteaapp.repository.VouchersRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VoucherManageFragment extends Fragment
        implements com.pro.milkteaapp.interfaces.AddActionHandler, VoucherAdapter.Listener {

    private VouchersRepository vouchersRepository;
    private ListenerRegistration listenerRegistration;
    private VoucherAdapter adapter;
    private TextInputEditText searchEditText;
    private final List<Voucher> allVouchers = new ArrayList<>();
    private String lastSearch = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.admin_fragment_voucher_manage, container, false);
        vouchersRepository = new VouchersRepository();

        RecyclerView rv = v.findViewById(R.id.rv);
        View fabAdd = v.findViewById(R.id.fabAdd);
        searchEditText = v.findViewById(R.id.searchEditText);

        adapter = new VoucherAdapter(this);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        if (fabAdd != null) {
            fabAdd.setOnClickListener(x -> showUpsertDialog(null, "ORDER_FIXED"));
        }

        // Setup search
        if (searchEditText != null) {
            searchEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    lastSearch = s == null ? "" : s.toString();
                    filterVouchers();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        listenerRegistration = vouchersRepository.listenAllVouchers(new VouchersRepository.OnVouchersListener() {
            @Override
            public void onVouchersReceived(@NonNull List<Voucher> vouchers) {
                allVouchers.clear();
                allVouchers.addAll(vouchers);
                filterVouchers();
            }

            @Override
            public void onError(@NonNull Exception error) {
                Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
    }

    @Override
    public void onAddAction() {
        showUpsertDialog(null, "ORDER_FIXED");
    }

    private void showUpsertDialog(@Nullable Voucher editing, @Nullable String presetType) {
        com.pro.milkteaapp.fragment.bottomsheet.VoucherFormBottomSheet sheet =
                com.pro.milkteaapp.fragment.bottomsheet.VoucherFormBottomSheet.newInstance(editing, presetType, false);
        sheet.show(getParentFragmentManager(), "voucher_form");
    }

    @Override public void onEdit(@NonNull Voucher v) { showUpsertDialog(v, null); }

    @Override
    public void onDelete(@NonNull Voucher v) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setMessage("Xóa " + v.getCode() + "?")
                .setPositiveButton("Xóa", (dlg, w) ->
                        vouchersRepository.deleteVoucher(v.getId(), new VouchersRepository.OnVoucherUpdateListener() {
                            @Override
                            public void onSuccess() {
                                // Success
                            }

                            @Override
                            public void onError(@NonNull Exception error) {
                                Toast.makeText(requireContext(), "Lỗi xóa: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }))
                .setNegativeButton("Hủy", null).show();
    }

    @Override
    public void onToggle(@NonNull Voucher v, boolean active) {
        vouchersRepository.updateVoucherActive(v.getId(), active, new VouchersRepository.OnVoucherUpdateListener() {
            @Override
            public void onSuccess() {
                // Success
            }

            @Override
            public void onError(@NonNull Exception error) {
                Toast.makeText(requireContext(), "Lỗi cập nhật: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterVouchers() {
        if (allVouchers.isEmpty()) {
            adapter.submit(new ArrayList<>());
            return;
        }

        String query = lastSearch.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            adapter.submit(new ArrayList<>(allVouchers));
            return;
        }

        List<Voucher> filtered = new ArrayList<>();
        for (Voucher v : allVouchers) {
            String code = v.getCode() != null ? v.getCode().toLowerCase(Locale.ROOT) : "";
            if (code.contains(query)) {
                filtered.add(v);
            }
        }
        adapter.submit(filtered);
    }
}
