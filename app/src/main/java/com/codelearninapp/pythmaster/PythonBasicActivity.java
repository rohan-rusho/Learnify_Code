package com.codelearninapp.pythmaster;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class PythonBasicActivity extends AppCompatActivity {

    private static final int PART_COUNT = 5;
    private static final int PART_DETAIL_REQUEST = 301;

    private ProgressBar progressBar;
    private TextView tvProgress;
    private LinearLayout cardPart1, cardPart2, cardPart3, cardPart4, cardPart5;

    private int completedParts = 0;

    private DatabaseReference userRef;
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Ensure this filename matches the layout above
        setContentView(R.layout.activity_python_basic_topics);

        progressBar = findViewById(R.id.progress_bar);
        tvProgress = findViewById(R.id.tv_progress);

        cardPart1 = findViewById(R.id.card_part1);
        cardPart2 = findViewById(R.id.card_part2);
        cardPart3 = findViewById(R.id.card_part3);
        cardPart4 = findViewById(R.id.card_part4);
        cardPart5 = findViewById(R.id.card_part5);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }
        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);

        // Open parts
        cardPart1.setOnClickListener(v -> openPartDetail(1));
        cardPart2.setOnClickListener(v -> openPartDetail(2));
        cardPart3.setOnClickListener(v -> openPartDetail(3));
        cardPart4.setOnClickListener(v -> openPartDetail(4));
        cardPart5.setOnClickListener(v -> openPartDetail(5));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProgressFromDatabase();
    }

    private void loadProgressFromDatabase() {
        if (userRef == null) return;
        userRef.child("pythonBasicPartsCompleted").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                completedParts = 0;

                if (snapshot.exists()) {
                    for (DataSnapshot partSnap : snapshot.getChildren()) {
                        Object v = partSnap.getValue(); // legacy tolerant
                        if (v instanceof Boolean && (Boolean) v) completedParts++;
                        else if (v instanceof Long && (Long) v > 0) completedParts++;
                        else if (v instanceof String) {
                            String s = ((String) v).trim().toLowerCase();
                            if ("true".equals(s) || "1".equals(s)) completedParts++;
                        }
                    }
                }

                int percent = (int) (completedParts * 100.0 / PART_COUNT);
                // Keep Dashboard in sync
                userRef.child("pythonBasicProgress").setValue(percent);

                updateProgressUI(percent);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                int percent = (int) (completedParts * 100.0 / PART_COUNT);
                updateProgressUI(percent);
            }
        });
    }

    private void updateProgressUI(int percent) {
        tvProgress.setText("Progress: " + completedParts + "/" + PART_COUNT);
        setProgressPercent(progressBar, percent);
    }

    // Clamp and animate (API 24+)
    private void setProgressPercent(ProgressBar bar, int percent) {
        if (bar == null) return;
        if (percent < 0) percent = 0;
        if (percent > 100) percent = 100;
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            bar.setProgress(percent, true);
        } else {
            bar.setProgress(percent);
        }
    }

    private void openPartDetail(int partNumber) {
        Intent intent = new Intent(this, PythonBasicPartActivity.class);
        intent.putExtra("part_number", partNumber);
        startActivityForResult(intent, PART_DETAIL_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PART_DETAIL_REQUEST) {
            loadProgressFromDatabase();
        }
    }
}