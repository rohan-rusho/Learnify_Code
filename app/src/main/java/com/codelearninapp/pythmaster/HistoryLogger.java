package com.codelearninapp.pythmaster;

import androidx.annotation.NonNull;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

/**
 * Writes a history entry to: users/{uid}/history/{timestamp}
 */
public class HistoryLogger {

    public static void logQuizCompletion(@NonNull String uid,
                                         @NonNull String trackKey,   // "python_basic" | "python_mid" | "python_advance"
                                         int partNumber,
                                         int pointsEarned,           // e.g., correctAnswers * 10
                                         int correct,
                                         int total,
                                         @NonNull String title) {    // e.g., "Python Basic — Part 2"

        long ts = System.currentTimeMillis();
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid)
                .child("history")
                .child(String.valueOf(ts));

        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("track", trackKey);
        data.put("partNumber", partNumber);
        data.put("pointsEarned", pointsEarned);
        data.put("correct", correct);
        data.put("total", total);
        data.put("completedAt", ts);

        ref.setValue(data);
    }
}