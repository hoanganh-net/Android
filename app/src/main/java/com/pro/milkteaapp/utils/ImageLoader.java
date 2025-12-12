package com.pro.milkteaapp.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.Locale;

public final class ImageLoader {

    private static final String TAG = "ImageLoader";

    private ImageLoader() {}

    /**
     * Load ảnh từ nhiều nguồn:
     *  - "https://..." hoặc "http://..."  -> load URL
     *  - "file://..." hoặc "content://..." -> load local URI
     *  - "drawable:ic_xxx" hoặc "ic_xxx"  -> load từ res/drawable/ic_xxx.*
     *  - Tên file không extension (ví dụ: "milktea") -> tìm trong drawable
     */
    public static void load(ImageView iv, @Nullable String urlOrName, @DrawableRes int placeholder) {
        if (iv == null) return;
        
        Context ctx = iv.getContext();
        if (ctx == null) return;

        if (TextUtils.isEmpty(urlOrName)) {
            Glide.with(ctx).load(placeholder).into(iv);
            return;
        }

        String s = urlOrName.trim();

        // 1) HTTP/HTTPS URL
        if (s.startsWith("http://") || s.startsWith("https://")) {
            Glide.with(ctx)
                    .load(s)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(placeholder)
                    .error(placeholder)
                    .into(iv);
            return;
        }

        // 2) Local URI (file:// hoặc content://)
        if (s.startsWith("file://") || s.startsWith("content://")) {
            try {
                Uri uri = Uri.parse(s);
                Log.d(TAG, "Loading local URI: " + s);
                // Thử load với quyền đọc persistent
                Glide.with(ctx)
                        .load(uri)
                        .diskCacheStrategy(DiskCacheStrategy.NONE) // Không cache local URI
                        .skipMemoryCache(false) // Vẫn cache trong memory để hiển thị nhanh
                        .placeholder(placeholder)
                        .error(placeholder)
                        .into(iv);
                Log.d(TAG, "Successfully loaded URI: " + s);
            } catch (Exception e) {
                Log.w(TAG, "Failed to load URI: " + s, e);
                // Nếu load URI thất bại, thử fallback về placeholder
                Glide.with(ctx).load(placeholder).into(iv);
            }
            return;
        }

        // 3) Drawable resource name
        // Xử lý prefix "drawable:"
        if (s.startsWith("drawable:")) {
            s = s.substring("drawable:".length()).trim();
        }

        // Loại bỏ extension nếu có (.png, .jpg, .jpeg, .webp)
        String drawableName = stripExtension(s).trim();
        if (!drawableName.isEmpty()) {
            // Chuyển về lowercase và thay space bằng underscore
            drawableName = drawableName.toLowerCase(Locale.US)
                    .replace(' ', '_')
                    .replace('-', '_');
            
            @SuppressLint("DiscouragedApi") int resId = ctx.getResources().getIdentifier(drawableName, "drawable", ctx.getPackageName());
            if (resId != 0) {
                Glide.with(ctx)
                        .load(resId)
                        .placeholder(placeholder)
                        .error(placeholder)
                        .into(iv);
                return;
            } else {
                Log.d(TAG, "Drawable not found: " + drawableName);
            }
        }

        // 4) Fallback
        Glide.with(ctx).load(placeholder).into(iv);
    }

    /**
     * Loại bỏ extension từ tên file
     */
    private static String stripExtension(String filename) {
        if (filename == null) return "";
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(0, lastDot);
        }
        return filename;
    }
}
