# Tài liệu Chuẩn hóa Code - MilkteaApp Android

## Tổng quan

Project đã được chuẩn hóa với các cải tiến sau:
1. ✅ Tạo package `constants` để tập trung tất cả collection/field names
2. ✅ Tạo đầy đủ các Repository classes theo Repository Pattern
3. ✅ Refactor ProductFragment làm ví dụ sử dụng Repository
4. ✅ Tạo tài liệu hướng dẫn sử dụng

## Cấu trúc mới

### 1. Package Constants
```
app/src/main/java/com/pro/milkteaapp/constants/
└── FirestoreConstants.java
```

**Mục đích**: Tập trung tất cả tên collection và field của Firestore để:
- Tránh hardcode strings
- Dễ bảo trì và thay đổi
- Tránh lỗi typo

**Sử dụng**:
```java
// Thay vì:
db.collection("products")

// Dùng:
db.collection(FirestoreConstants.COLLECTION_PRODUCTS)
```

### 2. Package Repository
```
app/src/main/java/com/pro/milkteaapp/repository/
├── ProductsRepository.java
├── CategoriesRepository.java
├── OrdersRepository.java
├── ReviewsRepository.java
├── VouchersRepository.java
├── ToppingsRepository.java
├── UsersRepository.java
├── AddressRepository.java (đã có sẵn)
├── AuthRepository.java (đã có sẵn)
└── README.md (hướng dẫn sử dụng)
```

**Mục đích**: Tập trung hóa logic truy cập dữ liệu Firestore

**Lợi ích**:
- Dễ test (có thể mock repository)
- Code gọn gàng hơn ở Activity/Fragment
- Chuẩn hóa error handling
- Dễ bảo trì và mở rộng

## Ví dụ Migration

### Trước (cũ):
```java
// Trong Fragment/Activity
db.collection("products")
    .orderBy("category", Query.Direction.ASCENDING)
    .orderBy("name", Query.Direction.ASCENDING)
    .get()
    .addOnSuccessListener(snapshot -> {
        List<Products> products = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Products p = doc.toObject(Products.class);
            if (p != null) {
                if (p.getId() == null) p.setId(doc.getId());
                products.add(p);
            }
        }
        adapter.submitList(products);
    })
    .addOnFailureListener(e -> {
        Log.e(TAG, "Error", e);
    });
```

### Sau (mới):
```java
// Trong Fragment/Activity
ProductsRepository repository = new ProductsRepository();
repository.fetchAllProducts(new ProductsRepository.OnProductsListener() {
    @Override
    public void onProductsReceived(@NonNull List<Products> products) {
        adapter.submitList(products);
    }

    @Override
    public void onError(@NonNull Exception error) {
        Log.e(TAG, "Error", error);
    }
});
```

## Các Repository đã tạo

### 1. ProductsRepository
- `fetchAllProducts()`: Lấy tất cả products
- `listenAllProducts()`: Lắng nghe realtime tất cả products
- `fetchProductsByCategory()`: Lấy products theo category
- `fetchProductsByStatus()`: Lấy products theo status
- `fetchProductById()`: Lấy một product theo ID
- `searchProducts()`: Tìm kiếm products
- `fetchTopProducts()`: Lấy top products

### 2. CategoriesRepository
- `fetchActiveCategories()`: Lấy categories đang active
- `listenActiveCategories()`: Lắng nghe realtime categories active
- `fetchAllCategories()`: Lấy tất cả categories
- `fetchCategoryById()`: Lấy một category theo ID

### 3. OrdersRepository
- `fetchOrdersByUserId()`: Lấy orders của user
- `listenOrdersByUserId()`: Lắng nghe realtime orders của user
- `fetchOrdersByStatus()`: Lấy orders theo status
- `fetchUserOrdersByStatus()`: Lấy orders của user theo status
- `fetchOrderById()`: Lấy một order theo ID
- `fetchAllOrders()`: Lấy tất cả orders (admin)
- `listenAllOrders()`: Lắng nghe realtime tất cả orders (admin)

### 4. ReviewsRepository
- `fetchReviewsByProductId()`: Lấy reviews của product
- `listenReviewsByProductId()`: Lắng nghe realtime reviews
- `fetchReviewsByOrderId()`: Lấy reviews của order
- `fetchTopReviews()`: Lấy top reviews

### 5. VouchersRepository
- `fetchActiveVouchers()`: Lấy vouchers đang active
- `listenActiveVouchers()`: Lắng nghe realtime vouchers active
- `fetchAllVouchers()`: Lấy tất cả vouchers (admin)
- `fetchVoucherByCode()`: Lấy voucher theo code
- `fetchVoucherById()`: Lấy voucher theo ID

### 6. ToppingsRepository
- `fetchActiveToppings()`: Lấy toppings đang active
- `listenActiveToppings()`: Lắng nghe realtime toppings active
- `fetchToppingsByCategory()`: Lấy toppings theo category
- `fetchAllToppings()`: Lấy tất cả toppings (admin)
- `fetchToppingById()`: Lấy topping theo ID

### 7. UsersRepository
- `fetchUserById()`: Lấy user theo ID
- `fetchUserByAuthUid()`: Lấy user theo Firebase Auth UID
- `fetchUserByEmail()`: Lấy user theo email
- `fetchAllUsers()`: Lấy tất cả users (admin)
- `listenAllUsers()`: Lắng nghe realtime users (admin)
- `fetchUsersByRole()`: Lấy users theo role

## File đã refactor

### ProductFragment.java
- ✅ Đã refactor `loadCategoriesThenListenProducts()` để dùng `CategoriesRepository`
- ✅ Đã refactor `listenProductsRealtime()` để dùng `ProductsRepository`
- ✅ Đã refactor `greetUser()` để dùng `UsersRepository`

## Các bước tiếp theo (chưa hoàn thành)

### 1. Refactor các Fragment/Activity khác
Các file cần refactor:
- `HomeFragment.java`
- `OrdersFragment.java`
- `CartFragment.java`
- `ProfileFragment.java`
- `CheckoutActivity.java`
- `OrderDetailActivity.java`
- Các Fragment trong `admin/` package

### 2. Tối ưu Error Handling
- Tạo một ErrorHandler utility class
- Chuẩn hóa cách hiển thị lỗi cho user
- Logging pattern nhất quán

### 3. Tạo Base Repository (tùy chọn)
- Tạo interface hoặc base class cho các Repository
- Chia sẻ common methods (parse, error handling)

### 4. Unit Tests
- Viết unit tests cho các Repository
- Mock Firestore để test

## Lưu ý quan trọng

1. **Luôn remove listener**: Khi sử dụng realtime listener, nhớ remove trong `onDestroyView()` hoặc `onDestroy()`

2. **Sử dụng Constants**: Luôn dùng `FirestoreConstants` thay vì hardcode strings

3. **Error Handling**: Xử lý error một cách nhất quán trong toàn bộ app

4. **Backward Compatibility**: Các Repository vẫn tương thích với code cũ, migration có thể làm từng phần

## Tài liệu tham khảo

- Xem `app/src/main/java/com/pro/milkteaapp/repository/README.md` để biết chi tiết cách sử dụng
- Xem `ProductFragment.java` để xem ví dụ refactor

