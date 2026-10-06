package com.example.s_a;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class DashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);

        TextView txtDashboardTitle = findViewById(R.id.txtDashboardTitle);
        String role = getIntent().getStringExtra("USER_ROLE");

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        if (role == null) {
            role = prefs.getString("USER_ROLE", "Teacher");
        }

        if (txtDashboardTitle != null) {
            txtDashboardTitle.setText(role + " Dashboard");
        }

        View imgProfile = findViewById(R.id.imgProfile);
        if (imgProfile != null) {
            imgProfile.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(DashboardActivity.this, imgProfile);
                popup.getMenu().add("Profile");
                popup.setOnMenuItemClickListener(item -> {
                    if ("Profile".equals(item.getTitle())) {
                        startActivity(new Intent(DashboardActivity.this, ProfileActivity.class));
                    }
                    return true;
                });
                popup.show();
            });
        }

        View btnSessions = findViewById(R.id.btnSessions);
        View btnManageStudents = findViewById(R.id.btnManageStudents);
        View btnTimeTable = findViewById(R.id.btnTimeTable);
        View btnManageFaculty = findViewById(R.id.btnManageFaculty);

        if (role.equals("Teacher")) {
            if (btnManageFaculty != null) {
                btnManageFaculty.setVisibility(View.GONE);
            }
            if (btnTimeTable != null) {
                if (btnTimeTable instanceof Button) {
                    ((Button) btnTimeTable).setText("Assigned Time Table");
                } else if (btnTimeTable instanceof TextView) {
                    ((TextView) btnTimeTable).setText("Assigned Time Table");
                }
            }
        }

        if (btnSessions != null) {
            btnSessions.setOnClickListener(v -> {
                startActivity(new Intent(DashboardActivity.this, SessionYearsActivity.class));
            });
        }

        if (btnManageStudents != null) {
            btnManageStudents.setOnClickListener(v -> {
                startActivity(new Intent(DashboardActivity.this, StudentYearsActivity.class));
            });
        }

        if (btnTimeTable != null) {
            btnTimeTable.setOnClickListener(v -> {
                startActivity(new Intent(DashboardActivity.this, TimeTableListActivity.class));
            });
        }

        if (btnManageFaculty != null) {
            btnManageFaculty.setOnClickListener(v -> {
                startActivity(new Intent(DashboardActivity.this, FacultyManagementActivity.class));
            });
        }
    }
}
