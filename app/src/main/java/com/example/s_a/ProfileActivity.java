package com.example.s_a;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ProfileActivity extends AppCompatActivity {

    private EditText etProfileName, etProfileRole, etProfileDesig, etProfileEmail, etProfilePhone, etProfilePassword;
    private TextView txtProfileHeroName, txtRoleBadge;
    private String role = "Teacher";
    private String loggedPhone = "";
    private String loggedEmail = "";
    private String loggedUser = "";
    private String loggedKey = "";
    private String matchedKey = null;
    private DatabaseReference userRef;
    private String currentPassword = "12345";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile);

        etProfileName = findViewById(R.id.etProfileName);
        etProfileRole = findViewById(R.id.etProfileRole);
        etProfileDesig = findViewById(R.id.etProfileDesig);
        etProfileEmail = findViewById(R.id.etProfileEmail);
        etProfilePhone = findViewById(R.id.etProfilePhone);
        etProfilePassword = findViewById(R.id.etProfilePassword);

        txtProfileHeroName = findViewById(R.id.txtProfileHeroName);
        txtRoleBadge = findViewById(R.id.txtRoleBadge);

        Button btnSaveProfile = findViewById(R.id.btnSaveProfile);
        Button btnLogoutProfile = findViewById(R.id.btnLogoutProfile);

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        role = prefs.getString("USER_ROLE", "Teacher");
        loggedPhone = prefs.getString("LOGGED_IN_PHONE", "").trim();
        loggedEmail = prefs.getString("LOGGED_IN_EMAIL", "").trim();
        loggedUser = prefs.getString("LOGGED_IN_USER", "").trim();
        loggedKey = prefs.getString("LOGGED_IN_KEY", "").trim();

        etProfileRole.setText(role);
        if (txtRoleBadge != null) {
            txtRoleBadge.setText(role.equalsIgnoreCase("HOD") ? "HEAD OF DEPARTMENT" : "FACULTY MEMBER");
        }

        if (role.equals("HOD")) {
            userRef = FirebaseDatabase.getInstance().getReference("hod");
        } else {
            userRef = FirebaseDatabase.getInstance().getReference("faculty");
        }

        loadProfileFromFirebase();

        btnSaveProfile.setOnClickListener(v -> saveProfileToFirebase());

        btnLogoutProfile.setOnClickListener(v -> {
            prefs.edit().clear().apply();
            startActivity(new Intent(ProfileActivity.this, LoginActivity.class));
            finishAffinity();
        });
    }

    private void loadProfileFromFirebase() {
        if (role.equals("HOD")) {
            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String name = snapshot.child("name").getValue(String.class);
                        String desig = snapshot.child("designation").getValue(String.class);
                        String email = snapshot.child("email").getValue(String.class);
                        String phone = snapshot.child("phone").getValue(String.class);
                        currentPassword = snapshot.child("password").getValue(String.class);
                        if (currentPassword == null) currentPassword = "admin";

                        etProfileName.setText(name != null ? name : "HOD");
                        if (txtProfileHeroName != null) txtProfileHeroName.setText(name != null ? name : "HOD");
                        etProfileDesig.setText(desig != null ? desig : "Head of Department");
                        etProfileEmail.setText(email != null ? email : "hod@tkiet.ac.in");
                        etProfilePhone.setText(phone != null ? phone : "hod");
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(ProfileActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    DataSnapshot matchedChild = null;

                    if (!loggedKey.isEmpty() && snapshot.hasChild(loggedKey)) {
                        matchedChild = snapshot.child(loggedKey);
                    } else {
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            String phone = ds.child("phone").getValue(String.class);
                            String email = ds.child("email").getValue(String.class);
                            String name = ds.child("name").getValue(String.class);

                            boolean matchEmail = !loggedEmail.isEmpty() && email != null && loggedEmail.equalsIgnoreCase(email.trim());
                            boolean matchPhone = !loggedPhone.isEmpty() && phone != null && loggedPhone.equals(phone.trim());
                            boolean matchName = !loggedUser.isEmpty() && name != null && loggedUser.equalsIgnoreCase(name.trim());

                            if (matchEmail || matchPhone || matchName) {
                                matchedChild = ds;
                                break;
                            }
                        }
                    }

                    if (matchedChild != null) {
                        String name = matchedChild.child("name").getValue(String.class);
                        String desig = matchedChild.child("designation").getValue(String.class);
                        String email = matchedChild.child("email").getValue(String.class);
                        String phone = matchedChild.child("phone").getValue(String.class);
                        currentPassword = matchedChild.child("password").getValue(String.class);
                        if (currentPassword == null) currentPassword = "12345";

                        etProfileName.setText(name != null ? name : "Faculty Member");
                        if (txtProfileHeroName != null) txtProfileHeroName.setText(name != null ? name : "Faculty Member");
                        etProfileDesig.setText(desig != null ? desig : "Assistant Professor");
                        etProfileEmail.setText(email != null ? email : "");
                        etProfilePhone.setText(phone != null ? phone : "");

                        matchedKey = matchedChild.getKey();
                        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                        prefs.edit().putString("LOGGED_IN_KEY", matchedKey).apply();
                    } else {
                        etProfileName.setText(loggedUser);
                        if (txtProfileHeroName != null) txtProfileHeroName.setText(loggedUser);
                        etProfileEmail.setText(loggedEmail);
                        etProfilePhone.setText(loggedPhone);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(ProfileActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void saveProfileToFirebase() {
        String name = etProfileName.getText().toString().trim();
        String desig = etProfileDesig.getText().toString().trim();
        String email = etProfileEmail.getText().toString().trim();
        String phone = etProfilePhone.getText().toString().trim();
        String newPass = etProfilePassword.getText().toString().trim();
        String finalPass = newPass.isEmpty() ? currentPassword : newPass;

        if (name.isEmpty()) {
            Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        if (txtProfileHeroName != null) {
            txtProfileHeroName.setText(name);
        }

        if (role.equals("HOD")) {
            userRef.child("name").setValue(name);
            userRef.child("designation").setValue(desig);
            userRef.child("email").setValue(email);
            userRef.child("phone").setValue(phone);
            userRef.child("password").setValue(finalPass);
            Toast.makeText(this, "HOD Profile & Password Updated Successfully!", Toast.LENGTH_SHORT).show();
        } else {
            String targetKey = matchedKey != null ? matchedKey : loggedKey;
            if (targetKey != null && !targetKey.isEmpty()) {
                userRef.child(targetKey).child("name").setValue(name);
                userRef.child(targetKey).child("designation").setValue(desig);
                userRef.child(targetKey).child("email").setValue(email);
                userRef.child(targetKey).child("phone").setValue(phone);
                userRef.child(targetKey).child("password").setValue(finalPass);

                SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                prefs.edit()
                        .putString("LOGGED_IN_USER", name)
                        .putString("LOGGED_IN_EMAIL", email)
                        .putString("LOGGED_IN_PHONE", phone)
                        .apply();

                Toast.makeText(ProfileActivity.this, "Teacher Profile & Password Updated Successfully!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(ProfileActivity.this, "Faculty record not found in database", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
