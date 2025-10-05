package com.codelearninapp.pythmaster;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

public class SignupActivity extends AppCompatActivity {

    private EditText etName, etEmail, etPassword, etConfirmPassword;
    private AppCompatButton btnSignup, btnLogin;
    private TextView tvAlreadyAccount;
    private ProgressBar progressSignup;

    private FirebaseAuth mAuth;
    private SharedPreferences sharedPreferences;

    public static final String PREFS_NAME = "user_prefs";
    public static final String KEY_EMAIL = "email";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_UID = "uid";
    public static final String KEY_NAME = "name";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signin);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );

        mAuth = FirebaseAuth.getInstance();
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnSignup = findViewById(R.id.btnSignup);
        btnLogin = findViewById(R.id.btnLogin);
        tvAlreadyAccount = findViewById(R.id.tvAlreadyAccount);
        progressSignup = findViewById(R.id.progressSignup);

        btnSignup.setOnClickListener(v -> {
            if (!isInternetConnected()) {
                Toast.makeText(SignupActivity.this, "No Internet Connection", Toast.LENGTH_SHORT).show();
                return;
            }
            signupUser();
        });

        btnLogin.setOnClickListener(v -> {
            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
            finish();
        });

        tvAlreadyAccount.setOnClickListener(v -> {
            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
            finish();
        });
    }

    private boolean isInternetConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    private void setLoading(boolean loading) {
        // Toggle spinner and inputs
        progressSignup.setVisibility(loading ? View.VISIBLE : View.GONE);

        btnSignup.setEnabled(!loading);
        btnLogin.setEnabled(!loading);
        tvAlreadyAccount.setEnabled(!loading);

        etName.setEnabled(!loading);
        etEmail.setEnabled(!loading);
        etPassword.setEnabled(!loading);
        etConfirmPassword.setEnabled(!loading);

        // Change sign-up button text to indicate progress
        btnSignup.setText(loading ? "SIGNING UP..." : "SIGN UP");
        // Optional: reduce alpha for a subtle disabled look
        btnSignup.setAlpha(loading ? 0.8f : 1f);
    }

    private void signupUser() {
        setLoading(true);

        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            etName.setError("Name required");
            etName.requestFocus();
            setLoading(false);
            return;
        }
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email required");
            etEmail.requestFocus();
            setLoading(false);
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password required");
            etPassword.requestFocus();
            setLoading(false);
            return;
        }
        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            setLoading(false);
            return;
        }
        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            setLoading(false);
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            FirebaseDatabase.getInstance().getReference("users")
                                    .child(user.getUid())
                                    .setValue(new UserProfile(name, email))
                                    .addOnCompleteListener(t2 -> {
                                        saveUserLogin(name, email, password, user.getUid());
                                        Toast.makeText(SignupActivity.this, "Signup Success", Toast.LENGTH_SHORT).show();
                                        // Navigate — no need to call setLoading(false) because we leave this screen
                                        startActivity(new Intent(SignupActivity.this, DashboardActivity.class));
                                        finish();
                                    });
                        } else {
                            Toast.makeText(SignupActivity.this, "Signup Failed: user is null", Toast.LENGTH_LONG).show();
                            setLoading(false);
                        }
                    } else {
                        Toast.makeText(SignupActivity.this, "Signup Failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                        setLoading(false);
                    }
                });
    }

    private void saveUserLogin(String name, String email, String password, String uid) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_NAME, name);
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_PASSWORD, password); // Store securely in production
        editor.putString(KEY_UID, uid);
        editor.apply();
    }

    @Override
    protected void onStart() {
        super.onStart();
        String email = sharedPreferences.getString(KEY_EMAIL, null);
        String password = sharedPreferences.getString(KEY_PASSWORD, null);
        String uid = sharedPreferences.getString(KEY_UID, null);
        if (email != null && password != null && uid != null) {
            startActivity(new Intent(SignupActivity.this, DashboardActivity.class));
            finish();
        }
    }

    public static class UserProfile {
        public String name, email;
        public UserProfile() {}
        public UserProfile(String name, String email) {
            this.name = name;
            this.email = email;
        }
    }
}