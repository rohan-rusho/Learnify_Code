package com.codelearninapp.pythmaster;

import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;

public class HistoryEntry {
    public String id;            // Firebase key
    public String title;         // e.g., "Python Basic — Part 2"
    public String track;         // "python_basic" | "python_mid" | "python_advance"
    public int partNumber;       // 1..N
    public int pointsEarned;     // e.g., 20
    public int correct;          // e.g., 2
    public int total;            // e.g., 4
    public long completedAt;     // epoch millis

    @Nullable
    public static HistoryEntry fromSnapshot(DataSnapshot snap) {
        if (snap == null) return null;
        HistoryEntry e = new HistoryEntry();
        e.id = snap.getKey();

        Object titleObj = snap.child("title").getValue();
        e.title = titleObj != null ? String.valueOf(titleObj) : "";

        Object trackObj = snap.child("track").getValue();
        e.track = trackObj != null ? String.valueOf(trackObj) : "";

        e.partNumber = safeInt(snap.child("partNumber").getValue());
        e.pointsEarned = safeInt(snap.child("pointsEarned").getValue());
        e.correct = safeInt(snap.child("correct").getValue());
        e.total = safeInt(snap.child("total").getValue());

        Object tsObj = snap.child("completedAt").getValue();
        if (tsObj instanceof Long) e.completedAt = (Long) tsObj;
        else if (tsObj instanceof String) {
            try { e.completedAt = Long.parseLong((String) tsObj); } catch (Exception ignored) {}
        } else {
            // If you used the key as timestamp
            try { e.completedAt = Long.parseLong(snap.getKey()); } catch (Exception ignored) {}
        }
        return e;
    }

    private static int safeInt(Object o) {
        if (o instanceof Long) return ((Long) o).intValue();
        if (o instanceof Integer) return (Integer) o;
        if (o instanceof String) {
            try { return Integer.parseInt((String) o); } catch (Exception ignored) {}
        }
        return 0;
    }
}