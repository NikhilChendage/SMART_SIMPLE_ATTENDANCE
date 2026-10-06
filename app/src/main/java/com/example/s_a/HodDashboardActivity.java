package com.example.s_a;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class HodDashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_hod_dashboard);

        View imgProfile = findViewById(R.id.imgProfile);
        if (imgProfile != null) {
            imgProfile.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(HodDashboardActivity.this, imgProfile);
                popup.getMenu().add("Profile");
                popup.setOnMenuItemClickListener(item -> {
                    if ("Profile".equals(item.getTitle())) {
                        startActivity(new Intent(HodDashboardActivity.this, ProfileActivity.class));
                    }
                    return true;
                });
                popup.show();
            });
        }

        View btnManageFaculty = findViewById(R.id.btnManageFaculty);
        View btnTimeTable = findViewById(R.id.btnTimeTable);
        View btnManageStudents = findViewById(R.id.btnManageStudents);
        View btnSessions = findViewById(R.id.btnSessions);
        View btnAnalytics = findViewById(R.id.btnAnalytics);

        if (btnManageFaculty != null) btnManageFaculty.setOnClickListener(v -> startActivity(new Intent(this, FacultyManagementActivity.class)));
        if (btnTimeTable != null) btnTimeTable.setOnClickListener(v -> startActivity(new Intent(this, TimeTableListActivity.class)));
        if (btnManageStudents != null) btnManageStudents.setOnClickListener(v -> startActivity(new Intent(this, StudentYearsActivity.class)));
        if (btnSessions != null) btnSessions.setOnClickListener(v -> startActivity(new Intent(this, SessionYearsActivity.class)));
        if (btnAnalytics != null) btnAnalytics.setOnClickListener(v -> startActivity(new Intent(this, AttendanceAnalyticsActivity.class)));

        TextView txtTotalStaffStat = findViewById(R.id.txtTotalStaffStat);
        TextView txtTotalTimetablesStat = findViewById(R.id.txtTotalTimetablesStat);
        TextView txtTotalStudentsStat = findViewById(R.id.txtTotalStudentsStat);

        // Real-time Total Students Count
        FirebaseDatabase.getInstance().getReference("students").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long count = snapshot.getChildrenCount();
                if (txtTotalStudentsStat != null) {
                    txtTotalStudentsStat.setText(count + " Total");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (txtTotalStudentsStat != null) {
                    txtTotalStudentsStat.setText("0 Total");
                }
            }
        });

        // Real-time Staff Count
        FirebaseDatabase.getInstance().getReference("faculty").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long count = snapshot.getChildrenCount();
                if (txtTotalStaffStat != null) {
                    txtTotalStaffStat.setText(count + " Staff");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (txtTotalStaffStat != null) {
                    txtTotalStaffStat.setText("0 Staff");
                }
            }
        });

        // Real-time Timetable Count
        FirebaseDatabase.getInstance().getReference("timetable").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long count = snapshot.getChildrenCount();
                if (txtTotalTimetablesStat != null) {
                    txtTotalTimetablesStat.setText(count + " Synced");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (txtTotalTimetablesStat != null) {
                    txtTotalTimetablesStat.setText("0 Synced");
                }
            }
        });
    }
}
