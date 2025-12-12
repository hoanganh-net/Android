package com.pro.milkteaapp.fragment.bottomsheet;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.Voucher;

/**
 * BottomSheet để xác nhận xóa voucher
 */
public class VoucherDeleteConfirmBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_VOUCHER = "arg_voucher";

    public interface Listener {
        void onConfirmDelete(@NonNull Voucher voucher);
    }

    private Voucher voucher;
    private Listener listener;

    public static VoucherDeleteConfirmBottomSheet newInstance(@NonNull Voucher voucher) {
        VoucherDeleteConfirmBottomSheet sheet = new VoucherDeleteConfirmBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_VOUCHER, voucher);
        sheet.setArguments(args);
        return sheet;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public int getTheme() {
        return R.style.AppBottomSheetDialogTheme;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            voucher = (Voucher) getArguments().getSerializable(ARG_VOUCHER);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottomsheet_voucher_delete_confirm, container, false);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (voucher == null) {
            dismiss();
            return;
        }

        TextView tvMessage = view.findViewById(R.id.tvMessage);
        if (tvMessage != null && voucher.getCode() != null) {
            tvMessage.setText("Bạn có chắc muốn xóa voucher \"" + voucher.getCode() + "\"?");
        }

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dismiss());

        view.findViewById(R.id.btnDelete).setOnClickListener(v -> {
            if (listener != null) {
                listener.onConfirmDelete(voucher);
            }
            dismiss();
        });
    }
}
