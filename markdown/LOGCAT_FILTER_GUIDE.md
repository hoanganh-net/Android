# Hướng dẫn lọc Logcat để debug Combo trong Order Detail

## Cách lọc Logcat trong Android Studio

### 1. Mở Logcat
- Ở dưới cùng của Android Studio, click vào tab **"Logcat"**
- Hoặc: `View` → `Tool Windows` → `Logcat`

### 2. Lọc theo Tag
Trong ô filter (có icon kính lúp), nhập một trong các tag sau:

#### Để xem tất cả logs liên quan đến Order Detail:
```
OrderDetail
```

#### Để xem tất cả logs liên quan đến OrderLine parsing:
```
OrderLine
```

#### Để xem cả hai:
```
OrderDetail|OrderLine
```

### 3. Lọc theo Package Name
```
package:com.pro.milkteaapp
```

### 4. Kết hợp nhiều điều kiện
```
tag:OrderDetail|tag:OrderLine package:com.pro.milkteaapp
```

### 5. Lọc theo Log Level
Chỉ xem Debug logs:
```
tag:OrderDetail level:DEBUG
```

Chỉ xem Error logs:
```
tag:OrderDetail level:ERROR
```

### 6. Lọc theo Text Content
Tìm logs chứa từ khóa "combo":
```
combo
```

Tìm logs chứa "type":
```
type
```

## Các Log Tags được sử dụng trong code

1. **OrderDetail** - Logs trong `OrderDetailActivity.java`:
   - Items object type
   - Items list size
   - Item parsing details
   - Type field của từng item

2. **OrderLine** - Logs trong `OrderLine.java`:
   - Parsing item type và isCombo flag
   - Combo parsed details (name, comboId)
   - Quantity, UnitPrice, LineTotal

## Các bước debug

1. **Mở Logcat và set filter:**
   ```
   tag:OrderDetail|tag:OrderLine
   ```

2. **Tạo một đơn hàng có combo** (hoặc mở một đơn hàng đã có combo)

3. **Mở chi tiết đơn hàng** (OrderDetailActivity)

4. **Xem logs**, bạn sẽ thấy:
   - `OrderDetail: Items object type: ...`
   - `OrderDetail: Items list size: ...`
   - `OrderDetail: Item 0 type: ...`
   - `OrderDetail: Item 0 keys: [type, name, comboId, ...]`
   - `OrderDetail: Item 0 type field: combo`
   - `OrderLine: Parsing item - type: combo, isCombo: true`
   - `OrderLine: Combo parsed - name: ..., comboId: ...`
   - `OrderDetail: Item 0 parsed - name: ..., isCombo: true`

## Nếu không thấy combo trong logs

1. Kiểm tra xem combo có được lưu vào order không:
   - Mở Firestore Console
   - Tìm order document
   - Kiểm tra field `items` array
   - Xem có item nào có `type: "combo"` không

2. Nếu không có combo trong Firestore:
   - Kiểm tra `CheckoutActivity.placeOrder()` 
   - Xem `comboCartItems` có được thêm vào `itemsList` không

3. Nếu có combo trong Firestore nhưng không parse được:
   - Xem logs để biết format của item trong Firestore
   - So sánh với format trong `ComboCartItem.toOrderItemMap()`

## Ví dụ Log Output mong đợi

```
D/OrderDetail: Items object type: ArrayList
D/OrderDetail: Items list size: 2
D/OrderDetail: Item 0 type: HashMap
D/OrderDetail: Item 0 keys: [type, name, comboId, price, qty, unitPrice, lineTotal]
D/OrderDetail: Item 0 type field: combo
D/OrderLine: Parsing item - type: combo, isCombo: true
D/OrderLine: Combo parsed - name: Combo Trà Sữa, comboId: combo123
D/OrderLine: Quantity parsed: 2
D/OrderLine: UnitPrice parsed: 50000.0
D/OrderLine: LineTotal parsed: 100000.0
D/OrderDetail: Item 0 parsed - name: Combo Trà Sữa, isCombo: true
```

