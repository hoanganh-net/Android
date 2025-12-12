package com.pro.milkteaapp.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OrderLine implements Serializable {

    private String name;
    private String size;
    private List<Topping> toppings;  // sửa kiểu toppings sang List<Topping>

    private String sugar;
    private String ice;
    private String note;

    private int quantity;
    private double unitPrice;
    private double lineTotal;
    
    // Combo fields
    private boolean isCombo;
    private String comboId;
    private List<ComboProductDetail> comboProducts; // Chi tiết các sản phẩm trong combo

    public OrderLine() {}

    @NonNull
    @SuppressWarnings("unchecked")
    public static OrderLine fromMap(@Nullable Map<String, Object> m) {
        OrderLine ol = new OrderLine();
        if (m == null) return ol;

        // Kiểm tra xem có phải combo không
        Object typeObj = m.get("type");
        String typeStr = typeObj != null ? String.valueOf(typeObj).trim() : "";
        ol.isCombo = "combo".equalsIgnoreCase(typeStr);
        
        android.util.Log.d("OrderLine", "Parsing item - type: " + typeStr + ", isCombo: " + ol.isCombo);
        
        if (ol.isCombo) {
            // Xử lý combo
            Object n = pick(m, "name", "productName", "title", "comboName");
            ol.name = n == null ? "Combo" : String.valueOf(n);
            ol.comboId = asString(pick(m, "comboId", "id"));
            // Combo không có size ở level combo (mỗi sản phẩm có size riêng)
            ol.size = "";
            ol.toppings = new ArrayList<>(); // Combo không có toppings ở level combo
            ol.sugar = "";
            ol.ice = "";
            
            // Parse chi tiết các sản phẩm trong combo
            Object productsObj = m.get("products");
            ol.comboProducts = new ArrayList<>();
            if (productsObj instanceof List) {
                for (Object p : (List<?>) productsObj) {
                    if (p instanceof Map) {
                        Map<String, Object> productMap = (Map<String, Object>) p;
                        ComboProductDetail detail = new ComboProductDetail();
                        detail.productName = asString(productMap.get("productName"));
                        detail.size = asString(productMap.get("size"));
                        detail.sugar = asString(productMap.get("sugar"));
                        // Bỏ đá - sản phẩm mặc định phục vụ đá riêng
                        
                        // Parse toppings
                        Object tops = productMap.get("toppings");
                        detail.toppings = new ArrayList<>();
                        if (tops instanceof List) {
                            for (Object t : (List<?>) tops) {
                                if (t instanceof Map) {
                                    Topping topping = mapToTopping((Map<String, Object>) t);
                                    detail.toppings.add(topping);
                                }
                            }
                        }
                        ol.comboProducts.add(detail);
                    }
                }
            }
            android.util.Log.d("OrderLine", "Combo parsed - name: " + ol.name + ", comboId: " + ol.comboId + ", products count: " + ol.comboProducts.size());
        } else {
            // Xử lý sản phẩm thường
            // tên sản phẩm
            Object n = pick(m, "productName", "name", "title");
            ol.name = n == null ? "" : String.valueOf(n);

            // size/biến thể
            Object s = pick(m, "sizeName", "size", "variant");
            ol.size = s == null ? "" : String.valueOf(s);

            ol.sugar = asString(pick(m, "sugar"));
            ol.ice = asString(pick(m, "ice"));
            ol.note = asString(pick(m, "note"));

            // toppings: chuyển từ List<Map> thành List<Topping>
            Object tops = m.get("toppings");
            List<Topping> topList = new ArrayList<>();
            if (tops instanceof List) {
                for (Object t : (List<?>) tops) {
                    if (t instanceof Map) {
                        // Ép kiểu an toàn từ Map sang Topping
                        Topping topping = mapToTopping((Map<String, Object>) t);
                        topList.add(topping);
                    }
                }
            }
            ol.toppings = topList;
        }
        
        // Note cho cả combo và sản phẩm thường
        ol.note = asString(pick(m, "note"));

        // số lượng (cho cả combo và sản phẩm thường)
        ol.quantity = toInt(pick(m, "quantity", "qty"));
        android.util.Log.d("OrderLine", "Quantity parsed: " + ol.quantity);

        // đơn giá (cho cả combo và sản phẩm thường)
        ol.unitPrice = toDouble(pick(m, "unitPrice", "price"));
        android.util.Log.d("OrderLine", "UnitPrice parsed: " + ol.unitPrice);

        // thành tiền dòng (cho cả combo và sản phẩm thường)
        Object lt = pick(m, "lineTotal", "totalPrice", "amount");
        ol.lineTotal = lt != null ? toDouble(lt) : (ol.unitPrice * ol.quantity);
        android.util.Log.d("OrderLine", "LineTotal parsed: " + ol.lineTotal);

        return ol;
    }

    private static String asString(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    // Chuyển map thành đối tượng Topping
    private static Topping mapToTopping(Map<String, Object> map) {
        Topping t = new Topping();
        Object id = map.get("id");
        Object name = map.get("name");
        Object price = map.get("price");
        t.setId(id == null ? "" : String.valueOf(id));
        t.setName(name == null ? "" : String.valueOf(name));
        if (price instanceof Number) {
            t.setPrice(((Number) price).longValue());
        } else {
            try {
                t.setPrice(Long.parseLong(String.valueOf(price)));
            } catch (Exception e) {
                t.setPrice(0L);
            }
        }
        return t;
    }

    private static Object pick(Map<String, Object> m, String... keys) {
        for (String k : keys) if (m.containsKey(k)) return m.get(k);
        return null;
    }

    private static int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception ignored) { return 0; }
    }

    private static double toDouble(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception ignored) { return 0; }
    }

    // ===== GETTERS =====
    @NonNull public String getName() { return name == null ? "" : name; }
    @NonNull public String getSize() { return size == null ? "" : size; }
    @NonNull public List<Topping> getToppings() { return toppings == null ? new ArrayList<>() : toppings; }

    @NonNull public String getSugar() { return sugar == null ? "" : sugar; }
    @NonNull public String getIce() { return ice == null ? "" : ice; }
    @NonNull public String getNote() { return note == null ? "" : note; }

    public int getQuantity() { return quantity; }
    public double getUnitPrice() { return unitPrice; }
    public double getLineTotal() { return lineTotal; }
    
    // Combo getters
    public boolean isCombo() { return isCombo; }

    @NonNull public List<ComboProductDetail> getComboProducts() {
        return comboProducts == null ? new ArrayList<>() : comboProducts; 
    }

    // ===== HIỂN THỊ PHỤ =====
    @NonNull
    public String buildMeta() {
        StringBuilder sb = new StringBuilder();
        boolean added = false;

//        // Nếu có size thì xuống dòng ghi "Size: <size>"
//        if (!getSize().isEmpty()) {
//            sb.append("Size: ").append(getSize()).append("\n");
//        }
//
//        // Nếu có topping thì xuống dòng ghi "Topping:" rồi list từng topping xuống dòng, thụt đầu dòng 5-6 spaces
//        List<Topping> tops = getToppings();
//        if (!tops.isEmpty()) {
//            sb.append("Topping: ");
//            for (Topping t : tops) {
//                sb.append(" - ").append(t.getName()).append("");
//            }
//        }

        // Nếu là combo, hiển thị chi tiết các sản phẩm
        if (isCombo) {
            List<ComboProductDetail> products = getComboProducts();
            if (products.isEmpty()) {
                return "Combo";
            }
            
            StringBuilder comboDetail = new StringBuilder();
            for (int i = 0; i < products.size(); i++) {
                List<String> options = getStrings(products, i, comboDetail);

                if (!options.isEmpty()) {
                    comboDetail.append(" (").append(String.join(" • ", options)).append(")");
                }
                
                if (i < products.size() - 1) comboDetail.append("\n");
            }
            return comboDetail.toString();
        }

        // Size
        if (!getSize().isEmpty()) {
            sb.append("Size: ").append(getSize());
            added = true;
        }

        // Sugar
        if (!getSugar().isEmpty()) {
            if (added) sb.append(" • ");
            sb.append(getSugar());
            added = true;
        }

        // Ice
        if (!getIce().isEmpty()) {
            if (added) sb.append(" • ");
            sb.append(getIce());
            added = true;
        }

        // Topping
        List<Topping> tops = getToppings();
        if (!tops.isEmpty()) {
            if (added) sb.append(" • ");
            sb.append("Topping: ");
            for (int i = 0; i < tops.size(); i++) {
                sb.append(tops.get(i).getName());
                if (i < tops.size() - 1) sb.append(", ");
            }
        }

        return sb.toString().trim();  // trim để tránh xuống dòng thừa cuối cùng
    }

    @NonNull
    private List<String> getStrings(List<ComboProductDetail> products, int i, StringBuilder comboDetail) {
        ComboProductDetail product = products.get(i);
        comboDetail.append(product.productName);

        // Thêm size, đường, topping
        List<String> options = new ArrayList<>();
        if (!product.size.isEmpty()) options.add("Size: " + product.size);
        if (!product.sugar.isEmpty()) options.add("Đường: " + product.sugar);
        if (!product.toppings.isEmpty()) {
            StringBuilder toppingStr = new StringBuilder("Topping: ");
            for (int j = 0; j < product.toppings.size(); j++) {
                toppingStr.append(product.toppings.get(j).getName());
                if (j < product.toppings.size() - 1) toppingStr.append(", ");
            }
            options.add(toppingStr.toString());
        }
        return options;
    }

    // Inner class để lưu thông tin chi tiết sản phẩm trong combo
    public static class ComboProductDetail implements Serializable {
        public String productName;
        public String size;
        public String sugar;
        public List<Topping> toppings;
        
        public ComboProductDetail() {
            this.productName = "";
            this.size = "";
            this.sugar = "";
            this.toppings = new ArrayList<>();
        }
    }
}
