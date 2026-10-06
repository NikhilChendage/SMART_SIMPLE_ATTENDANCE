package com.example.s_a;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;

public class AttendanceAnalyticsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_attendance_analytics);

        MaterialCardView btnSubjectAnalytics = findViewById(R.id.btnSubjectAnalytics);
        MaterialCardView btnStudentAnalytics = findViewById(R.id.btnStudentAnalytics);

        if (btnSubjectAnalytics != null) {
            btnSubjectAnalytics.setOnClickListener(v -> startActivity(new Intent(AttendanceAnalyticsActivity.this, SubjectListActivity.class)));
        }

        if (btnStudentAnalytics != null) {
            btnStudentAnalytics.setOnClickListener(v -> startActivity(new Intent(AttendanceAnalyticsActivity.this, StudentAnalyticsActivity.class)));
        }
    }
}
