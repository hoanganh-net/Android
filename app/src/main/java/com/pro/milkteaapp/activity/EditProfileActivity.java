package com.pro.milkteaapp.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.databinding.UserActivityEditProfileBinding;
import com.pro.milkteaapp.repository.UsersRepository;
import com.pro.milkteaapp.utils.ImageLoader;

import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    private UserActivityEditProfileBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private DocumentReference userDoc;
    private SessionManager session;
    private UsersRepository usersRepository;
    private String currentUserId; // Lưu userId để dùng cho update

    private ActivityResultLauncher<String> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = UserActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        session = new SessionManager(this);
        usersRepository = new UsersRepository();

        setupToolbar();
        if (!ensureLoginOrFinish()) return;

        // đăng ký launcher chọn ảnh
        registerGalleryPicker();

        binding.btnPickAvatar.setOnClickListener(v -> showPickAvatarOptions());
        setupSaveButton();
        loadCurrentProfile();
    }

    private android.widget.TextView customTitleView;
    
    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        centerToolbarTitle(binding.toolbar, getString(R.string.edit_profile));
    }
    
    private void centerToolbarTitle(com.google.android.material.appbar.MaterialToolbar toolbar, String title) {
        customTitleView = com.pro.milkteaapp.utils.ToolbarUtils.centerToolbarTitle(
                toolbar, title, customTitleView, getSupportActionBar());
    }

    private boolean ensureLoginOrFinish() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, R.string.please_login_first, Toast.LENGTH_SHORT).show();
            finish();
            return false;
        }
        return true;
    }

    private void registerGalleryPicker() {
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        // Hiển thị preview ngay
                        ImageLoader.load(binding.imgAvatarPreview, uri.toString(), R.drawable.ic_avatar_default);
                        // Lưu URI local vào Firestore (content:// hoặc file://)
                        saveAvatarUriToFirestore(uri.toString());
                    }
                }
        );
    }
    
    // Lưu URI local (content:// hoặc file://) vào Firestore
    private void saveAvatarUriToFirestore(@NonNull String avatarUri) {
        ensureUserDoc();
        if (TextUtils.isEmpty(currentUserId)) {
            Toast.makeText(this, "Không thể cập nhật: thiếu thông tin người dùng", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        Map<String, Object> upd = new HashMap<>();
        upd.put("avatar", avatarUri);

        usersRepository.updateUser(currentUserId, upd, new UsersRepository.OnUserUpdateListener() {
            @Override
            public void onSuccess() {
                session.setAvatar(avatarUri);
                setLoading(false);
                Toast.makeText(EditProfileActivity.this, "Đã cập nhật ảnh đại diện", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull Exception error) {
                setLoading(false);
                Toast.makeText(EditProfileActivity.this, "Lỗi cập nhật: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadCurrentProfile() {
        setLoading(true);

        String sid = session.getUid();
        if (!TextUtils.isEmpty(sid)) {
            // Ưu tiên dùng custom ID từ SessionManager
            usersRepository.fetchUserById(sid, new UsersRepository.OnUserListener() {
                @Override
                public void onUserReceived(@NonNull com.pro.milkteaapp.models.User user) {
                    currentUserId = sid;
                    userDoc = db.collection("users").document(sid);
                    bindUserData(user);
                }

                @Override
                public void onError(@NonNull Exception error) {
                    // Fallback theo email
                    tryLoadByEmail();
                }
            });
            return;
        }

        tryLoadByEmail();
    }

    private void tryLoadByEmail() {
        FirebaseUser u = auth.getCurrentUser();
        String email = u != null ? u.getEmail() : null;
        if (!TextUtils.isEmpty(email)) {
            usersRepository.fetchUserByEmail(email, new UsersRepository.OnUserListener() {
                @Override
                public void onUserReceived(@NonNull com.pro.milkteaapp.models.User user) {
                    currentUserId = user.getUid();
                    session.setUid(user.getUid());
                    userDoc = db.collection("users").document(user.getUid());
                    bindUserData(user);
                }

                @Override
                public void onError(@NonNull Exception error) {
                    // Fallback theo firebase uid
                    tryLoadByAuthUid(u.getUid());
                }
            });
        } else if (u != null) {
            tryLoadByAuthUid(u.getUid());
        } else {
            setLoading(false);
            Toast.makeText(this, "Không thể tải hồ sơ", Toast.LENGTH_SHORT).show();
        }
    }

    private void tryLoadByAuthUid(String authUid) {
        usersRepository.fetchUserByAuthUid(authUid, new UsersRepository.OnUserListener() {
            @Override
            public void onUserReceived(@NonNull com.pro.milkteaapp.models.User user) {
                currentUserId = user.getUid();
                session.setUid(user.getUid());
                userDoc = db.collection("users").document(user.getUid());
                bindUserData(user);
            }

            @Override
            public void onError(@NonNull Exception error) {
                setLoading(false);
                Toast.makeText(EditProfileActivity.this, "Lỗi tải hồ sơ: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindUserData(@NonNull com.pro.milkteaapp.models.User user) {
        FirebaseUser u = auth.getCurrentUser();

        // Ưu tiên: user.getFullName() -> Firebase Auth displayName
        String fullName = firstNonEmpty(user.getFullName(), u != null ? u.getDisplayName() : null);
        String email = firstNonEmpty(user.getEmail(), u != null ? u.getEmail() : null);
        String phone = orEmpty(user.getPhone());
        String address = orEmpty(user.getAddress());
        String avatar = orEmpty(user.getAvatar());

        binding.editTextFullName.setText(fullName);
        binding.editTextEmail.setText(email);
        binding.editTextPhone.setText(phone);
        binding.editTextAddress.setText(address);

        if (!TextUtils.isEmpty(avatar)) session.setAvatar(avatar);
        ImageLoader.load(binding.imgAvatarPreview, avatar, R.drawable.ic_avatar_default);

        setLoading(false);
    }

    private void setupSaveButton() {
        binding.buttonSaveProfile.setOnClickListener(v -> {
            FirebaseUser u = auth.getCurrentUser();
            if (u == null) {
                Toast.makeText(this, R.string.please_login_first, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            String fullName = text(binding.editTextFullName);
            String email = text(binding.editTextEmail);
            String phone = text(binding.editTextPhone);
            String address = text(binding.editTextAddress);

            if (!validate(fullName, email, phone)) return;

            if (TextUtils.isEmpty(currentUserId)) {
                currentUserId = !TextUtils.isEmpty(session.getUid()) ? session.getUid() : u.getUid();
                userDoc = db.collection("users").document(currentUserId);
            }

            saveProfileFields(fullName, email, phone, address);
        });
    }

    /**
     * Hiển thị lựa chọn:
     * 1. Ảnh có sẵn (drawable)
     * 2. Chọn từ thư viện
     */
    private void showPickAvatarOptions() {
        String[] options = new String[]{"Chọn avatar có sẵn", "Chọn từ thư viện"};
        new AlertDialog.Builder(this)
                .setTitle("Chọn ảnh đại diện")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // mở danh sách drawable
                        showAvatarChooser();
                    } else {
                        // mở gallery
                        if (pickImageLauncher != null) {
                            pickImageLauncher.launch("image/*");
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Chọn avatar từ res/drawable, chỉ lấy file có tên bắt đầu bằng "avt", hiển thị dưới dạng ảnh */
    private void showAvatarChooser() {
        // Lấy toàn bộ resource trong R.drawable
        java.lang.reflect.Field[] drawables = R.drawable.class.getFields();
        java.util.List<AvatarItem> avatarItems = new java.util.ArrayList<>();

        // Lọc ra tên bắt đầu bằng "avt" và lấy resource ID
        for (java.lang.reflect.Field field : drawables) {
            try {
                String name = field.getName();
                if (name.toLowerCase().startsWith("avt")) {
                    int resId = field.getInt(null);
                    avatarItems.add(new AvatarItem(name, resId));
                }
            } catch (Exception e) {
                // Bỏ qua field không hợp lệ
            }
        }

        // Nếu không có ảnh nào
        if (avatarItems.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy ảnh avatar nào trong res/drawable", Toast.LENGTH_SHORT).show();
            return;
        }

        // Tạo dialog với RecyclerView
        View dialogView = getLayoutInflater().inflate(R.layout._dialog_avatar_chooser, null);
        RecyclerView recyclerView = dialogView.findViewById(R.id.recyclerViewAvatars);
        
        // GridLayout với 3 cột
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        
        // Adapter để hiển thị ảnh
        AvatarChooserAdapter adapter = new AvatarChooserAdapter(avatarItems, drawableName -> {
            dialog.dismiss(); // Đóng dialog khi chọn
            saveAvatarDrawableNameToFirestore(drawableName);
        });
        recyclerView.setAdapter(adapter);
        
        dialog.show();
    }
    
    // Class để lưu thông tin avatar
    private static class AvatarItem {
        final String name;
        final int resId;
        
        AvatarItem(String name, int resId) {
            this.name = name;
            this.resId = resId;
        }
    }
    
    // Adapter để hiển thị avatar trong grid
    private static class AvatarChooserAdapter extends RecyclerView.Adapter<AvatarChooserAdapter.AvatarViewHolder> {
        private final java.util.List<AvatarItem> items;
        private final OnAvatarSelectedListener listener;
        
        interface OnAvatarSelectedListener {
            void onSelected(String drawableName);
        }
        
        AvatarChooserAdapter(java.util.List<AvatarItem> items, OnAvatarSelectedListener listener) {
            this.items = items;
            this.listener = listener;
        }
        
        @NonNull
        @Override
        public AvatarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_avatar_chooser, parent, false);
            return new AvatarViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull AvatarViewHolder holder, int position) {
            if (position < 0 || position >= items.size()) return;
            
            AvatarItem item = items.get(position);
            // Load trực tiếp từ resId
            com.bumptech.glide.Glide.with(holder.itemView.getContext())
                    .load(item.resId)
                    .placeholder(R.drawable.ic_avatar_default)
                    .error(R.drawable.ic_avatar_default)
                    .into(holder.imgAvatar);
            
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSelected(item.name);
                }
            });
        }
        
        @Override
        public int getItemCount() {
            return items.size();
        }
        
        static class AvatarViewHolder extends RecyclerView.ViewHolder {
            de.hdodenhof.circleimageview.CircleImageView imgAvatar;
            
            AvatarViewHolder(@NonNull View itemView) {
                super(itemView);
                imgAvatar = itemView.findViewById(R.id.imgAvatar);
            }
        }
    }



    private void saveAvatarDrawableNameToFirestore(@NonNull String drawableName) {
        ensureUserDoc();
        if (TextUtils.isEmpty(currentUserId)) {
            Toast.makeText(this, "Không thể cập nhật: thiếu thông tin người dùng", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        Map<String, Object> upd = new HashMap<>();
        upd.put("avatar", drawableName);

        usersRepository.updateUser(currentUserId, upd, new UsersRepository.OnUserUpdateListener() {
            @Override
            public void onSuccess() {
                session.setAvatar(drawableName);
                ImageLoader.load(binding.imgAvatarPreview, drawableName, R.drawable.ic_avatar_default);
                setLoading(false);
                Toast.makeText(EditProfileActivity.this, "Đã cập nhật ảnh đại diện", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull Exception error) {
                setLoading(false);
                Toast.makeText(EditProfileActivity.this, "Lỗi cập nhật: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }


    private void ensureUserDoc() {
        if (userDoc == null || TextUtils.isEmpty(currentUserId)) {
            FirebaseUser u = auth.getCurrentUser();
            currentUserId = !TextUtils.isEmpty(session.getUid())
                    ? session.getUid()
                    : (u != null ? u.getUid() : null);
            if (!TextUtils.isEmpty(currentUserId)) {
                userDoc = db.collection("users").document(currentUserId);
            }
        }
    }

    private void saveProfileFields(@NonNull String fullName,
                                   @NonNull String email,
                                   @NonNull String phone,
                                   @NonNull String address) {
        setLoading(true);

        Map<String, Object> data = new HashMap<>();
        data.put("fullName", fullName);
        data.put("email", email);
        data.put("phone", phone);
        data.put("address", address);
        data.put("updatedAt", FieldValue.serverTimestamp());

        usersRepository.updateUser(currentUserId, data, new UsersRepository.OnUserUpdateListener() {
            @Override
            public void onSuccess() {
                FirebaseUser u = auth.getCurrentUser();
                if (u != null && !TextUtils.isEmpty(fullName)) {
                    u.updateProfile(new UserProfileChangeRequest.Builder()
                            .setDisplayName(fullName)
                            .build());
                }
                setLoading(false);
                Toast.makeText(EditProfileActivity.this, R.string.saved_successfully, Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(@NonNull Exception error) {
                setLoading(false);
                Toast.makeText(EditProfileActivity.this,
                        getString(R.string.save_failed_with_reason, error.getMessage()),
                        Toast.LENGTH_LONG).show();
            }
        });
    }


    private boolean validate(@NonNull String fullName, @NonNull String email, @NonNull String phone) {
        if (TextUtils.isEmpty(fullName)) {
            binding.editTextFullName.setError(getString(R.string.error_fullname_required));
            binding.editTextFullName.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.editTextEmail.setError(getString(R.string.error_invalid_email));
            binding.editTextEmail.requestFocus();
            return false;
        }
        if (!TextUtils.isEmpty(phone) && (phone.length() < 9 || phone.length() > 11)) {
            binding.editTextPhone.setError(getString(R.string.error_invalid_phone));
            binding.editTextPhone.requestFocus();
            return false;
        }
        return true;
    }

    private void setLoading(boolean loading) {
        View progress = findViewById(R.id.progressBar);
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);

        binding.buttonSaveProfile.setEnabled(!loading);
        setEnabled(binding.editTextFullName, !loading);
        setEnabled(binding.editTextEmail, !loading);
        setEnabled(binding.editTextPhone, !loading);
        setEnabled(binding.editTextAddress, !loading);
        binding.btnPickAvatar.setEnabled(!loading);
    }

    private void setEnabled(EditText e, boolean enabled) {
        if (e != null) e.setEnabled(enabled);
    }

    private String text(@NonNull EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private String orEmpty(String v) {
        return v == null ? "" : v;
    }

    private String firstNonEmpty(String a, String b) {
        return !TextUtils.isEmpty(a) ? a : (!TextUtils.isEmpty(b) ? b : "");
    }

    private String firstNonEmpty(String a, String b, String c) {
        if (!TextUtils.isEmpty(a)) return a;
        if (!TextUtils.isEmpty(b)) return b;
        return !TextUtils.isEmpty(c) ? c : "";
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
