package com.pro.milkteaapp.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base class cho các Repository để giảm code duplication.
 * Cung cấp common utilities và helper methods.
 */
public abstract class BaseRepository {
    
    /**
     * FirebaseFirestore instance - shared across all repositories
     */
    protected final FirebaseFirestore db = FirebaseFirestore.getInstance();
    
    /**
     * Parse QuerySnapshot thành List<T>.
     * Subclass cần implement parseDocument() để parse từng document.
     */
    @NonNull
    protected <T> List<T> parseQuerySnapshot(@Nullable QuerySnapshot snapshot, 
                                             @NonNull DocumentParser<T> parser) {
        List<T> items = new ArrayList<>();
        if (snapshot != null && !snapshot.isEmpty()) {
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                T item = parser.parse(doc);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        return items;
    }
    
    /**
     * Helper để thêm timestamps vào data map
     */
    protected void addTimestamps(@NonNull Map<String, Object> data, boolean includeCreatedAt) {
        data.put(com.pro.milkteaapp.constants.FirestoreConstants.FIELD_UPDATED_AT, 
                 FieldValue.serverTimestamp());
        if (includeCreatedAt && !data.containsKey(com.pro.milkteaapp.constants.FirestoreConstants.FIELD_CREATED_AT)) {
            data.put(com.pro.milkteaapp.constants.FirestoreConstants.FIELD_CREATED_AT, 
                     FieldValue.serverTimestamp());
        }
    }
    
    /**
     * Helper để đảm bảo ID được set từ document ID
     */
    protected void ensureIdSet(@NonNull DocumentSnapshot doc, @NonNull Object model) {
        try {
            java.lang.reflect.Method setIdMethod = model.getClass().getMethod("setId", String.class);
            java.lang.reflect.Method getIdMethod = model.getClass().getMethod("getId");
            
            String currentId = (String) getIdMethod.invoke(model);
            if (currentId == null || currentId.isEmpty()) {
                setIdMethod.invoke(model, doc.getId());
            }
        } catch (Exception e) {
            // Ignore - model might not have setId/getId methods
            android.util.Log.w("BaseRepository", "Failed to set ID: " + e.getMessage());
        }
    }
    
    /**
     * Interface để parse DocumentSnapshot thành model object
     */
    @FunctionalInterface
    protected interface DocumentParser<T> {
        @Nullable
        T parse(@NonNull DocumentSnapshot doc);
    }
}

