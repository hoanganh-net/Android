package com.pro.milkteaapp.fragment.pickersheet;

import android.os.Bundle;
import android.view.*;
import androidx.annotation.*;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.pro.milkteaapp.databinding.PickerPaymentMethodBinding;
import com.pro.milkteaapp.payment.PaymentService;

public class PaymentMethodPickerSheet extends BottomSheetDialogFragment {

    public interface Listener { void onPicked(String method); }
    private PickerPaymentMethodBinding b;
    private Listener listener;

    public void setListener(Listener l) { this.listener = l; }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        b = PickerPaymentMethodBinding.inflate(inf, c, false);
        return b.getRoot();
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        b.btnCOD.setOnClickListener(v1 -> pick(PaymentService.METHOD_COD));
        b.btnVNPAY.setOnClickListener(v1 -> pick(PaymentService.METHOD_VNPAY));
        b.btnMomo.setOnClickListener(v1 -> pick(PaymentService.METHOD_MOMO));
        
        // Thêm nút thẻ nếu có trong layout
        b.btnCard.setOnClickListener(v1 -> pick(PaymentService.METHOD_CARD));
    }

    private void pick(String m) { if (listener!=null) listener.onPicked(m); dismiss(); }
    @Override public void onDestroyView(){ super.onDestroyView(); b=null; }
}
