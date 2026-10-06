package com.example.s_a;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Auto-login check
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        if (prefs.getBoolean("isLoggedIn", false)) {
            String role = prefs.getString("USER_ROLE", "Teacher");
            Class<?> targetActivity = role.equals("HOD") ? HodDashboardActivity.class : TeacherDashboardActivity.class;
            Intent intent = new Intent(this, targetActivity);
            intent.putExtra("USER_ROLE", role);
            startActivity(intent);
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        EditText etUsername = findViewById(R.id.etUsername);
        EditText etPassword = findViewById(R.id.etPassword);
        Button btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email/phone and password", Toast.LENGTH_SHORT).show();
                return;
            }

            // 1. Check HOD in Firebase Realtime Database ("hod" node)
            DatabaseReference hodRef = FirebaseDatabase.getInstance().getReference("hod");
            hodRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot hodSnapshot) {
                    boolean isHod = false;
                    String hodPass = hodSnapshot.child("password").getValue(String.class);
                    String hodPhone = hodSnapshot.child("phone").getValue(String.class);
                    String hodEmail = hodSnapshot.child("email").getValue(String.class);

                    if (hodPass != null && password.equals(hodPass) && (
                            (hodPhone != null && username.equals(hodPhone.trim())) ||
                            (hodEmail != null && username.equalsIgnoreCase(hodEmail.trim())) ||
                            username.equalsIgnoreCase("hod"))) {
                        isHod = true;
                    }

                    if (isHod) {
                        prefs.edit().clear().apply(); // Clear any previous user session
                        prefs.edit()
                                .putBoolean("isLoggedIn", true)
                                .putString("USER_ROLE", "HOD")
                                .putString("LOGGED_IN_PHONE", hodPhone != null ? hodPhone.trim() : "hod")
                                .putString("LOGGED_IN_USER", "HOD")
                                .apply();
                        Toast.makeText(LoginActivity.this, "HOD Login Successful!", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(LoginActivity.this, HodDashboardActivity.class);
                        intent.putExtra("USER_ROLE", "HOD");
                        startActivity(intent);
                        finish();
                    } else {
                        // 2. Check Faculty in Firebase Realtime Database ("faculty" node)
                        DatabaseReference facultyRef = FirebaseDatabase.getInstance().getReference("faculty");
                        facultyRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot facultySnapshot) {
                                boolean isValidTeacher = false;
                                String loggedPhone = "";
                                String loggedEmail = "";
                                String loggedInTeacherName = "";
                                String loggedKey = "";

                                for (DataSnapshot ds : facultySnapshot.getChildren()) {
                                    String phone = ds.child("phone").getValue(String.class);
                                    String email = ds.child("email").getValue(String.class);
                                    String name = ds.child("name").getValue(String.class);
                                    String pass = ds.child("password").getValue(String.class);

                                    if (pass != null && password.equals(pass) && (
                                            (email != null && username.equalsIgnoreCase(email.trim())) ||
                                            (phone != null && username.equals(phone.trim())) ||
                                            (name != null && username.equalsIgnoreCase(name.trim())))) {
                                        isValidTeacher = true;
                                        loggedPhone = phone != null ? phone.trim() : "";
                                        loggedEmail = email != null ? email.trim() : "";
                                        loggedInTeacherName = name != null ? name.trim() : "";
                                        loggedKey = ds.getKey();
                                        break;
                                    }
                                }

                                if (isValidTeacher) {
                                    prefs.edit().clear().apply(); // Clear any previous user session
                                    prefs.edit()
                                            .putBoolean("isLoggedIn", true)
                                            .putString("USER_ROLE", "Teacher")
                                            .putString("LOGGED_IN_PHONE", loggedPhone)
                                            .putString("LOGGED_IN_EMAIL", loggedEmail)
                                            .putString("LOGGED_IN_USER", loggedInTeacherName)
                                            .putString("LOGGED_IN_KEY", loggedKey)
                                            .apply();

                                    Toast.makeText(LoginActivity.this, "Teacher Login Successful!", Toast.LENGTH_SHORT).show();
                                    Intent intent = new Intent(LoginActivity.this, TeacherDashboardActivity.class);
                                    intent.putExtra("USER_ROLE", "Teacher");
                                    startActivity(intent);
                                    finish();
                                } else {
                                    Toast.makeText(LoginActivity.this, "Invalid credentials or user not found in database", Toast.LENGTH_LONG).show();
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Toast.makeText(LoginActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(LoginActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
