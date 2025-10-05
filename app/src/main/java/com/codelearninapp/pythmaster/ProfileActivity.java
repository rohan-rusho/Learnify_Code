package com.codelearninapp.pythmaster;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    // XML views
    private ImageView profileImage;
    private EditText etName, etEmail, etPassword;
    private AppCompatButton btnSave, btnLogout;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private DatabaseReference userRef;

    // Local cache (same keys used in DashboardActivity)
    public static final String PREFS_NAME = "user_prefs";
    public static final String KEY_NAME = "name";
    public static final String KEY_EMAIL = "email";
    public static final String KEY_POINTS = "points";
    private SharedPreferences prefs;

    private boolean isSaving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile); // set this to your provided XML file name

        // If your file name is different, e.g., activity_profile.xml, update this reference accordingly.

        // Window styling optional (status bar icons dark on light background)


        // Init views
        profileImage = findViewById(R.id.profile_image);
        etName = findViewById(R.id.etProfileName);
        etEmail = findViewById(R.id.etProfileEmail);
        etPassword = findViewById(R.id.etProfilePassword);
        btnSave = findViewById(R.id.btnSaveProfile);
        btnLogout = findViewById(R.id.btnLogout);

        // Firebase
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            goToLoginAndFinish();
            return;
        }

        userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(currentUser.getUid());

        // Local prefs
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Load initial info
        loadProfile();

        // Save handler
        btnSave.setOnClickListener(v -> {
            if (isSaving) return;
            saveProfile();
        });

        // Logout handler
        btnLogout.setOnClickListener(v -> performLogout());
    }

    private void loadProfile() {
        // Prefill from cache quickly
        String cachedName = prefs.getString(KEY_NAME, "");
        String cachedEmail = prefs.getString(KEY_EMAIL, "");

        if (!TextUtils.isEmpty(cachedName)) etName.setText(cachedName);
        if (!TextUtils.isEmpty(cachedEmail)) etEmail.setText(cachedEmail);
        if (TextUtils.isEmpty(cachedEmail) && currentUser.getEmail() != null) {
            etEmail.setText(currentUser.getEmail());
        }

        // Then fetch from DB
        userRef.addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Object nameObj = snapshot.child("name").getValue();
                Object emailObj = snapshot.child("email").getValue();

                String dbName = nameObj != null ? String.valueOf(nameObj) : "";
                String dbEmail = emailObj != null ? String.valueOf(emailObj) : (currentUser.getEmail() != null ? currentUser.getEmail() : "");

                if (!TextUtils.isEmpty(dbName)) etName.setText(dbName);
                if (!TextUtils.isEmpty(dbEmail)) etEmail.setText(dbEmail);

                // Cache
                SharedPreferences.Editor ed = prefs.edit();
                if (!TextUtils.isEmpty(dbName)) ed.putString(KEY_NAME, dbName);
                if (!TextUtils.isEmpty(dbEmail)) ed.putString(KEY_EMAIL, dbEmail);
                ed.apply();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Ignore silently or show a toast
            }
        });
    }

    private void saveProfile() {
        String newName = etName.getText().toString().trim();
        String newEmail = etEmail.getText().toString().trim();
        String newPassword = etPassword.getText().toString();

        if (TextUtils.isEmpty(newName)) {
            etName.setError("Name required");
            etName.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(newEmail) || !Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
            etEmail.setError("Valid email required");
            etEmail.requestFocus();
            return;
        }
        if (!TextUtils.isEmpty(newPassword) && newPassword.length() < 6) {
            etPassword.setError("Minimum 6 characters");
            etPassword.requestFocus();
            return;
        }

        setSaving(true);

        // Step 1: Update email in Auth if changed
        String currentEmail = currentUser.getEmail() != null ? currentUser.getEmail() : "";
        if (!newEmail.equalsIgnoreCase(currentEmail)) {
            currentUser.updateEmail(newEmail)
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful()) {
                            handleSensitiveUpdateFailure(task.getException(), "email");
                            // Proceed to update name in DB even if email failed
                            updateDatabaseProfile(newName, currentEmail /*fallback*/, newPassword);
                        } else {
                            // Email updated in Auth, go update DB with new email
                            updateDatabaseProfile(newName, newEmail, newPassword);
                        }
                    });
        } else {
            // No email change, just update DB (and optional password)
            updateDatabaseProfile(newName, newEmail, newPassword);
        }
    }

    private void updateDatabaseProfile(String nameToSet, String emailToSet, String newPassword) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", nameToSet);
        updates.put("email", emailToSet);

        userRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                setSaving(false);
                Toast.makeText(this, "Failed to save profile info", Toast.LENGTH_SHORT).show();
                return;
            }

            // Update local cache
            SharedPreferences.Editor ed = prefs.edit();
            ed.putString(KEY_NAME, nameToSet);
            ed.putString(KEY_EMAIL, emailToSet);
            ed.apply();

            // Step 2: Update password if provided
            if (!TextUtils.isEmpty(newPassword)) {
                currentUser.updatePassword(newPassword)
                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                setSaving(false);
                                if (!task.isSuccessful()) {
                                    handleSensitiveUpdateFailure(task.getException(), "password");
                                    // Still consider profile saved even if password failed
                                    Toast.makeText(ProfileActivity.this, "Profile saved (password not changed).", Toast.LENGTH_SHORT).show();
                                } else {
                                    etPassword.setText(""); // clear input
                                    Toast.makeText(ProfileActivity.this, "Profile and password updated.", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
            } else {
                setSaving(false);
                Toast.makeText(this, "Profile updated.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleSensitiveUpdateFailure(Exception ex, String what) {
        if (ex == null) {
            Toast.makeText(this, String.format(Locale.getDefault(), "Failed to update %s.", what), Toast.LENGTH_SHORT).show();
            return;
        }
        if (ex instanceof FirebaseAuthRecentLoginRequiredException) {
            Toast.makeText(this,
                    String.format(Locale.getDefault(), "Please re-login to change %s.", what),
                    Toast.LENGTH_LONG).show();
            // Optionally redirect to LoginActivity for re-auth
            // goToLoginAndFinish();
        } else if (ex instanceof FirebaseNetworkException) {
            Toast.makeText(this, "Network error. Try again.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setSaving(boolean saving) {
        isSaving = saving;
        btnSave.setEnabled(!saving);
        btnLogout.setEnabled(!saving);
        etName.setEnabled(!saving);
        etEmail.setEnabled(!saving);
        etPassword.setEnabled(!saving);
        btnSave.setAlpha(saving ? 0.6f : 1f);
    }

    private void performLogout() {
        FirebaseAuth.getInstance().signOut();
        // Clear local prefs
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.clear();
        editor.apply();
        goToLoginAndFinish();
    }

    private void goToLoginAndFinish() {
        Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}