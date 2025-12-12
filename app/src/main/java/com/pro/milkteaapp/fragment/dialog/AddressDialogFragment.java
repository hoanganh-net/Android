package com.pro.milkteaapp.fragment.dialog;

import android.app.Dialog;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.Address;
import com.pro.milkteaapp.repository.AddressRepository;

public class AddressDialogFragment extends DialogFragment {

    public interface Listener { void onAddressSaved(Address address, boolean isEdit); }

    private static final String ARG_ADDRESS = "arg_address";

    public static AddressDialogFragment newInstance(@Nullable Address a) {
        AddressDialogFragment d = new AddressDialogFragment();
        Bundle b = new Bundle();
        b.putParcelable(ARG_ADDRESS, a);
        d.setArguments(b);
        return d;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle s) {
        View v = getLayoutInflater().inflate(R.layout._dialog_address_editor, null, false);

        EditText edtName = v.findViewById(R.id.edtName);
        EditText edtPhone = v.findViewById(R.id.edtPhone);
        EditText edtLine1 = v.findViewById(R.id.edtLine1);
        EditText edtLine2 = v.findViewById(R.id.edtLine2);
        EditText edtCity = v.findViewById(R.id.edtCity);
        EditText edtProvince = v.findViewById(R.id.edtProvince);
        MaterialButton btnSave = v.findViewById(R.id.btnSave);

        Address editing;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            editing = getArguments() != null ? getArguments().getParcelable(ARG_ADDRESS, Address.class) : null;
        } else {
            editing = null;
        }
        if (editing != null) {
            edtName.setText(editing.getFullName());
            edtPhone.setText(editing.getPhone());
            edtLine1.setText(editing.getLine1());
            edtLine2.setText(editing.getLine2());
            edtCity.setText(editing.getCity());
            edtProvince.setText(editing.getProvince());
        }

        // ✅ Khởi tạo repo với Context để lấy userId USRxxxxx từ SessionManager
        AddressRepository repo = new AddressRepository(requireContext());

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(editing == null ? R.string.add_address : R.string.edit_address)
                .setView(v)
                .setNegativeButton(R.string.cancel, null)
                .create();

        btnSave.setOnClickListener(view -> {
            String name = val(edtName), phone = val(edtPhone), line1 = val(edtLine1),
                    city = val(edtCity), province = val(edtProvince);

            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(phone) ||
                    TextUtils.isEmpty(line1) || TextUtils.isEmpty(city) || TextUtils.isEmpty(province)) {
                if (getContext() != null) {
                    android.widget.Toast.makeText(getContext(), 
                        "Vui lòng điền đầy đủ thông tin bắt buộc", 
                        android.widget.Toast.LENGTH_SHORT).show();
                }
                return;
            }

            Address out = editing != null ? editing : new Address();
            // Không sinh UUID thủ công — repo sẽ add/update và tự set id trả về
            out.setFullName(name);
            out.setPhone(phone);
            out.setLine1(line1);
            out.setLine2(val(edtLine2));
            out.setCity(city);
            out.setProvince(province);
            // Không lưu mã bưu chính - đã xóa khỏi UI
            out.setPostalCode(null);

            // Nếu là địa chỉ mới (không phải edit), kiểm tra xem đã có địa chỉ nào chưa
            if (editing == null) {
                // Kiểm tra xem đã có địa chỉ nào chưa
                repo.fetchAll(addresses -> {
                    // Nếu chưa có địa chỉ nào, địa chỉ đầu tiên sẽ là mặc định
                    out.setDefault(addresses.isEmpty());
                    
                    btnSave.setEnabled(false);
                    repo.addOrUpdate(out, saved -> {
                        // Báo về cho host (Activity/Fragment) nếu có implement Listener
                        if (isAdded()) {
                            if (getActivity() instanceof Listener) {
                                ((Listener) getActivity()).onAddressSaved(saved, false);
                            } else if (getParentFragment() instanceof Listener) {
                                ((Listener) getParentFragment()).onAddressSaved(saved, false);
                            }
                            dismiss();
                        }
                    }, e -> {
                        btnSave.setEnabled(true);
                        if (isAdded() && getContext() != null) {
                            android.widget.Toast.makeText(getContext(), 
                                "Lỗi: " + (e.getMessage() != null ? e.getMessage() : "Không thể lưu địa chỉ"), 
                                android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                }, e -> {
                    // Nếu lỗi khi fetch, vẫn tạo địa chỉ nhưng không set mặc định
                    out.setDefault(false);
                    btnSave.setEnabled(false);
                    repo.addOrUpdate(out, saved -> {
                        if (isAdded()) {
                            if (getActivity() instanceof Listener) {
                                ((Listener) getActivity()).onAddressSaved(saved, false);
                            } else if (getParentFragment() instanceof Listener) {
                                ((Listener) getParentFragment()).onAddressSaved(saved, false);
                            }
                            dismiss();
                        }
                    }, err -> {
                        btnSave.setEnabled(true);
                        if (isAdded() && getContext() != null) {
                            android.widget.Toast.makeText(getContext(), 
                                "Lỗi: " + (err.getMessage() != null ? err.getMessage() : "Không thể lưu địa chỉ"), 
                                android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            } else {
                // Khi edit, giữ nguyên giá trị isDefault hiện tại
                btnSave.setEnabled(false);
                repo.addOrUpdate(out, saved -> {
                    // Báo về cho host (Activity/Fragment) nếu có implement Listener
                    if (isAdded()) {
                        if (getActivity() instanceof Listener) {
                            ((Listener) getActivity()).onAddressSaved(saved, true);
                        } else if (getParentFragment() instanceof Listener) {
                            ((Listener) getParentFragment()).onAddressSaved(saved, true);
                        }
                        dismiss();
                    }
                }, e -> {
                    btnSave.setEnabled(true);
                    if (isAdded() && getContext() != null) {
                        android.widget.Toast.makeText(getContext(), 
                            "Lỗi: " + (e.getMessage() != null ? e.getMessage() : "Không thể lưu địa chỉ"), 
                            android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        return dialog;
    }

    private static String val(EditText e) {
        return e.getText() != null ? e.getText().toString().trim() : "";
    }
}
