package com.codelearninapp.pythmaster;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private EditText inputEmail, inputPassword;
    private AppCompatButton btnLogin, btnSignup;
    private ProgressBar progressLogin;

    private FirebaseAuth mAuth;
    private SharedPreferences sharedPreferences;

    public static final String PREFS_NAME = "user_prefs";
    public static final String KEY_EMAIL = "email";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_UID = "uid";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        mAuth = FirebaseAuth.getInstance();
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        inputEmail = findViewById(R.id.input_email);
        inputPassword = findViewById(R.id.input_password);
        btnLogin = findViewById(R.id.btn_login);
        btnSignup = findViewById(R.id.btn_signup);
        progressLogin = findViewById(R.id.progressLogin);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );

        btnLogin.setOnClickListener(view -> {
            if (!isInternetConnected()) {
                Toast.makeText(LoginActivity.this, "No Internet Connection", Toast.LENGTH_SHORT).show();
                return;
            }
            loginUser();
        });

        btnSignup.setOnClickListener(view ->
                startActivity(new Intent(LoginActivity.this, SignupActivity.class)));
    }

    private boolean isInternetConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    private void setLoading(boolean loading) {
        progressLogin.setVisibility(loading ? View.VISIBLE : View.GONE);

        btnLogin.setEnabled(!loading);
        btnSignup.setEnabled(!loading);
        inputEmail.setEnabled(!loading);
        inputPassword.setEnabled(!loading);

        btnLogin.setText(loading ? "LOGGING IN..." : "Login");
        btnLogin.setAlpha(loading ? 0.9f : 1f);
    }

    private void loginUser() {
        String email = inputEmail.getText().toString().trim();
        String password = inputPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            inputEmail.setError("Email or name required");
            inputEmail.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            inputPassword.setError("Password required");
            inputPassword.requestFocus();
            return;
        }

        setLoading(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            saveUserLogin(email, password, user.getUid());
                        }
                        Toast.makeText(LoginActivity.this, "Login Success", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(LoginActivity.this, DashboardActivity.class));
                        finish(); // no need to setLoading(false) because we're leaving
                    } else {
                        Toast.makeText(LoginActivity.this, "Authentication Failed: " +
                                        (task.getException() != null ? task.getException().getMessage() : ""),
                                Toast.LENGTH_LONG).show();
                        setLoading(false);
                    }
                });
    }

    private void saveUserLogin(String email, String password, String uid) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_PASSWORD, password); // Store securely in production
        editor.putString(KEY_UID, uid);
        editor.apply();
    }

    private boolean isAlreadyLoggedIn() {
        String email = sharedPreferences.getString(KEY_EMAIL, null);
        String password = sharedPreferences.getString(KEY_PASSWORD, null);
        String uid = sharedPreferences.getString(KEY_UID, null);
        return email != null && password != null && uid != null;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (isAlreadyLoggedIn()) {
            startActivity(new Intent(LoginActivity.this, DashboardActivity.class));
            finish();
        } else {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                startActivity(new Intent(LoginActivity.this, DashboardActivity.class));
                finish();
            }
        }
    }
}