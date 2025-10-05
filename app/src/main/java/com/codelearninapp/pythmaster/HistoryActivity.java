package com.codelearninapp.pythmaster;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HistoryActivity extends AppCompatActivity {

    private TextView tvTotalPointsValue, tvCompletedPartsValue;
    private LinearLayout emptyState;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recycler;
    private HistoryAdapter adapter;

    private DatabaseReference userRef;
    private ValueEventListener pointsListener;
    private ValueEventListener liveHistoryListener;

    private final List<HistoryEntry> items = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        tvTotalPointsValue = findViewById(R.id.tvTotalPointsValue);
        tvCompletedPartsValue = findViewById(R.id.tvCompletedPartsValue);
        emptyState = findViewById(R.id.emptyState);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        recycler = findViewById(R.id.recyclerHistory);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(items);
        recycler.setAdapter(adapter);

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (uid == null) {
            // Not logged in; show empty
            emptyState.setVisibility(View.VISIBLE);
            swipeRefresh.setEnabled(false);
            return;
        }

        userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);

        // Pull-to-refresh (one-time reload)
        swipeRefresh.setOnRefreshListener(this::reloadOnce);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (userRef == null) return;

        // Live points
        pointsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Integer pts = snapshot.getValue(Integer.class);
                tvTotalPointsValue.setText(String.format(Locale.getDefault(), "%d", pts != null ? pts : 0));
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };
        userRef.child("points").addValueEventListener(pointsListener);

        // Live history listener
        liveHistoryListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                items.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    HistoryEntry e = HistoryEntry.fromSnapshot(child);
                    if (e != null) items.add(e);
                }
                sortDescByTime(items);
                adapter.notifyDataSetChanged();
                updateEmptyAndCompletedCount(items);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };
        userRef.child("history").addValueEventListener(liveHistoryListener);

        // Also trigger initial refresh of points and history
        swipeRefresh.setRefreshing(true);
        reloadOnce();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (userRef != null) {
            if (pointsListener != null) userRef.child("points").removeEventListener(pointsListener);
            if (liveHistoryListener != null) userRef.child("history").removeEventListener(liveHistoryListener);
        }
    }

    private void reloadOnce() {
        if (userRef == null) {
            swipeRefresh.setRefreshing(false);
            return;
        }
        // Reload points once
        userRef.child("points").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                Integer pts = snapshot.getValue(Integer.class);
                tvTotalPointsValue.setText(String.format(Locale.getDefault(), "%d", pts != null ? pts : 0));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        });

        // Reload history once
        userRef.child("history").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                items.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    HistoryEntry e = HistoryEntry.fromSnapshot(child);
                    if (e != null) items.add(e);
                }
                sortDescByTime(items);
                adapter.notifyDataSetChanged();
                updateEmptyAndCompletedCount(items);
                swipeRefresh.setRefreshing(false);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                swipeRefresh.setRefreshing(false);
            }
        });
    }

    private void sortDescByTime(List<HistoryEntry> list) {
        Collections.sort(list, new Comparator<HistoryEntry>() {
            @Override
            public int compare(HistoryEntry a, HistoryEntry b) {
                // Newest first
                if (a.completedAt == b.completedAt) return 0;
                return (a.completedAt > b.completedAt) ? -1 : 1;
            }
        });
    }

    private void updateEmptyAndCompletedCount(List<HistoryEntry> list) {
        emptyState.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);

        // Count distinct completed parts (track + partNumber)
        Set<String> distinct = new HashSet<>();
        for (HistoryEntry e : list) {
            distinct.add(e.track + "#" + e.partNumber);
        }
        tvCompletedPartsValue.setText(String.format(Locale.getDefault(), "%d", distinct.size()));
    }
}