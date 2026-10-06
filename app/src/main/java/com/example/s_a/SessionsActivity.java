package com.example.s_a;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class SessionsActivity extends AppCompatActivity {

    private LinearLayout sessionsContainer;
    private ActivityResultLauncher<Intent> createSessionLauncher;
    private DatabaseReference sessionsRef;
    private String userRole = "Teacher";
    private String loggedInTeacher = "";

    private String selectedYearFilter = null;
    private String selectedDivFilter = null;
    private String currentTab = "TODAY"; // TODAY (default), UPCOMING, PAST, ALL

    private TextView txtTabAll, txtTabToday, txtTabUpcoming, txtTabPast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sessions);

        if (getIntent().hasExtra("SELECTED_YEAR")) selectedYearFilter = getIntent().getStringExtra("SELECTED_YEAR");
        if (getIntent().hasExtra("SELECTED_DIV")) selectedDivFilter = getIntent().getStringExtra("SELECTED_DIV");

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        userRole = prefs.getString("USER_ROLE", "Teacher");
        loggedInTeacher = prefs.getString("LOGGED_IN_USER", "").trim();

        TextView txtSessionsTitle = findViewById(R.id.txtSessionsTitle);
        if (txtSessionsTitle != null) {
            String titleText = (selectedYearFilter != null ? selectedYearFilter : "Department") + (selectedDivFilter != null ? " - Div " + selectedDivFilter : "") + " Sessions";
            txtSessionsTitle.setText(titleText);
        }

        txtTabAll = findViewById(R.id.txtTabAll);
        txtTabToday = findViewById(R.id.txtTabToday);
        txtTabUpcoming = findViewById(R.id.txtTabUpcoming);
        txtTabPast = findViewById(R.id.txtTabPast);

        setupTabListeners();

        sessionsContainer = findViewById(R.id.sessionsContainer);
        Button btnOpenCreateSession = findViewById(R.id.btnOpenCreateSession);

        sessionsRef = FirebaseDatabase.getInstance().getReference("sessions");

        if (btnOpenCreateSession != null) {
            btnOpenCreateSession.setOnClickListener(v -> {
                Intent intent = new Intent(SessionsActivity.this, CreateSessionActivity.class);
                createSessionLauncher.launch(intent);
            });
        }

        createSessionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Toast.makeText(this, "Session created and synced to Firebase", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        loadSessionsFromFirebase();
    }

    private void setupTabListeners() {
        View.OnClickListener tabClickListener = v -> {
            txtTabAll.setTextColor(Color.parseColor("#64748B"));
            txtTabAll.setTypeface(null, Typeface.NORMAL);
            txtTabToday.setTextColor(Color.parseColor("#64748B"));
            txtTabToday.setTypeface(null, Typeface.NORMAL);
            txtTabUpcoming.setTextColor(Color.parseColor("#64748B"));
            txtTabUpcoming.setTypeface(null, Typeface.NORMAL);
            txtTabPast.setTextColor(Color.parseColor("#64748B"));
            txtTabPast.setTypeface(null, Typeface.NORMAL);

            TextView selectedTab = (TextView) v;
            selectedTab.setTextColor(Color.parseColor("#2563EB"));
            selectedTab.setTypeface(null, Typeface.BOLD);

            int id = v.getId();
            if (id == R.id.txtTabToday) {
                currentTab = "TODAY";
            } else if (id == R.id.txtTabUpcoming) {
                currentTab = "UPCOMING";
            } else if (id == R.id.txtTabPast) {
                currentTab = "PAST";
            } else {
                currentTab = "ALL";
            }

            loadSessionsFromFirebase();
        };

        if (txtTabAll != null) txtTabAll.setOnClickListener(tabClickListener);
        if (txtTabToday != null) txtTabToday.setOnClickListener(tabClickListener);
        if (txtTabUpcoming != null) txtTabUpcoming.setOnClickListener(tabClickListener);
        if (txtTabPast != null) txtTabPast.setOnClickListener(tabClickListener);
    }

    private void loadSessionsFromFirebase() {
        sessionsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                sessionsContainer.removeAllViews();
                boolean hasSessions = false;

                Calendar now = Calendar.getInstance();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
                String todayDate = dateFormat.format(now.getTime());
                String todayDay = dayFormat.format(now.getTime());

                for (DataSnapshot ds : snapshot.getChildren()) {
                    String sessionId = ds.getKey();
                    String subject = ds.child("subject").getValue(String.class);
                    String faculty = ds.child("faculty").getValue(String.class);
                    String sessionType = ds.child("sessionType").getValue(String.class);
                    String year = ds.child("year").getValue(String.class);
                    String division = ds.child("division").getValue(String.class);
                    String batch = ds.child("batch").getValue(String.class);
                    String date = ds.child("date").getValue(String.class);
                    String day = ds.child("day").getValue(String.class);
                    String startTime = ds.child("startTime").getValue(String.class);
                    String endTime = ds.child("endTime").getValue(String.class);

                    Boolean isCompleted = ds.child("completed").getValue(Boolean.class);
                    String lastAttendanceDate = ds.child("lastAttendanceDate").getValue(String.class);
                    if (isCompleted == null) isCompleted = false;

                    boolean matchesYear = selectedYearFilter == null || selectedYearFilter.isEmpty() || (year != null && year.equalsIgnoreCase(selectedYearFilter));
                    boolean matchesDiv = selectedDivFilter == null || selectedDivFilter.isEmpty() || (division != null && division.equalsIgnoreCase(selectedDivFilter));

                    // For Teachers: show ONLY sessions assigned to this specific teacher or unassigned
                    boolean isForThisTeacher;
                    if (userRole.equalsIgnoreCase("HOD")) {
                        isForThisTeacher = true; // HOD sees all department sessions
                    } else {
                        if (faculty == null || faculty.trim().isEmpty()) {
                            isForThisTeacher = true; // Unassigned sessions visible to teachers
                        } else {
                            String f = faculty.toLowerCase();
                            String t = loggedInTeacher.toLowerCase();
                            isForThisTeacher = f.contains(t) || t.contains(f) || f.equalsIgnoreCase(t);
                        }
                    }

                    if (matchesYear && matchesDiv && isForThisTeacher && subject != null) {
                        boolean isMarkedToday = isCompleted && lastAttendanceDate != null && lastAttendanceDate.equalsIgnoreCase(todayDate);

                        // Calculate category for Tab Filtering
                        String sessionCategory = "OTHER";
                        if (isMarkedToday) {
                            sessionCategory = "PAST";
                        } else if (date != null && !date.trim().isEmpty()) {
                            if (date.equals(todayDate)) sessionCategory = "TODAY";
                            else if (date.compareTo(todayDate) < 0) sessionCategory = "PAST";
                            else sessionCategory = "UPCOMING";
                        } else if (day != null && !day.trim().isEmpty()) {
                            if (day.equalsIgnoreCase(todayDay)) sessionCategory = "TODAY";
                            else sessionCategory = "UPCOMING";
                        }

                        boolean showForTab = currentTab.equals("ALL") ||
                                (currentTab.equals("TODAY") && sessionCategory.equals("TODAY")) ||
                                (currentTab.equals("UPCOMING") && sessionCategory.equals("UPCOMING")) ||
                                (currentTab.equals("PAST") && sessionCategory.equals("PAST"));

                        if (showForTab) {
                            hasSessions = true;
                            String sessionTitle = subject + " (" + (sessionType != null ? sessionType : "Lecture") + " - Div " + (division != null ? division : "A") + (batch != null && !batch.isEmpty() ? " - Batch " + batch : "") + ")";

                            if (faculty != null && !faculty.isEmpty()) {
                                sessionTitle += "\nFaculty: " + faculty;
                            }

                            String dateDayInfo = "";
                            if (date != null && !date.isEmpty()) {
                                dateDayInfo += "Date: " + date;
                            }
                            if (day != null && !day.isEmpty()) {
                                if (!dateDayInfo.isEmpty()) dateDayInfo += " | ";
                                dateDayInfo += "Day: " + day;
                            }
                            if (!dateDayInfo.isEmpty()) {
                                sessionTitle += "\n" + dateDayInfo;
                            }

                            String timeInfo = (startTime != null && !startTime.isEmpty() ? startTime : "") + (endTime != null && !endTime.isEmpty() ? " to " + endTime : "");
                            if (!timeInfo.isEmpty()) {
                                sessionTitle += " | Time: " + timeInfo;
                            }

                            addSessionCard(sessionId, sessionTitle, subject, sessionType, division, batch, date, day, startTime, endTime, sessionCategory);
                        }
                    }
                }

                if (!hasSessions) {
                    TextView txtEmpty = new TextView(SessionsActivity.this);
                    String emptyMsg = "No " + (currentTab.equals("ALL") ? "" : currentTab.toLowerCase() + " ") + "sessions found";
                    if (!loggedInTeacher.isEmpty()) {
                        emptyMsg += " for " + loggedInTeacher;
                    }
                    emptyMsg += ".";
                    txtEmpty.setText(emptyMsg);
                    txtEmpty.setTextColor(Color.parseColor("#6B7280"));
                    txtEmpty.setTextSize(14);
                    txtEmpty.setPadding(16, 24, 16, 24);
                    sessionsContainer.addView(txtEmpty);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(SessionsActivity.this, "Failed to load sessions: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addSessionCard(String sessionId, String sessionName, String subject, String sessionType, String division, String batch, String date, String day, String startTime, String endTime, String category) {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 16);
        card.setLayoutParams(params);
        card.setRadius(16 * getResources().getDisplayMetrics().density);
        card.setCardElevation(3 * getResources().getDisplayMetrics().density);
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        inner.setPadding(pad, pad, pad, pad);

        // Status Badge Calculation
        String badgeText = "SESSION";
        String badgeColor = "#2563EB";
        String badgeBg = "#EEF2FF";

        if (category.equals("TODAY")) {
            badgeText = "TODAY (" + (day != null ? day : "Active") + ")";
            badgeColor = "#059669";
            badgeBg = "#ECFDF5";
        } else if (category.equals("PAST")) {
            badgeText = "PAST SESSION";
            badgeColor = "#64748B";
            badgeBg = "#F1F5F9";
        } else if (category.equals("UPCOMING")) {
            badgeText = "UPCOMING";
            badgeColor = "#2563EB";
            badgeBg = "#EEF2FF";
        } else if (day != null && !day.trim().isEmpty()) {
            badgeText = "TIMETABLE (" + day + ")";
            badgeColor = "#D97706";
            badgeBg = "#FFFBEB";
        }

        // Top Row with Title & Status Badge
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setPadding(0, 0, 0, 8);

        TextView txtTitle = new TextView(this);
        txtTitle.setText(sessionName);
        txtTitle.setTextSize(15);
        txtTitle.setTypeface(null, Typeface.BOLD);
        txtTitle.setTextColor(Color.parseColor("#0F172A"));
        txtTitle.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        topRow.addView(txtTitle);

        TextView txtBadge = new TextView(this);
        txtBadge.setText(badgeText);
        txtBadge.setTextSize(11);
        txtBadge.setTypeface(null, Typeface.BOLD);
        txtBadge.setTextColor(Color.parseColor(badgeColor));
        txtBadge.setBackgroundColor(Color.parseColor(badgeBg));
        txtBadge.setPadding(12, 6, 12, 6);
        topRow.addView(txtBadge);

        inner.addView(topRow);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setWeightSum(userRole.equals("HOD") ? 2 : 1);

        boolean isPast = "PAST".equalsIgnoreCase(category);
        boolean isUpcoming = "UPCOMING".equalsIgnoreCase(category);

        String actionText = isPast ? "Edit Attendance" : (isUpcoming ? "Upcoming Session" : "Take Attendance");
        String actionColor = isPast ? "#2563EB" : (isUpcoming ? "#94A3B8" : "#4F46E5");

        Button btnOpen = new Button(this);
        btnOpen.setText(actionText);
        btnOpen.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(actionColor)));
        btnOpen.setTextColor(Color.WHITE);
        btnOpen.setAllCaps(false);

        if (isUpcoming) {
            btnOpen.setEnabled(false);
            btnOpen.setAlpha(0.6f);
        } else {
            btnOpen.setEnabled(true);
            btnOpen.setAlpha(1.0f);
            btnOpen.setOnClickListener(v -> openAttendance(sessionId, sessionName, subject, sessionType, division, batch, date, startTime, endTime));
        }

        LinearLayout.LayoutParams bp1 = new LinearLayout.LayoutParams(0, (int)(45 * getResources().getDisplayMetrics().density), 1f);
        if (userRole.equals("HOD")) bp1.setMarginEnd(4);
        btnOpen.setLayoutParams(bp1);
        btnRow.addView(btnOpen);

        if (userRole.equals("HOD")) {
            Button btnDelete = new Button(this);
            btnDelete.setText("Delete");
            btnDelete.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#DC2626")));
            btnDelete.setTextColor(Color.WHITE);
            btnDelete.setAllCaps(false);
            LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(0, (int)(45 * getResources().getDisplayMetrics().density), 1f);
            bp2.setMarginStart(4);
            btnDelete.setLayoutParams(bp2);
            btnDelete.setOnClickListener(v -> {
                if (sessionId != null) {
                    sessionsRef.child(sessionId).removeValue()
                            .addOnSuccessListener(aVoid -> Toast.makeText(this, "Session deleted successfully", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete session: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });
            btnRow.addView(btnDelete);
        }

        inner.addView(btnRow);
        card.addView(inner);
        sessionsContainer.addView(card);
    }

    private void openAttendance(String sessionId, String sessionName, String subject, String sessionType, String division, String batch, String date, String startTime, String endTime) {
        Intent intent = new Intent(SessionsActivity.this, MainActivity.class);
        intent.putExtra("SESSION_ID", sessionId);
        intent.putExtra("SESSION_NAME", sessionName);
        intent.putExtra("SESSION_SUBJECT", subject);
        intent.putExtra("SESSION_TYPE", sessionType);
        intent.putExtra("SESSION_DIV", division);
        intent.putExtra("SESSION_BATCH", batch);
        intent.putExtra("SESSION_DATE", date);
        intent.putExtra("SESSION_START_TIME", startTime);
        intent.putExtra("SESSION_END_TIME", endTime);
        startActivity(intent);
    }
}
