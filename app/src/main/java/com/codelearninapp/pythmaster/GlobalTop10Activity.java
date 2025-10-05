package com.codelearninapp.pythmaster;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Activity to show Global Top 10 users by points.
 *
 * Layout expected: res/layout/activity_global_top10.xml
 * Must contain:
 *   - TextView:  @id/title_top10
 *   - LinearLayout container: @id/top10_container
 *
 * Database structure expected (per user node):
 *   users/{uid}/name    -> String
 *   users/{uid}/points  -> Number
 *   users/{uid}/photoUrl (optional) -> String URL
 */
public class GlobalTop10Activity extends AppCompatActivity {

    private LinearLayout top10Container;
    private DatabaseReference usersRef;
    private ValueEventListener leaderboardListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_global_top10);

        top10Container = findViewById(R.id.top10_container);
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        // Optional status bar icon color for light background

    }

    @Override
    protected void onStart() {
        super.onStart();

        leaderboardListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<UserRow> rows = new ArrayList<>();
                for (DataSnapshot userSnap : snapshot.getChildren()) {
                    String name = valString(userSnap.child("name").getValue(), "User");
                    long pointsLong = valLong(userSnap.child("points").getValue(), 0L);
                    String photoUrl = valString(userSnap.child("photoUrl").getValue(), null);

                    rows.add(new UserRow(name, (int) pointsLong, photoUrl));
                }

                // Sort DESC by points, then name for stable order
                Collections.sort(rows, new Comparator<UserRow>() {
                    @Override
                    public int compare(UserRow a, UserRow b) {
                        if (b.points != a.points) return b.points - a.points;
                        return a.name.compareToIgnoreCase(b.name);
                    }
                });

                // Take top 10
                if (rows.size() > 10) rows = rows.subList(0, 10);

                renderRows(rows);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                top10Container.removeAllViews();
            }
        };

        usersRef.addValueEventListener(leaderboardListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (usersRef != null && leaderboardListener != null) {
            usersRef.removeEventListener(leaderboardListener);
        }
    }

    private void renderRows(List<UserRow> rows) {
        top10Container.removeAllViews();

        int rank = 1;
        for (UserRow row : rows) {
            View item = buildRow(this, rank, row);
            top10Container.addView(item);

            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) item.getLayoutParams();
            lp.bottomMargin = dp(12);
            item.setLayoutParams(lp);

            rank++;
        }
    }

    private View buildRow(Context ctx, int rank, UserRow row) {
        // Root horizontal card
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackgroundResource(R.drawable.card_bg_white);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setElevation(dp(2));
        card.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams cardLP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        card.setLayoutParams(cardLP);

        // Rank bubble
        TextView tvRank = new TextView(ctx);
        LinearLayout.LayoutParams rankLP = new LinearLayout.LayoutParams(dp(40), dp(40));
        tvRank.setLayoutParams(rankLP);
        tvRank.setBackgroundResource(R.drawable.bg_circle_blue); // keep your existing style
        tvRank.setText(String.valueOf(rank));
        tvRank.setGravity(Gravity.CENTER);
        tvRank.setTextColor(0xFFFFFFFF);
        tvRank.setTextSize(18);

        // Profile image
        ImageView img = new ImageView(ctx);
        LinearLayout.LayoutParams imgLP = new LinearLayout.LayoutParams(dp(44), dp(44));
        imgLP.setMarginStart(dp(16));
        img.setLayoutParams(imgLP);
        img.setBackgroundResource(R.drawable.circle_white);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setImageResource(R.drawable.ic_avatar_placeholder);
        // If you store photoUrl and have Glide:
        // Glide.with(ctx).load(row.photoUrl).placeholder(R.drawable.ic_avatar_placeholder).into(img);

        // Name + medal row (horizontal)
        LinearLayout nameRow = new LinearLayout(ctx);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView tvName = new TextView(ctx);
        tvName.setText(row.name);
        tvName.setTextColor(0xFF272727);
        tvName.setTextSize(16);
        tvName.setTypeface(tvName.getTypeface(), android.graphics.Typeface.BOLD);

        ImageView ivMedal = new ImageView(ctx);
        LinearLayout.LayoutParams medalLP = new LinearLayout.LayoutParams(dp(18), dp(18));
        medalLP.setMarginStart(dp(6));
        medalLP.gravity = Gravity.CENTER_VERTICAL;
        ivMedal.setLayoutParams(medalLP);

        // Show medal only for top 3
        if (rank == 1) {
            ivMedal.setImageResource(R.drawable.ic_medal_gold);
            ivMedal.setContentDescription("Gold medal");
            ivMedal.setVisibility(View.VISIBLE);
        } else if (rank == 2) {
            ivMedal.setImageResource(R.drawable.ic_medal_silver);
            ivMedal.setContentDescription("Silver medal");
            ivMedal.setVisibility(View.VISIBLE);
        } else if (rank == 3) {
            ivMedal.setImageResource(R.drawable.ic_medal_bronze);
            ivMedal.setContentDescription("Bronze medal");
            ivMedal.setVisibility(View.VISIBLE);
        } else {
            ivMedal.setVisibility(View.GONE);
        }

        nameRow.addView(tvName);
        nameRow.addView(ivMedal);

        // Points text (below name row)
        TextView tvPoints = new TextView(ctx);
        tvPoints.setText("Points: " + row.points);
        // Use your color resource if present, else fallback to hex
        int blue = ContextCompat.getColor(ctx, R.color.primary_blue_700);
        tvPoints.setTextColor(blue != 0 ? blue : 0xFF1565CB);
        tvPoints.setTextSize(14);

        // Vertical column
        LinearLayout col = new LinearLayout(ctx);
        LinearLayout.LayoutParams colLP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        colLP.setMarginStart(dp(14));
        col.setLayoutParams(colLP);
        col.setOrientation(LinearLayout.VERTICAL);

        col.addView(nameRow);
        LinearLayout.LayoutParams ptsLP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ptsLP.topMargin = dp(2);
        tvPoints.setLayoutParams(ptsLP);
        col.addView(tvPoints);

        // Assemble
        card.addView(tvRank);
        card.addView(img);
        card.addView(col);
        return card;
    }

    // Helpers

    private static class UserRow {
        final String name;
        final int points;
        final String photoUrl;

        UserRow(String name, int points, String photoUrl) {
            this.name = name;
            this.points = points;
            this.photoUrl = photoUrl;
        }
    }

    private int dp(int dps) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dps * density);
    }

    private String valString(Object o, String def) {
        if (o == null) return def;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? def : s;
    }

    private long valLong(Object o, long def) {
        if (o == null) return def;
        if (o instanceof Long) return (Long) o;
        if (o instanceof Integer) return ((Integer) o).longValue();
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception ignored) {
            return def;
        }
    }
}