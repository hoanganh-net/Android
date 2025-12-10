package com.pro.milkteaapp.utils;

import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;

import com.google.android.material.appbar.MaterialToolbar;

/**
 * Utility class để căn giữa toolbar title thực sự
 */
public class ToolbarUtils {
    
    /**
     * Căn giữa toolbar title, tính toán lại vị trí sau khi layout để đảm bảo căn giữa thực sự
     * @param toolbar Toolbar cần căn giữa title
     * @param title Text hiển thị
     * @param customTitleView TextView đã tạo (có thể null, sẽ tạo mới nếu null)
     * @param actionBar ActionBar của activity/fragment
     * @return TextView đã được căn giữa
     */
    public static TextView centerToolbarTitle(
            @NonNull MaterialToolbar toolbar,
            @NonNull String title,
            TextView customTitleView,
            ActionBar actionBar) {
        
        if (actionBar == null) return null;
        
        // Ẩn title mặc định trước
        actionBar.setDisplayShowTitleEnabled(false);
        
        // Xóa title view cũ nếu có
        if (customTitleView != null && customTitleView.getParent() != null) {
            toolbar.removeView(customTitleView);
        }
        
        // Tạo custom TextView để căn giữa tiêu đề
        TextView titleView = customTitleView != null ? customTitleView : new TextView(toolbar.getContext());
        titleView.setText(title);
        titleView.setTextAppearance(toolbar.getContext(), com.pro.milkteaapp.R.style.ToolbarTitleStyle);
        titleView.setTextColor(toolbar.getContext().getResources().getColor(com.pro.milkteaapp.R.color.white, toolbar.getContext().getTheme()));
        titleView.setGravity(android.view.Gravity.CENTER);
        
        // Sử dụng MATCH_PARENT để chiếm toàn bộ không gian, sau đó điều chỉnh padding
        MaterialToolbar.LayoutParams layoutParams = new MaterialToolbar.LayoutParams(
                MaterialToolbar.LayoutParams.MATCH_PARENT,
                MaterialToolbar.LayoutParams.WRAP_CONTENT
        );
        layoutParams.gravity = android.view.Gravity.CENTER_VERTICAL;
        titleView.setLayoutParams(layoutParams);
        
        // Đảm bảo title view được thêm vào sau và có elevation cao hơn để không bị che
        toolbar.addView(titleView);
        titleView.bringToFront();
        titleView.setElevation(1f); // Đảm bảo title view nằm trên các view khác
        
        // Sử dụng ViewTreeObserver để tính toán lại padding sau khi layout hoàn tất
        // Sử dụng post với delay nhỏ để đảm bảo tất cả các view đã được layout xong
        toolbar.post(() -> {
            toolbar.postDelayed(() -> {
                if (titleView == null || titleView.getParent() == null || toolbar.getWidth() == 0) {
                    return;
                }
                
                recalculateTitlePosition(toolbar, titleView);
            }, 100); // Delay để đảm bảo drawer và các view khác đã được setup
        });
        
        // Lắng nghe khi layout thay đổi (ví dụ khi drawer mở/đóng)
        ViewTreeObserver observer = toolbar.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (titleView == null || titleView.getParent() == null || toolbar.getWidth() == 0) {
                    return;
                }
                
                // Tính toán lại với delay nhỏ để tránh tính toán quá nhiều lần
                toolbar.postDelayed(() -> {
                    if (titleView != null && titleView.getParent() != null && toolbar.getWidth() > 0) {
                        recalculateTitlePosition(toolbar, titleView);
                    }
                }, 50);
            }
        });
        
        return titleView;
    }
    
    /**
     * Tính toán lại vị trí của title để căn giữa hoàn hảo
     */
    private static void recalculateTitlePosition(MaterialToolbar toolbar, TextView titleView) {
        int toolbarWidth = toolbar.getWidth();
        if (toolbarWidth == 0) return;
        
        // Tìm navigation icon view để lấy vị trí thực tế
        int leftOffset = 0;
        View navIconView = null;
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child != titleView && child.getVisibility() == View.VISIBLE) {
                // Kiểm tra xem có phải là navigation icon không (thường là ImageButton hoặc AppCompatImageButton)
                String className = child.getClass().getSimpleName();
                if (child instanceof android.widget.ImageButton ||
                    className.contains("ImageButton") ||
                    (child.getLeft() < toolbarWidth / 4 && child.getWidth() > 0 && child.getRight() > 0 && child.getRight() < toolbarWidth / 3)) {
                    navIconView = child;
                    leftOffset = Math.max(leftOffset, child.getRight());
                    break;
                }
            }
        }
        
        // Nếu không tìm thấy navigation icon view, ước tính dựa trên navigation icon
        if (navIconView == null && toolbar.getNavigationIcon() != null) {
            // Navigation icon thường có width khoảng 56dp (actionBarSize)
            // Nhưng có thể lớn hơn nếu có drawer, nên ước tính khoảng 64dp
            leftOffset = (int) (64 * toolbar.getContext().getResources().getDisplayMetrics().density);
        }
        
        // Tìm menu items hoặc ImageButton ở bên phải để tính rightOffset
        int rightOffset = 0;
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child != titleView && child != navIconView && child.getVisibility() == View.VISIBLE) {
                // Menu items hoặc ImageButton thường ở bên phải
                // Kiểm tra nếu view ở bên phải (sau 3/4 toolbar width)
                if (child.getRight() > toolbarWidth * 3 / 4 && child.getLeft() > 0) {
                    rightOffset = Math.max(rightOffset, toolbarWidth - child.getLeft());
                } else if (child instanceof android.widget.ImageButton || 
                          child.getClass().getSimpleName().contains("ImageButton")) {
                    // Nếu là ImageButton và ở bên phải (gravity END)
                    if (child.getRight() > toolbarWidth / 2) {
                        rightOffset = Math.max(rightOffset, toolbarWidth - child.getLeft());
                    }
                }
            }
        }
        
        // Tính padding để căn giữa text trong không gian còn lại
        // Sử dụng max của leftOffset và rightOffset để đảm bảo text được căn giữa hoàn hảo
        // Điều này đảm bảo text luôn ở giữa màn hình, không bị ảnh hưởng bởi navigation icon hay menu
        int maxOffset = Math.max(leftOffset, rightOffset);
        
        // Đảm bảo padding không quá lớn (tối đa 50% toolbar width để tránh text quá nhỏ)
        int maxPadding = toolbarWidth / 2;
        maxOffset = Math.min(maxOffset, maxPadding);
        
        // Đảm bảo padding không quá nhỏ (tối thiểu 56dp để tránh text bị che bởi navigation icon)
        int minPadding = (int) (56 * toolbar.getContext().getResources().getDisplayMetrics().density);
        maxOffset = Math.max(maxOffset, minPadding);
        
        // Giảm padding left đi 10 để điều chỉnh vị trí
        int leftPadding = Math.max(0, maxOffset - 10);
        int rightPadding = maxOffset;
        
        // Đặt padding để text được căn giữa
        titleView.setPadding(leftPadding, 0, rightPadding, 0);
    }
}

