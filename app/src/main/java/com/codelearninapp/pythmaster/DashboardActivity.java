package com.codelearninapp.pythmaster;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class DashboardActivity extends AppCompatActivity {

    private TextView usernameText, welcomeText, totalPointsText;
    private ProgressBar progressBasic, progressVariable, progressAdvance;
    private TextView progressBasicPercent, progressVariablePercent, progressAdvancePercent;
    private ImageView menuIcon, profileImage;

    private LinearLayout cardPythonBasic, cardPythonMid, cardPythonAdvance;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;

    private SharedPreferences sharedPreferences;

    public static final String PREFS_NAME = "user_prefs";
    public static final String KEY_NAME = "name";
    public static final String KEY_EMAIL = "email";
    public static final String KEY_POINTS = "points";
    public static final String KEY_BASIC_PROGRESS = "basic_progress";
    public static final String KEY_VARIABLE_PROGRESS = "variable_progress";
    public static final String KEY_ADVANCE_PROGRESS = "advance_progress";

    private int points = 0;
    private int basicProgressPct = 0;
    private int variableProgressPct = 0;
    private int advanceProgressPct = 0;
    private String username = "";
    private String email = "";

    private ValueEventListener liveUserListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.navigation_view);

        usernameText = findViewById(R.id.username);
        welcomeText = findViewById(R.id.welcome_text);
        totalPointsText = findViewById(R.id.total_points);

        progressBasic = findViewById(R.id.progressBarBasic);
        progressVariable = findViewById(R.id.progressBarVariable);
        progressAdvance = findViewById(R.id.progressBarAdvance);

        progressBasicPercent = findViewById(R.id.tvBasicPercent);
        progressVariablePercent = findViewById(R.id.tvVariablePercent);
        progressAdvancePercent = findViewById(R.id.tvAdvancePercent);

        menuIcon = findViewById(R.id.menu_icon);
        profileImage = findViewById(R.id.profile_image);

        cardPythonBasic = findViewById(R.id.cardPythonBasic);
        cardPythonMid = findViewById(R.id.cardPythonMid);
        cardPythonAdvance = findViewById(R.id.cardPythonAdvance);

        mAuth = FirebaseAuth.getInstance();
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Open drawer
        menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.END));

        // Profile image opens profile
        profileImage.setOnClickListener(v -> safeStartActivity(new Intent(this, ProfileActivity.class)));

        // Card navigations
        cardPythonBasic.setOnClickListener(v -> navigateToBasic());
        cardPythonMid.setOnClickListener(v -> navigateToMid());
        cardPythonAdvance.setOnClickListener(v -> navigateToAdvance());

        // Drawer menu item actions (handle all expected items safely)
        navigationView.setNavigationItemSelectedListener(item -> {
            drawerLayout.closeDrawer(GravityCompat.END);
            int id = item.getItemId();

            if (id == getIdSafe("menu_home")) {
                return true;
            }
            if (id == getIdSafe("menu_top10")) {
                safeStartActivity(new Intent(this, GlobalTop10Activity.class));
                return true;
            }
            if (id == getIdSafe("menu_profile")) {
                safeStartActivity(new Intent(this, ProfileActivity.class));
                return true;
            }
            if (id == getIdSafe("menu_history")) {
                safeStartActivity(new Intent(this, HistoryActivity.class));
                return true;
            }
            if (id == getIdSafe("menu_basic")) {
                navigateToBasic();
                return true;
            }
            if (id == getIdSafe("menu_mid")) {
                navigateToMid();
                return true;
            }
            if (id == getIdSafe("menu_advance")) {
                navigateToAdvance();
                return true;
            }
            if (id == getIdSafe("menu_share")) {
                shareApp();
                return true;
            }
            if (id == getIdSafe("menu_rate")) {
                rateApp();
                return true;
            }
            if (id == getIdSafe("menu_privacy")) {
                openUrl("https://your-domain.example/privacy");
                return true;
            }
            if (id == getIdSafe("menu_logout")) {
                performLogout();
                return true;
            }
            return false;
        });

        // Optional footer logout inside drawer
        LinearLayout footer = navigationView.findViewById(R.id.footerRoot);
        if (footer != null) {
            TextView btnLogout = footer.findViewById(R.id.btnLogout);
            if (btnLogout != null) btnLogout.setOnClickListener(v -> performLogout());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        usersRef = FirebaseDatabase.getInstance().getReference("users").child(user.getUid());

        liveUserListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String nameDb = snapshot.child("name").getValue(String.class);
                username = nameDb != null && !nameDb.isEmpty() ? nameDb : "User";
                String emailDb = snapshot.child("email").getValue(String.class);
                email = emailDb != null ? emailDb : "";
                Integer pts = snapshot.child("points").getValue(Integer.class);
                points = pts != null ? pts : 0;

                Integer storedBasicPct = snapshot.child("pythonBasicProgress").getValue(Integer.class);
                if (storedBasicPct != null) {
                    basicProgressPct = clampPercent(storedBasicPct);
                } else {
                    int completed = 0;
                    DataSnapshot parts = snapshot.child("pythonBasicPartsCompleted");
                    for (DataSnapshot p : parts.getChildren()) {
                        Object v = p.getValue();
                        if (v instanceof Boolean && (Boolean) v) completed++;
                        else if (v instanceof Long && (Long) v > 0) completed++;
                        else if (v instanceof String) {
                            String s = ((String) v).trim().toLowerCase();
                            if ("true".equals(s) || "1".equals(s)) completed++;
                        }
                    }
                    basicProgressPct = (int) (completed * 100.0 / 5.0);
                }

                Integer midPct = snapshot.child("pythonMidProgress").getValue(Integer.class);
                if (midPct != null) {
                    variableProgressPct = clampPercent(midPct);
                } else {
                    int completed = 0;
                    DataSnapshot parts = snapshot.child("pythonMidPartsCompleted");
                    for (DataSnapshot p : parts.getChildren()) {
                        Object v = p.getValue();
                        if (v instanceof Boolean && (Boolean) v) completed++;
                        else if (v instanceof Long && (Long) v > 0) completed++;
                        else if (v instanceof String) {
                            String s = ((String) v).trim().toLowerCase();
                            if ("true".equals(s) || "1".equals(s)) completed++;
                        }
                    }
                    variableProgressPct = (int) (completed * 100.0 / 5.0);
                }

                Integer advPct = snapshot.child("pythonAdvanceProgress").getValue(Integer.class);
                if (advPct != null) {
                    advanceProgressPct = clampPercent(advPct);
                } else {
                    int completed = 0;
                    DataSnapshot parts = snapshot.child("pythonAdvancePartsCompleted");
                    for (DataSnapshot p : parts.getChildren()) {
                        Object v = p.getValue();
                        if (v instanceof Boolean && (Boolean) v) completed++;
                        else if (v instanceof Long && (Long) v > 0) completed++;
                        else if (v instanceof String) {
                            String s = ((String) v).trim().toLowerCase();
                            if ("true".equals(s) || "1".equals(s)) completed++;
                        }
                    }
                    advanceProgressPct = (int) (completed * 100.0 / 10.0);
                }

                updateTopSummaryUI();
                updateBottomCardsUI();

                SharedPreferences.Editor ed = sharedPreferences.edit();
                ed.putString(KEY_NAME, username);
                ed.putString(KEY_EMAIL, email);
                ed.putInt(KEY_POINTS, points);
                ed.putInt(KEY_BASIC_PROGRESS, basicProgressPct);
                ed.putInt(KEY_VARIABLE_PROGRESS, variableProgressPct);
                ed.putInt(KEY_ADVANCE_PROGRESS, advanceProgressPct);
                ed.apply();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };

        usersRef.addValueEventListener(liveUserListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (usersRef != null && liveUserListener != null) {
            usersRef.removeEventListener(liveUserListener);
        }
    }

    private int clampPercent(int p) {
        if (p < 0) return 0;
        if (p > 100) return 100;
        return p;
    }

    private void updateTopSummaryUI() {
        usernameText.setText(username);
        if (welcomeText != null) {
            welcomeText.setText(getString(R.string.welcome_back, username));
        }
        totalPointsText.setText("Total Points: " + points);

        setProgressPercent(progressBasic, basicProgressPct);
        setProgressPercent(progressVariable, variableProgressPct);
        setProgressPercent(progressAdvance, advanceProgressPct);

        progressBasicPercent.setText(basicProgressPct + "%");
        progressVariablePercent.setText(variableProgressPct + "%");
        progressAdvancePercent.setText(advanceProgressPct + "%");
    }

    private void updateBottomCardsUI() {
        TextView tvCardBasicProgress = findViewById(R.id.tvCardBasicProgress);
        ProgressBar pbCardBasic = findViewById(R.id.pbCardBasic);

        TextView tvCardMidProgress = findViewById(R.id.tvCardMidProgress);
        ProgressBar pbCardMid = findViewById(R.id.pbCardMid);

        TextView tvCardAdvanceProgress = findViewById(R.id.tvCardAdvanceProgress);
        ProgressBar pbCardAdvance = findViewById(R.id.pbCardAdvance);

        if (tvCardBasicProgress != null) tvCardBasicProgress.setText("Progress: " + basicProgressPct + "%");
        if (pbCardBasic != null) setProgressPercent(pbCardBasic, basicProgressPct);

        if (tvCardMidProgress != null) tvCardMidProgress.setText("Progress: " + variableProgressPct + "%");
        if (pbCardMid != null) setProgressPercent(pbCardMid, variableProgressPct);

        if (tvCardAdvanceProgress != null) tvCardAdvanceProgress.setText("Progress: " + advanceProgressPct + "%");
        if (pbCardAdvance != null) setProgressPercent(pbCardAdvance, advanceProgressPct);
    }

    private void navigateToBasic() {
        safeStartActivity(new Intent(DashboardActivity.this, PythonBasicActivity.class));
    }

    private void navigateToMid() {
        safeStartActivity(new Intent(DashboardActivity.this, PythonMidActivity.class));
    }

    private void navigateToAdvance() {
        safeStartActivity(new Intent(DashboardActivity.this, PythonAdvanceActivity.class));
    }

    private void shareApp() {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            String body = "Check out this app: https://play.google.com/store/apps/details?id=" + getPackageName();
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Learn Python");
            shareIntent.putExtra(Intent.EXTRA_TEXT, body);
            startActivity(Intent.createChooser(shareIntent, "Share via"));
        } catch (Exception ignored) { }
    }

    private void rateApp() {
        Uri uri = Uri.parse("market://details?id=" + getPackageName());
        Intent goToMarket = new Intent(Intent.ACTION_VIEW, uri);
        goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY |
                Intent.FLAG_ACTIVITY_NEW_DOCUMENT |
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        try {
            startActivity(goToMarket);
        } catch (ActivityNotFoundException e) {
            openUrl("https://play.google.com/store/apps/details?id=" + getPackageName());
        }
    }

    private void openUrl(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(i);
        } catch (Exception ignored) { }
    }

    private void safeStartActivity(Intent intent) {
        try {
            startActivity(intent);
        } catch (Exception ignored) { }
    }

    private int getIdSafe(String name) {
        // Resolve a menu ID if it exists; returns 0 if not found
        return getResources().getIdentifier(name, "id", getPackageName());
    }

    private void performLogout() {
        FirebaseAuth.getInstance().signOut();
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.clear();
        editor.apply();
        Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        safeStartActivity(intent);
        finish();
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
}