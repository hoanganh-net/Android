package com.pro.milkteaapp.constants;

/**
 * Tập trung tất cả các tên collection và field của Firestore.
 * Giúp tránh hardcode và dễ bảo trì.
 */
public class FirestoreConstants {

    // ===== COLLECTIONS =====
    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_PRODUCTS = "products";
    public static final String COLLECTION_ORDERS = "orders";
    public static final String COLLECTION_CATEGORIES = "categories";
    public static final String COLLECTION_REVIEWS = "reviews";
    public static final String COLLECTION_VOUCHERS = "vouchers";
    public static final String COLLECTION_TOPPINGS = "toppings";
    public static final String COLLECTION_COMBOS = "combos";
    public static final String COLLECTION_MESSAGES = "messages";
    public static final String COLLECTION_META = "meta";
    public static final String COLLECTION_ADDRESSES = "addresses"; // subcollection của users
    public static final String COLLECTION_VOUCHER_USAGES = "voucher_usages"; // subcollection của users
    public static final String COLLECTION_INBOX = "inbox"; // subcollection của users

    // ===== DOCUMENTS =====
    public static final String DOC_COUNTERS = "counters";
    public static final String DOC_USER_COUNTERS = "userCounters";

    // ===== USER FIELDS =====
    public static final String FIELD_UID = "uid";
    public static final String FIELD_AUTH_UID = "authUid";
    public static final String FIELD_EMAIL = "email";
    public static final String FIELD_FULL_NAME = "fullName";
    public static final String FIELD_ROLE = "role";
    public static final String FIELD_PHONE = "phone";
    public static final String FIELD_ADDRESS = "address";
    public static final String FIELD_AVATAR = "avatar";
    public static final String FIELD_LOYALTY_TIER = "loyaltyTier";
    public static final String FIELD_LOYALTY_POINTS = "loyaltyPoints";
    public static final String FIELD_REWARD_POINTS = "rewardPoints";
    public static final String FIELD_TOTAL_SPENT = "totalSpent";

    // ===== PRODUCT FIELDS =====
    public static final String FIELD_PRODUCT_ID = "id";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_PRICE = "price";
    public static final String FIELD_CATEGORY = "category";
    public static final String FIELD_IMAGE_URL = "imageUrl";
    public static final String FIELD_DESCRIPTION = "description";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_SEARCHABLE_NAME = "searchableName";
    public static final String FIELD_CREATED_BY_UID = "createdByUid";
    public static final String FIELD_CREATED_AT = "createdAt";
    public static final String FIELD_UPDATED_AT = "updatedAt";
    public static final String FIELD_FEATURED = "featured";

    // ===== ORDER FIELDS =====
    public static final String FIELD_ORDER_ID = "id";
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_ORDER_STATUS = "status";
    public static final String FIELD_FINAL_TOTAL = "finalTotal";
    public static final String FIELD_SUBTOTAL = "subtotal";
    public static final String FIELD_DISCOUNT = "discount";
    public static final String FIELD_SHIPPING_FEE = "shippingFee";
    public static final String FIELD_RECEIVER_NAME = "receiverName";
    public static final String FIELD_RECEIVER_PHONE = "receiverPhone";
    public static final String FIELD_ADDRESS_DISPLAY = "addressDisplay";
    public static final String FIELD_ADDRESS_OBJ = "addressObj";
    public static final String FIELD_FINISHED_AT = "finishedAt";
    public static final String FIELD_CANCELLED_AT = "cancelledAt";
    public static final String FIELD_CANCELED_AT = "canceledAt"; // fallback

    // ===== CATEGORY FIELDS =====
    public static final String FIELD_CATEGORY_ID = "id";
    public static final String FIELD_CATEGORY_NAME = "name";
    public static final String FIELD_ACTIVE = "active";

    // ===== REVIEW FIELDS =====
    public static final String FIELD_RATING = "rating";
    public static final String FIELD_COMMENT = "comment";
    public static final String FIELD_PRODUCT_ID_REF = "productId";
    public static final String FIELD_ORDER_ID_REF = "orderId";

    // ===== VOUCHER FIELDS =====
    public static final String FIELD_VOUCHER_CODE = "code";
    public static final String FIELD_DISCOUNT_TYPE = "discountType";
    public static final String FIELD_DISCOUNT_VALUE = "discountValue";
    public static final String FIELD_MIN_ORDER = "minOrder";
    public static final String FIELD_START_AT = "startAt";
    public static final String FIELD_END_AT = "endAt";
    public static final String FIELD_IS_ACTIVE = "isActive";

    // ===== TOPPING FIELDS =====
    public static final String FIELD_TOPPING_NAME = "name";
    public static final String FIELD_TOPPING_PRICE = "price";

    // ===== ADDRESS FIELDS =====
    public static final String FIELD_ADDRESS_ID = "id";
    public static final String FIELD_LINE1 = "line1";
    public static final String FIELD_LINE2 = "line2";
    public static final String FIELD_CITY = "city";
    public static final String FIELD_PROVINCE = "province";
    public static final String FIELD_POSTAL_CODE = "postalCode";
    public static final String FIELD_IS_DEFAULT = "isDefault";

    // ===== META FIELDS =====
    public static final String FIELD_PRODUCT_SEQ = "productSeq";
    public static final String FIELD_ORDER_SEQ = "orderSeq";
    public static final String FIELD_USER_SEQ = "userSeq";

    // ===== STATUS VALUES =====
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_FINISHED = "FINISHED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    // ===== ROLE VALUES =====
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_USER = "user";

    // ===== COMBO FIELDS =====
    public static final String FIELD_COMBO_ID = "id";
    public static final String FIELD_COMBO_NAME = "name";
    public static final String FIELD_COMBO_DESCRIPTION = "description";
    public static final String FIELD_COMBO_PRICE = "price";
    public static final String FIELD_COMBO_IMAGE_URL = "imageUrl";
    public static final String FIELD_COMBO_PRODUCT_IDS = "productIds";
    public static final String FIELD_COMBO_STATUS = "status";
    public static final String FIELD_COMBO_SEARCHABLE_NAME = "searchableName";
    public static final String FIELD_COMBO_ORIGINAL_PRICE = "originalPrice";
    public static final String FIELD_COMBO_DISCOUNT_PERCENT = "discountPercent";

    // ===== ID PREFIXES =====
    public static final String PREFIX_USER = "USR";
    public static final String PREFIX_PRODUCT = "PRD";
    public static final String PREFIX_ORDER = "ORD";
    public static final String PREFIX_COMBO = "CMB";

    // ===== ID WIDTHS =====
    public static final int ID_WIDTH_DEFAULT = 5;
}

