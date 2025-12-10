package com.pro.milkteaapp.generator;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.constants.FirestoreConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * Generator để tạo ID cho Combo theo format CMB00001, CMB00002, ...
 */
public class ComboIDGenerator {
    private static final String META_COLLECTION = "meta";
    private static final String SEQ_DOC = "counters";
    private static final String FIELD_COMBO_SEQ = "comboSeq";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Tạo ID combo mới (CMB00001, CMB00002, ...)
     */
    public Task<String> nextComboId() {
        return nextComboId(FirestoreConstants.ID_WIDTH_DEFAULT, FirestoreConstants.PREFIX_COMBO);
    }

    public Task<String> nextComboId(int width, String prefix) {
        final DocumentReference ref = db.collection(META_COLLECTION).document(SEQ_DOC);
        return db.runTransaction(tr -> {
            DocumentSnapshot snap = tr.get(ref);

            long current = 0L;
            if (snap.exists() && snap.contains(FIELD_COMBO_SEQ)) {
                Object raw = snap.get(FIELD_COMBO_SEQ);
                if (raw instanceof Number) {
                    current = ((Number) raw).longValue();
                }
            }

            long next = current + 1;

            Map<String, Object> updates = new HashMap<>();
            updates.put(FIELD_COMBO_SEQ, next);

            if (snap.exists()) {
                tr.update(ref, updates);
            } else {
                tr.set(ref, updates);
            }

            String number = String.format("%0" + width + "d", next);
            return prefix + number; // ví dụ: CMB00001
        });
    }
}

