# Tài Liệu Kỹ Thuật - Milktea App

## 📋 Mục Lục

1. [Tổng Quan](#tổng-quan)
2. [Kiến Trúc Hệ Thống](#kiến-trúc-hệ-thống)
3. [Công Nghệ Sử Dụng](#công-nghệ-sử-dụng)
4. [Chức Năng Người Dùng (User)](#chức-năng-người-dùng-user)
5. [Chức Năng Quản Trị (Admin)](#chức-năng-quản-trị-admin)
6. [Cấu Trúc Database](#cấu-trúc-database)
7. [Hệ Thống Thanh Toán](#hệ-thống-thanh-toán)
8. [Xác Thực và Bảo Mật](#xác-thực-và-bảo-mật)
9. [Repository Pattern](#repository-pattern)
10. [Cấu Trúc Code](#cấu-trúc-code)

---

## 📱 Tổng Quan

**Milktea App** là ứng dụng Android để đặt hàng trà sữa và đồ uống trực tuyến. Ứng dụng hỗ trợ hai loại người dùng:

- **User (Người dùng)**: Đặt hàng, quản lý giỏ hàng, theo dõi đơn hàng
- **Admin (Quản trị viên)**: Quản lý sản phẩm, đơn hàng, người dùng, thống kê

### Thông Tin Kỹ Thuật

- **Package**: `com.pro.milkteaapp`
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 15)
- **Compile SDK**: 35
- **Java Version**: 17
- **Build Tool**: Gradle (Kotlin DSL)

---

## 🏗️ Kiến Trúc Hệ Thống

### Architecture Pattern

Ứng dụng sử dụng **Repository Pattern** để tách biệt logic truy cập dữ liệu khỏi UI layer:

```
UI Layer (Activities/Fragments)
    ↓
Repository Layer (Data Access)
    ↓
Firebase Firestore (Database)
```

### Cấu Trúc Package

```
com.pro.milkteaapp/
├── activity/          # Activities cho User
│   └── admin/         # Activities cho Admin
├── fragment/          # Fragments cho User
│   ├── admin/        # Fragments cho Admin
│   ├── bottomsheet/  # Bottom sheets
│   ├── dialog/       # Dialog fragments
│   └── pickersheet/  # Picker sheets
├── adapter/          # RecyclerView adapters
├── models/           # Data models
├── repository/       # Repository classes
├── utils/            # Utility classes
├── constants/        # Constants
├── payment/          # Payment gateways
└── generator/        # ID generators
```

---

## 🛠️ Công Nghệ Sử Dụng

### Core Libraries

- **Firebase Authentication**: Xác thực người dùng
- **Firebase Firestore**: Database NoSQL realtime
- **Material Design Components**: UI components
- **ViewBinding**: Type-safe view references
- **Glide**: Image loading và caching

### Android Jetpack

- **AppCompat**: Backward compatibility
- **RecyclerView**: List rendering
- **ViewPager2**: Tab navigation
- **Navigation Component**: Navigation handling
- **Lifecycle**: Lifecycle-aware components

### Payment Integration

- **Momo Payment Gateway**: Tích hợp thanh toán Momo
- **VNPAY Payment Gateway**: Tích hợp thanh toán VNPAY
- **Card Payment Gateway**: Thanh toán bằng thẻ

---

## 👤 Chức Năng Người Dùng (User)

### 1. Xác Thực (Authentication)

#### Đăng Ký (`RegisterActivity`)
- Đăng ký tài khoản mới với email/password
- Tự động tạo user document trong Firestore với ID format `USRxxxxx`
- Gán role mặc định: `user`
- Lưu session sau khi đăng ký thành công

#### Đăng Nhập (`LoginActivity`)
- Đăng nhập bằng email/password
- Tự động điều hướng dựa trên role:
  - `user` → `MainActivity`
  - `admin` → `AdminMainActivity`
- Quản lý session với `SessionManager`

#### Quên Mật Khẩu (`ForgotPasswordActivity`)
- Gửi email reset password qua Firebase Auth

#### Đổi Mật Khẩu (`ChangePasswordActivity`)
- Đổi mật khẩu cho user đã đăng nhập

### 2. Trang Chủ (`HomeFragment`)

#### Tính Năng
- Hiển thị sản phẩm nổi bật (featured products)
- Hiển thị combo đang active
- Hiển thị đánh giá gần đây từ người dùng khác
- Navigation đến các tab khác

#### Data Flow
- Sử dụng `ProductRepository.fetchFeaturedProduct()` để lấy featured products
- Sử dụng `ComboRepository.fetchActiveCombos()` để lấy combos
- Realtime updates khi có thay đổi

### 3. Sản Phẩm (`ProductFragment`)

#### Tính Năng
- **Danh sách sản phẩm**: Hiển thị tất cả sản phẩm theo category
- **Tìm kiếm**: Tìm kiếm sản phẩm theo tên, category
- **Lọc theo category**: Lọc sản phẩm theo danh mục
- **Thêm vào giỏ hàng**: Chọn size, đường, đá, topping, số lượng

#### UI Components
- Grid layout với 2 cột
- Section headers cho mỗi category
- Search bar với realtime filtering
- Drawer menu để chọn category

#### Data Management
- Sử dụng `ProductRepository.listenAllProduct()` cho realtime updates
- Sử dụng `CategoriesRepository.fetchActiveCategories()` để lấy categories
- Sử dụng `CartManager` để quản lý giỏ hàng

### 4. Giỏ Hàng (`CartFragment`)

#### Tính Năng
- **Xem giỏ hàng**: Hiển thị sản phẩm và combo đã thêm
- **Cập nhật số lượng**: Tăng/giảm số lượng sản phẩm
- **Xóa sản phẩm**: Xóa sản phẩm khỏi giỏ hàng
- **Lọc**: Lọc theo sản phẩm thường, combo, hoặc tất cả
- **Thanh toán**: Chuyển đến màn hình checkout

#### Cart Management
- Sử dụng `CartManager` (singleton) để quản lý state
- Hỗ trợ cả `CartItem` (sản phẩm thường) và `ComboCartItem` (combo)
- Tự động tính tổng tiền

### 5. Thanh Toán (`CheckoutActivity`)

#### Quy Trình Thanh Toán

1. **Chọn địa chỉ giao hàng** (`AddressPickerSheet`)
   - Chọn từ danh sách địa chỉ đã lưu
   - Thêm địa chỉ mới
   - Sửa địa chỉ hiện có

2. **Chọn phương thức vận chuyển** (`ShippingPickerSheet`)
   - Tiêu chuẩn
   - Nhanh
   - Express

3. **Áp dụng voucher** (`VoucherListPickerSheet`)
   - Xem danh sách voucher khả dụng
   - Áp dụng voucher để giảm giá

4. **Chọn phương thức thanh toán** (`PaymentMethodPickerSheet`)
   - COD (Thanh toán khi nhận hàng)
   - Momo
   - VNPAY
   - Thẻ

5. **Xác nhận và đặt hàng**
   - Tạo order document trong Firestore
   - Ghi nhận voucher usage (nếu có)
   - Xử lý thanh toán online (nếu cần)

#### Order Creation
- Sử dụng `OrderCreator` để tạo order
- Sử dụng `VoucherUsageRecorder` để ghi nhận voucher usage
- Tự động generate order ID với format `ORDxxxxx`

### 6. Đơn Hàng (`OrdersFragment`, `OrderDetailActivity`)

#### Tính Năng
- **Danh sách đơn hàng**: Hiển thị tất cả đơn hàng của user
- **Lọc theo trạng thái**: PENDING, FINISHED, CANCELLED
- **Chi tiết đơn hàng**: Xem thông tin chi tiết đơn hàng
- **Hủy đơn hàng**: Hủy đơn hàng ở trạng thái PENDING
- **Đánh giá đơn hàng**: Đánh giá sau khi đơn hàng FINISHED

#### Order Status Flow
```
PENDING → FINISHED (Admin xác nhận)
PENDING → CANCELLED (User/Admin hủy)
```

### 7. Hồ Sơ (`ProfileFragment`, `EditProfileActivity`)

#### Tính Năng
- **Xem thông tin**: Email, tên, số điện thoại, địa chỉ
- **Chỉnh sửa hồ sơ**: Cập nhật thông tin cá nhân
- **Quản lý địa chỉ**: Thêm, sửa, xóa địa chỉ giao hàng
- **Điểm thưởng**: Xem điểm loyalty và reward points
- **Hạng thành viên**: Xem hạng loyalty tier
- **Đổi mật khẩu**: Thay đổi mật khẩu
- **Đăng xuất**: Đăng xuất khỏi tài khoản

#### Loyalty System
- **Loyalty Points**: Điểm để thăng hạng (Bronze, Silver, Gold, Platinum)
- **Reward Points**: Điểm để đổi quà
- **Loyalty Tiers**: 
  - Bronze: 0-1000 điểm
  - Silver: 1001-5000 điểm
  - Gold: 5001-10000 điểm
  - Platinum: 10000+ điểm

### 8. Tin Nhắn (`MessagesFragment`)

#### Tính Năng
- **Xem tin nhắn**: Xem tin nhắn từ admin
- **Chat với admin**: Gửi tin nhắn cho admin
- **Thông báo đơn hàng**: Nhận thông báo về trạng thái đơn hàng

#### Data Structure
- Messages được lưu trong subcollection `users/{userId}/inbox/`
- Realtime updates khi có tin nhắn mới

### 9. Voucher (`RedeemVoucherActivity`)

#### Tính Năng
- **Xem voucher**: Danh sách voucher đã nhận
- **Đổi voucher**: Đổi điểm thưởng lấy voucher
- **Sử dụng voucher**: Áp dụng voucher khi thanh toán

---

## 👨‍💼 Chức Năng Quản Trị (Admin)

### 1. Dashboard (`AdminMainActivity`)

#### Navigation Tabs
- **Quản lý**: Quản lý sản phẩm, combo, categories, vouchers, toppings, ratings
- **Đơn hàng**: Xem và quản lý đơn hàng
- **Người dùng**: Quản lý người dùng
- **Thống kê**: Xem thống kê doanh thu, đơn hàng
- **Chat**: Chat với người dùng

### 2. Quản Lý Sản Phẩm (`AdminProductFragment`, `AdminProductEditorActivity`)

#### Tính Năng
- **Danh sách sản phẩm**: Xem tất cả sản phẩm
- **Thêm sản phẩm**: Tạo sản phẩm mới
- **Sửa sản phẩm**: Cập nhật thông tin sản phẩm
- **Xóa sản phẩm**: Xóa sản phẩm (soft delete)
- **Tìm kiếm**: Tìm kiếm sản phẩm
- **Đánh dấu nổi bật**: Đánh dấu sản phẩm featured

#### Product Fields
- Tên, mô tả, giá, category
- Hình ảnh (URL)
- Trạng thái (active/inactive)
- Featured flag

### 3. Quản Lý Combo (`AdminCombosFragment`, `AdminComboEditorActivity`)

#### Tính Năng
- **Danh sách combo**: Xem tất cả combo
- **Thêm combo**: Tạo combo mới với nhiều sản phẩm
- **Sửa combo**: Cập nhật thông tin combo
- **Xóa combo**: Xóa combo
- **Tính giá tự động**: Tự động tính giá combo dựa trên % giảm giá

#### Combo Structure
- Tên, mô tả, hình ảnh
- Danh sách sản phẩm trong combo
- % giảm giá
- Giá gốc và giá sau giảm

### 4. Quản Lý Đơn Hàng (`AdminOrdersFragment`, `AdminOrderDetailActivity`)

#### Tính Năng
- **Danh sách đơn hàng**: Xem tất cả đơn hàng
- **Lọc theo trạng thái**: PENDING, FINISHED, CANCELLED
- **Chi tiết đơn hàng**: Xem thông tin chi tiết đơn hàng
- **Cập nhật trạng thái**: 
  - PENDING → FINISHED (xác nhận hoàn thành)
  - PENDING → CANCELLED (hủy đơn hàng)
- **Cộng điểm**: Tự động cộng điểm khi đơn hàng FINISHED

#### Order Management
- Xem thông tin khách hàng
- Xem danh sách sản phẩm/combo trong đơn
- Xem địa chỉ giao hàng
- Xem phương thức thanh toán
- Cập nhật trạng thái đơn hàng

### 5. Quản Lý Người Dùng (`AdminUsersFragment`)

#### Tính Năng
- **Danh sách người dùng**: Xem tất cả người dùng
- **Tìm kiếm**: Tìm kiếm theo tên, email
- **Xem chi tiết**: Xem thông tin chi tiết người dùng
- **Quản lý điểm**: Xem và quản lý loyalty/reward points
- **Quản lý hạng**: Xem hạng loyalty tier

### 6. Quản Lý Categories (`CategoryManageFragment`)

#### Tính Năng
- **Danh sách categories**: Xem tất cả categories
- **Thêm category**: Tạo category mới
- **Sửa category**: Cập nhật thông tin category
- **Kích hoạt/Vô hiệu hóa**: Bật/tắt category

### 7. Quản Lý Vouchers (`VoucherManageFragment`)

#### Tính Năng
- **Danh sách vouchers**: Xem tất cả vouchers
- **Thêm voucher**: Tạo voucher mới
- **Sửa voucher**: Cập nhật thông tin voucher
- **Quản lý thời gian**: Thiết lập thời gian hiệu lực
- **Quản lý điều kiện**: Thiết lập điều kiện sử dụng (min order, discount type/value)

#### Voucher Types
- **Percentage**: Giảm theo phần trăm
- **Fixed**: Giảm số tiền cố định

### 8. Quản Lý Toppings (`ToppingManageFragment`)

#### Tính Năng
- **Danh sách toppings**: Xem tất cả toppings
- **Thêm topping**: Tạo topping mới
- **Sửa topping**: Cập nhật thông tin topping
- **Quản lý giá**: Thiết lập giá cho topping
- **Quản lý categories**: Gán topping vào categories

### 9. Quản Lý Đánh Giá (`RatingManageFragment`)

#### Tính Năng
- **Danh sách đánh giá**: Xem tất cả đánh giá từ người dùng
- **Lọc theo sản phẩm**: Xem đánh giá theo sản phẩm
- **Xóa đánh giá**: Xóa đánh giá không phù hợp

### 10. Thống Kê (`AdminStatisticsFragment`)

#### Tính Năng
- **Doanh thu**: Thống kê doanh thu theo ngày/tuần/tháng
- **Số đơn hàng**: Thống kê số lượng đơn hàng
- **Sản phẩm bán chạy**: Top sản phẩm bán chạy nhất
- **Người dùng mới**: Thống kê người dùng mới đăng ký

### 11. Chat (`AdminChatFragment`, `ChatActivity`)

#### Tính Năng
- **Danh sách chat**: Xem danh sách người dùng đang chat
- **Chat với người dùng**: Trả lời tin nhắn từ người dùng
- **Gửi thông báo**: Gửi thông báo cho người dùng

---

## 🗄️ Cấu Trúc Database

### Firestore Collections

#### 1. `users` Collection
```javascript
users/
  {userId}/  // Format: USRxxxxx
    - uid: string
    - email: string
    - fullName: string
    - role: "user" | "admin"
    - phone: string
    - address: string
    - avatar: string
    - loyaltyTier: string
    - loyaltyPoints: number
    - rewardPoints: number
    - totalSpent: number
    - createdAt: timestamp
    - updatedAt: timestamp
    
    // Subcollections:
    addresses/        // Địa chỉ giao hàng
    voucher_usages/  // Lịch sử sử dụng voucher
    inbox/           // Tin nhắn từ admin
```

#### 2. `products` Collection
```javascript
products/
  {productId}/
    - id: string
    - name: string
    - price: number
    - category: string
    - imageUrl: string
    - description: string
    - status: "active" | "inactive"
    - searchableName: string
    - featured: boolean
    - createdByUid: string
    - createdAt: timestamp
    - updatedAt: timestamp
```

#### 3. `orders` Collection
```javascript
orders/
  {orderId}/  // Format: ORDxxxxx
    - orderId: string
    - userId: string
    - status: "PENDING" | "FINISHED" | "CANCELLED"
    - items: array
      - productId: string
      - name: string
      - quantity: number
      - price: number
      - size: string
      - sugar: string
      - ice: string
      - toppings: array
      - type: "product" | "combo"
    - subtotal: number
    - discount: number
    - shippingFee: number
    - finalTotal: number
    - address: string
    - addressObj: object
    - paymentMethod: string
    - paymentStatus: string
    - voucherCode: string
    - createdAt: timestamp
    - finishedAt: timestamp
    - cancelledAt: timestamp
```

#### 4. `combos` Collection
```javascript
combos/
  {comboId}/  // Format: CMBxxxxx
    - id: string
    - name: string
    - description: string
    - imageUrl: string
    - productIds: array<string>
    - originalPrice: number
    - discountPercent: number
    - price: number
    - status: "active" | "inactive"
    - searchableName: string
    - createdAt: timestamp
    - updatedAt: timestamp
```

#### 5. `categories` Collection
```javascript
categories/
  {categoryId}/
    - id: string
    - name: string
    - active: boolean
    - createdAt: timestamp
    - updatedAt: timestamp
```

#### 6. `vouchers` Collection
```javascript
vouchers/
  {voucherId}/
    - id: string
    - code: string
    - discountType: "percentage" | "fixed"
    - discountValue: number
    - minOrder: number
    - startAt: timestamp
    - endAt: timestamp
    - isActive: boolean
    - createdAt: timestamp
    - updatedAt: timestamp
```

#### 7. `toppings` Collection
```javascript
toppings/
  {toppingId}/
    - id: string
    - name: string
    - price: number
    - active: boolean
    - categories: array<string>
    - createdAt: timestamp
    - updatedAt: timestamp
```

#### 8. `reviews` Collection
```javascript
reviews/
  {reviewId}/
    - id: string
    - userId: string
    - productId: string
    - orderId: string
    - rating: number (1-5)
    - comment: string
    - createdAt: timestamp
```

#### 9. `meta` Collection
```javascript
meta/
  counters/
    - productSeq: number
    - orderSeq: number
    - userSeq: number
    - comboSeq: number
```

### Indexes

Firestore yêu cầu composite indexes cho các queries phức tạp:
- `products`: `status + featured + name`
- `orders`: `userId + status + createdAt`
- `vouchers`: `isActive + startAt + endAt`

---

## 💳 Hệ Thống Thanh Toán

### Payment Methods

1. **COD (Cash on Delivery)**
   - Thanh toán khi nhận hàng
   - Không cần xử lý online payment

2. **Momo**
   - Tích hợp Momo Payment Gateway
   - Deep link để nhận kết quả thanh toán

3. **VNPAY**
   - Tích hợp VNPAY Payment Gateway
   - Deep link để nhận kết quả thanh toán

4. **Card**
   - Thanh toán bằng thẻ tín dụng/ghi nợ
   - Tích hợp Card Payment Gateway

### Payment Flow

```
User chọn payment method
    ↓
CheckoutActivity.processOnlinePayment()
    ↓
PaymentActivity (xử lý payment)
    ↓
Payment Gateway (Momo/VNPAY/Card)
    ↓
Deep link callback
    ↓
CheckoutActivity.onActivityResult()
    ↓
Tạo order với transactionId
```

### Payment Status

- **PENDING**: Đang chờ thanh toán
- **SUCCESS**: Thanh toán thành công
- **FAILED**: Thanh toán thất bại
- **CANCELLED**: Người dùng hủy thanh toán

---

## 🔐 Xác Thực và Bảo Mật

### Authentication Flow

1. **Đăng ký/Đăng nhập**
   - Sử dụng Firebase Authentication
   - Tự động tạo user document trong Firestore

2. **Session Management**
   - Sử dụng `SessionManager` với SharedPreferences
   - Lưu UID và role
   - Custom UID format: `USRxxxxx`

3. **Role-Based Access**
   - `EntryActivity` kiểm tra role và điều hướng:
     - `user` → `MainActivity`
     - `admin` → `AdminMainActivity`
   - Admin activities kiểm tra quyền trước khi cho phép truy cập

### Security Measures

- **ProGuard/R8**: Bật minify cho release builds
- **API Keys**: Cần cấu hình đúng cách (không hardcode)
- **Input Validation**: Validate user input trước khi lưu vào database
- **Error Handling**: Xử lý lỗi một cách an toàn, không expose thông tin nhạy cảm

---

## 📦 Repository Pattern

### Repository Classes

1. **ProductRepository**
   - `fetchAllProduct()`: Lấy tất cả sản phẩm
   - `listenAllProduct()`: Lắng nghe realtime sản phẩm
   - `fetchProductById()`: Lấy sản phẩm theo ID
   - `fetchProductByCategory()`: Lấy sản phẩm theo category
   - `fetchFeaturedProduct()`: Lấy sản phẩm nổi bật
   - `searchProduct()`: Tìm kiếm sản phẩm
   - `createProduct()`: Tạo sản phẩm mới
   - `updateProduct()`: Cập nhật sản phẩm
   - `deleteProduct()`: Xóa sản phẩm

2. **ComboRepository**
   - `fetchAllCombo()`: Lấy tất cả combo
   - `listenAllCombo()`: Lắng nghe realtime combo
   - `fetchComboById()`: Lấy combo theo ID
   - `fetchActiveCombo()`: Lấy combo đang active
   - `createCombo()`: Tạo combo mới
   - `updateCombo()`: Cập nhật combo
   - `deleteCombo()`: Xóa combo

3. **OrdersRepository**
   - `fetchOrdersByUserId()`: Lấy đơn hàng của user
   - `listenOrdersByUserId()`: Lắng nghe realtime đơn hàng
   - `fetchOrderDocument()`: Lấy chi tiết đơn hàng
   - `updateOrderStatus()`: Cập nhật trạng thái đơn hàng
   - `cancelOrder()`: Hủy đơn hàng

4. **UsersRepository**
   - `fetchUserByAuthUid()`: Lấy user theo Firebase UID
   - `fetchUserById()`: Lấy user theo custom UID
   - `fetchAllUsers()`: Lấy tất cả users (admin)
   - `updateUser()`: Cập nhật thông tin user

5. **CategoriesRepository**
   - `fetchActiveCategories()`: Lấy categories đang active
   - `listenActiveCategories()`: Lắng nghe realtime categories

6. **VouchersRepository**
   - `fetchVoucherByCode()`: Lấy voucher theo code
   - `fetchActiveVouchers()`: Lấy vouchers đang active

7. **ToppingsRepository**
   - `fetchActiveToppings()`: Lấy toppings đang active
   - `fetchToppingsByCategory()`: Lấy toppings theo category

8. **AddressRepository**
   - `fetchAddresses()`: Lấy danh sách địa chỉ
   - `addAddress()`: Thêm địa chỉ mới
   - `updateAddress()`: Cập nhật địa chỉ
   - `deleteAddress()`: Xóa địa chỉ
   - `fetchDefault()`: Lấy địa chỉ mặc định

### BaseRepository

Tất cả repositories có thể extend `BaseRepository` để sử dụng các utility methods:
- `parseQuerySnapshot()`: Parse QuerySnapshot thành List
- `addTimestamps()`: Thêm createdAt/updatedAt
- `ensureIdSet()`: Đảm bảo ID được set

---

## 📁 Cấu Trúc Code

### Models

- `Product`: Sản phẩm
- `Combo`: Combo sản phẩm
- `CartItem`: Item trong giỏ hàng (sản phẩm)
- `ComboCartItem`: Item trong giỏ hàng (combo)
- `Order`: Đơn hàng
- `OrderLine`: Dòng trong đơn hàng
- `User`: Người dùng
- `Address`: Địa chỉ
- `Voucher`: Voucher
- `Topping`: Topping
- `Category`: Danh mục
- `Review`: Đánh giá

### Utils

- `CartManager`: Quản lý giỏ hàng (singleton)
- `MoneyUtils`: Format tiền tệ
- `ErrorHandler`: Xử lý lỗi chuẩn hóa
- `CartFilterHelper`: Helper cho filter giỏ hàng
- `OrderPayloadBuilder`: Build order payload
- `OrderCreator`: Tạo order
- `VoucherUsageRecorder`: Ghi nhận voucher usage
- `ProductSearchHelper`: Helper cho search/filter sản phẩm

### Constants

- `FirestoreConstants`: Tất cả collection names và field names
- `SessionManager`: Quản lý session

### Generators

- `UserIDGenerator`: Generate user ID (USRxxxxx)
- `ProductIDGenerator`: Generate product ID (PRDxxxxx)
- `OrderIDGenerator`: Generate order ID (ORDxxxxx)
- `ComboIDGenerator`: Generate combo ID (CMBxxxxx)

---

## 🔄 Data Flow Examples

### Example 1: User đặt hàng

```
1. User thêm sản phẩm vào giỏ
   → CartManager.addCartItem()
   
2. User vào giỏ hàng
   → CartFragment hiển thị items từ CartManager
   
3. User click "Thanh toán"
   → CheckoutActivity mở
   
4. User chọn địa chỉ, shipping, voucher, payment
   → CheckoutBottomSheet
   
5. User xác nhận
   → OrderCreator.createOrder()
   → Tạo order document trong Firestore
   → VoucherUsageRecorder.recordVoucherUsageIfNeeded()
   → Xử lý payment (nếu online)
   → CartManager.clearAll()
```

### Example 2: Admin quản lý sản phẩm

```
1. Admin vào AdminProductFragment
   → ProductRepository.listenAllProduct()
   → Realtime updates
   
2. Admin thêm sản phẩm
   → AdminProductEditorActivity
   → ProductRepository.createProduct()
   → Tạo product document với ID PRDxxxxx
   
3. Admin sửa sản phẩm
   → AdminProductEditorActivity (edit mode)
   → ProductRepository.updateProduct()
   → Cập nhật product document
```

---

## 📝 Notes

### Best Practices

1. **Repository Pattern**: Luôn sử dụng Repository để truy cập data, không gọi Firestore trực tiếp
2. **Constants**: Sử dụng `FirestoreConstants` thay vì hardcode strings
3. **Error Handling**: Sử dụng `ErrorHandler` để xử lý lỗi nhất quán
4. **Null Safety**: Luôn kiểm tra `isAdded()` trước khi dùng `getContext()` trong Fragment
5. **Lifecycle**: Remove listeners trong `onDestroyView()` hoặc `onDestroy()`

### Performance

- **Realtime Listeners**: Chỉ sử dụng khi cần realtime updates
- **Image Loading**: Sử dụng Glide với caching
- **Pagination**: Có thể implement pagination cho danh sách dài
- **Offline Support**: Firestore hỗ trợ offline persistence

### Future Improvements

- Thêm ViewModel layer (MVVM pattern)
- Thêm Unit Tests
- Implement pagination
- Thêm push notifications
- Cải thiện offline support
- Thêm analytics

---

## 📞 Support

Để biết thêm chi tiết về implementation, xem:
- `repository/README.md`: Hướng dẫn sử dụng Repository Pattern
- `payment/PAYMENT_FLOW.md`: Chi tiết về payment flow
- `PHAN_TICH_SAU_CAU_TRUC_FOLDER.md`: Phân tích cấu trúc code
- `BAO_CAO_VAN_DE.md`: Báo cáo các vấn đề đã được xử lý

---

**Version**: 1.0  
**Last Updated**: 2024  
**Author**: Development Team

