# Repository Pattern - Hướng dẫn sử dụng

## Tổng quan

Project đã được chuẩn hóa với Repository Pattern để:
- Tập trung hóa logic truy cập dữ liệu
- Dễ dàng test và bảo trì
- Tránh hardcode collection/field names
- Chuẩn hóa error handling

## Cấu trúc

### Constants
- `FirestoreConstants.java`: Tập trung tất cả collection names và field names

### Repositories
- `ProductsRepository`: Quản lý products
- `CategoriesRepository`: Quản lý categories
- `OrdersRepository`: Quản lý orders
- `ReviewsRepository`: Quản lý reviews
- `VouchersRepository`: Quản lý vouchers
- `ToppingsRepository`: Quản lý toppings
- `UsersRepository`: Quản lý users
- `AddressRepository`: Quản lý addresses (đã có sẵn)
- `AuthRepository`: Quản lý authentication (đã có sẵn)

## Cách sử dụng

### Ví dụ 1: Lấy tất cả products

```java
ProductsRepository repository = new ProductsRepository();

repository.fetchAllProducts(new ProductsRepository.OnProductsListener() {
    @Override
    public void onProductsReceived(@NonNull List<Products> products) {
        // Xử lý danh sách products
        adapter.submitList(products);
    }

    @Override
    public void onError(@NonNull Exception error) {
        // Xử lý lỗi
        Log.e(TAG, "Error fetching products", error);
        Toast.makeText(context, "Lỗi tải sản phẩm", Toast.LENGTH_SHORT).show();
    }
});
```

### Ví dụ 2: Lắng nghe realtime products

```java
ProductsRepository repository = new ProductsRepository();

ListenerRegistration registration = repository.listenAllProducts(
    new ProductsRepository.OnProductsListener() {
        @Override
        public void onProductsReceived(@NonNull List<Products> products) {
            // Tự động cập nhật khi có thay đổi
            adapter.submitList(products);
        }

        @Override
        public void onError(@NonNull Exception error) {
            Log.e(TAG, "Error listening products", error);
        }
    }
);

// Nhớ remove listener khi không cần nữa (trong onDestroy/onDestroyView)
@Override
public void onDestroyView() {
    super.onDestroyView();
    if (registration != null) {
        registration.remove();
    }
}
```

### Ví dụ 3: Lấy products theo category

```java
CategoriesRepository categoriesRepo = new CategoriesRepository();
ProductsRepository productsRepo = new ProductsRepository();

// Lấy categories trước
categoriesRepo.fetchActiveCategories(new CategoriesRepository.OnCategoriesListener() {
    @Override
    public void onCategoriesReceived(@NonNull List<Category> categories) {
        // Sau đó lấy products theo category đầu tiên
        if (!categories.isEmpty()) {
            String firstCategory = categories.get(0).getName();
            productsRepo.fetchProductsByCategory(firstCategory, 
                new ProductsRepository.OnProductsListener() {
                    @Override
                    public void onProductsReceived(@NonNull List<Products> products) {
                        // Xử lý products
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        // Xử lý lỗi
                    }
                });
        }
    }

    @Override
    public void onError(@NonNull Exception error) {
        // Xử lý lỗi
    }
});
```

### Ví dụ 4: Lấy orders của user

```java
OrdersRepository repository = new OrdersRepository();
SessionManager sessionManager = new SessionManager(getContext());
String userId = sessionManager.getUid();

if (userId != null) {
    repository.fetchOrdersByUserId(userId, new OrdersRepository.OnOrdersListener() {
        @Override
        public void onOrdersReceived(@NonNull List<Order> orders) {
            // Hiển thị orders
            ordersAdapter.submitList(orders);
        }

        @Override
        public void onError(@NonNull Exception error) {
            Log.e(TAG, "Error fetching orders", error);
        }
    });
}
```

## Migration Guide

### Trước (cũ):
```java
db.collection("products")
    .orderBy("category", Query.Direction.ASCENDING)
    .get()
    .addOnSuccessListener(snapshot -> {
        // Xử lý
    });
```

### Sau (mới):
```java
ProductsRepository repository = new ProductsRepository();
repository.fetchAllProducts(new ProductsRepository.OnProductsListener() {
    @Override
    public void onProductsReceived(@NonNull List<Products> products) {
        // Xử lý
    }

    @Override
    public void onError(@NonNull Exception error) {
        // Xử lý lỗi
    }
});
```

## Lợi ích

1. **Tập trung hóa**: Tất cả logic Firestore ở một nơi
2. **Dễ test**: Có thể mock repository để test
3. **Tránh hardcode**: Dùng constants thay vì string literal
4. **Chuẩn hóa**: Tất cả repository có cùng pattern
5. **Dễ bảo trì**: Thay đổi logic chỉ cần sửa một chỗ

## Lưu ý

- Luôn remove listener trong `onDestroyView()` hoặc `onDestroy()`
- Xử lý error một cách nhất quán
- Sử dụng constants từ `FirestoreConstants` thay vì hardcode strings

