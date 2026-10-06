package com.example.s_a;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StudentAttendanceActivity extends AppCompatActivity {

    public static class AttendanceRecord {
        String subject, date, type, status;

        public AttendanceRecord(String subject, String date, String type, String status) {
            this.subject = subject;
            this.date = date;
            this.type = type;
            this.status = status; // "Present" or "Absent"
        }
    }

    private final List<AttendanceRecord> allRecords = new ArrayList<>();
    private TextView statTotal, statPresent, statPercentage;
    private LinearLayout attendanceListContainer;
    private String selectedSubject = "Overall";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_student_attendance);

        String studentName = getIntent().getStringExtra("STUDENT_NAME");
        String studentRoll = getIntent().getStringExtra("STUDENT_ROLL");
        if (studentName == null) studentName = "Student";
        if (studentRoll == null) studentRoll = "206";

        TextView txtNameHeader = findViewById(R.id.txtStudentNameHeader);
        TextView txtSubHeader = findViewById(R.id.txtStudentSubHeader);
        txtNameHeader.setText(studentName);
        txtSubHeader.setText("Roll No: " + studentRoll);

        statTotal = findViewById(R.id.statTotal);
        statPresent = findViewById(R.id.statPresent);
        statPercentage = findViewById(R.id.statPercentage);
        attendanceListContainer = findViewById(R.id.attendanceListContainer);

        // Populate sample attendance records across subjects
        allRecords.add(new AttendanceRecord("Computer Science 101", "2026-03-25", "Lecture", "Present"));
        allRecords.add(new AttendanceRecord("Computer Science 101", "2026-03-26", "Lecture", "Present"));
        allRecords.add(new AttendanceRecord("Computer Science 101", "2026-03-27", "Practical", "Absent"));
        allRecords.add(new AttendanceRecord("Computer Science 101", "2026-03-28", "Lecture", "Present"));

        allRecords.add(new AttendanceRecord("Data Structures", "2026-03-25", "Lecture", "Present"));
        allRecords.add(new AttendanceRecord("Data Structures", "2026-03-27", "Practical", "Present"));
        allRecords.add(new AttendanceRecord("Data Structures", "2026-03-29", "Lecture", "Present"));
        allRecords.add(new AttendanceRecord("Data Structures", "2026-03-30", "Tutorial", "Present"));

        allRecords.add(new AttendanceRecord("Database Systems", "2026-03-26", "Lecture", "Present"));
        allRecords.add(new AttendanceRecord("Database Systems", "2026-03-28", "Practical", "Absent"));
        allRecords.add(new AttendanceRecord("Database Systems", "2026-03-30", "Lecture", "Present"));
        allRecords.add(new AttendanceRecord("Database Systems", "2026-03-31", "Lecture", "Present"));

        setupTabs();
        refreshAttendanceView();
    }

    private void setupTabs() {
        TextView tabOverall = findViewById(R.id.tabOverall);
        TextView tabSub1 = findViewById(R.id.tabSub1);
        TextView tabSub2 = findViewById(R.id.tabSub2);
        TextView tabSub3 = findViewById(R.id.tabSub3);

        TextView[] tabs = {tabOverall, tabSub1, tabSub2, tabSub3};
        String[] subjects = {"Overall", "Computer Science 101", "Data Structures", "Database Systems"};

        for (int i = 0; i < tabs.length; i++) {
            final String sub = subjects[i];
            final TextView t = tabs[i];
            t.setOnClickListener(v -> {
                selectedSubject = sub;
                for (TextView tv : tabs) {
                    tv.setTextColor(Color.parseColor("#757575"));
                    tv.setTypeface(null, Typeface.NORMAL);
                }
                t.setTextColor(Color.parseColor("#3461E7"));
                t.setTypeface(null, Typeface.BOLD);
                refreshAttendanceView();
            });
        }
    }

    private void refreshAttendanceView() {
        List<AttendanceRecord> filtered = new ArrayList<>();
        int presentCount = 0;

        for (AttendanceRecord r : allRecords) {
            if (selectedSubject.equals("Overall") || r.subject.equalsIgnoreCase(selectedSubject)) {
                filtered.add(r);
                if (r.status.equals("Present")) presentCount++;
            }
        }

        int totalCount = filtered.size();
        float percentage = totalCount > 0 ? ((float) presentCount / totalCount) * 100 : 0f;

        statTotal.setText("Total Classes\n" + totalCount);
        statPresent.setText("Present\n" + presentCount);
        statPercentage.setText(String.format(Locale.getDefault(), "Attendance\n%.1f%%", percentage));

        attendanceListContainer.removeAllViews();

        for (AttendanceRecord record : filtered) {
            CardView card = new CardView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 12);
            card.setLayoutParams(params);
            card.setRadius(12 * getResources().getDisplayMetrics().density);
            card.setCardElevation(2 * getResources().getDisplayMetrics().density);
            card.setCardBackgroundColor(Color.WHITE);

            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            inner.setPadding(pad, pad, pad, pad);

            TextView txtSub = new TextView(this);
            txtSub.setText(record.subject);
            txtSub.setTextSize(16);
            txtSub.setTypeface(null, Typeface.BOLD);
            txtSub.setTextColor(Color.parseColor("#222222"));
            inner.addView(txtSub);

            TextView txtDetails = new TextView(this);
            txtDetails.setText("Date: " + record.date + " • Type: " + record.type);
            txtDetails.setTextSize(14);
            txtDetails.setTextColor(Color.parseColor("#757575"));
            txtDetails.setPadding(0, 4, 0, 8);
            inner.addView(txtDetails);

            TextView txtStatus = new TextView(this);
            txtStatus.setText("Status: " + record.status);
            txtStatus.setTextSize(14);
            txtStatus.setTypeface(null, Typeface.BOLD);
            txtStatus.setTextColor(record.status.equals("Present") ? Color.parseColor("#16803C") : Color.parseColor("#D32F2F"));
            inner.addView(txtStatus);

            card.addView(inner);
            attendanceListContainer.addView(card);
        }
    }
}
