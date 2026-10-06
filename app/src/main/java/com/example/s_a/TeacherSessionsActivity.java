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

public class TeacherSessionsActivity extends AppCompatActivity {

    private LinearLayout teacherSessionsContainer;
    private ActivityResultLauncher<Intent> createSessionLauncher;
    private DatabaseReference sessionsRef;
    private String loggedInTeacher = "";
    private String loggedInTeacherSubject = "";
    private String currentTab = "TODAY"; // TODAY (default), UPCOMING, PAST, ALL

    private TextView txtTabAll, txtTabToday, txtTabUpcoming, txtTabPast;

    private String selectedYearFilter = null;
    private String selectedDivFilter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_sessions);

        if (getIntent().hasExtra("SELECTED_YEAR")) selectedYearFilter = getIntent().getStringExtra("SELECTED_YEAR");
        if (getIntent().hasExtra("SELECTED_DIV")) selectedDivFilter = getIntent().getStringExtra("SELECTED_DIV");

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        loggedInTeacher = prefs.getString("LOGGED_IN_USER", "").trim();
        String loggedEmail = prefs.getString("LOGGED_IN_EMAIL", "").trim();
        String loggedPhone = prefs.getString("LOGGED_IN_PHONE", "").trim();
        String loggedKey = prefs.getString("LOGGED_IN_KEY", "").trim();

        teacherSessionsContainer = findViewById(R.id.teacherSessionsContainer);
        Button btnOpenCreateSession = findViewById(R.id.btnOpenCreateSession);

        txtTabAll = findViewById(R.id.txtTabAll);
        txtTabToday = findViewById(R.id.txtTabToday);
        txtTabUpcoming = findViewById(R.id.txtTabUpcoming);
        txtTabPast = findViewById(R.id.txtTabPast);

        setupTabListeners();

        sessionsRef = FirebaseDatabase.getInstance().getReference("sessions");

        if (btnOpenCreateSession != null) {
            btnOpenCreateSession.setOnClickListener(v -> {
                Intent intent = new Intent(TeacherSessionsActivity.this, CreateSessionActivity.class);
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

        // Resolve official teacher name & subject from Firebase "faculty" node
        DatabaseReference facultyRef = FirebaseDatabase.getInstance().getReference("faculty");
        facultyRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!loggedKey.isEmpty() && snapshot.hasChild(loggedKey)) {
                    DataSnapshot teacherSnap = snapshot.child(loggedKey);
                    String name = teacherSnap.child("name").getValue(String.class);
                    String subject = teacherSnap.child("subject").getValue(String.class);
                    if (name != null && !name.trim().isEmpty()) {
                        loggedInTeacher = name.trim();
                    }
                    if (subject != null && !subject.trim().isEmpty()) {
                        loggedInTeacherSubject = subject.trim();
                    }
                } else {
                    for (DataSnapshot ds : snapshot.getChildren()) {
                        String phone = ds.child("phone").getValue(String.class);
                        String email = ds.child("email").getValue(String.class);
                        String name = ds.child("name").getValue(String.class);
                        String subject = ds.child("subject").getValue(String.class);

                        boolean matchEmail = !loggedEmail.isEmpty() && email != null && loggedEmail.equalsIgnoreCase(email.trim());
                        boolean matchPhone = !loggedPhone.isEmpty() && phone != null && loggedPhone.equals(phone.trim());
                        boolean matchName = !loggedInTeacher.isEmpty() && name != null && loggedInTeacher.equalsIgnoreCase(name.trim());

                        if (matchEmail || matchPhone || matchName) {
                            if (name != null && !name.trim().isEmpty()) loggedInTeacher = name.trim();
                            if (subject != null && !subject.trim().isEmpty()) loggedInTeacherSubject = subject.trim();
                            prefs.edit().putString("LOGGED_IN_USER", loggedInTeacher).putString("LOGGED_IN_KEY", ds.getKey()).apply();
                            break;
                        }
                    }
                }

                TextView txtTeacherSubtitle = findViewById(R.id.txtTeacherSubtitle);
                if (txtTeacherSubtitle != null && !loggedInTeacher.isEmpty()) {
                    String subText = "Assigned to " + loggedInTeacher;
                    if (!loggedInTeacherSubject.isEmpty()) {
                        subText += " (" + loggedInTeacherSubject + ")";
                    }
                    txtTeacherSubtitle.setText(subText);
                }

                loadTeacherSessionsFromFirebase();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                loadTeacherSessionsFromFirebase();
            }
        });
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

            loadTeacherSessionsFromFirebase();
        };

        if (txtTabAll != null) txtTabAll.setOnClickListener(tabClickListener);
        if (txtTabToday != null) txtTabToday.setOnClickListener(tabClickListener);
        if (txtTabUpcoming != null) txtTabUpcoming.setOnClickListener(tabClickListener);
        if (txtTabPast != null) txtTabPast.setOnClickListener(tabClickListener);
    }

    private void loadTeacherSessionsFromFirebase() {
        sessionsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                teacherSessionsContainer.removeAllViews();
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

                    boolean matchesYear = selectedYearFilter == null || selectedYearFilter.isEmpty() || (year != null && year.equalsIgnoreCase(selectedYearFilter));
                    boolean matchesDiv = selectedDivFilter == null || selectedDivFilter.isEmpty() || (division != null && division.equalsIgnoreCase(selectedDivFilter));

                    // Teacher Session Matching
                    boolean isAssignedToMe = false;

                    // 1. Direct Faculty Name Match
                    if (faculty != null && !faculty.trim().isEmpty()) {
                        String f = faculty.trim().toLowerCase();
                        String t = loggedInTeacher.trim().toLowerCase();

                        if (!t.isEmpty() && (f.contains(t) || t.contains(f) || f.equalsIgnoreCase(t))) {
                            isAssignedToMe = true;
                        } else if (!t.isEmpty()) {
                            String[] parts = t.split("\\s+");
                            for (String part : parts) {
                                if (part.length() >= 3 && f.contains(part)) {
                                    isAssignedToMe = true;
                                    break;
                                }
                            }
                        }
                    }

                    // 2. If faculty is empty/unassigned, match by Teacher's Assigned Subject(s)!
                    if (!isAssignedToMe && (faculty == null || faculty.trim().isEmpty())) {
                        if (subject != null && loggedInTeacherSubject != null && !loggedInTeacherSubject.isEmpty()) {
                            String s = subject.trim().toLowerCase();
                            String[] assignedSubs = loggedInTeacherSubject.split("[,;/]+");
                            for (String sub : assignedSubs) {
                                String ts = sub.trim().toLowerCase();
                                if (!ts.isEmpty() && (s.contains(ts) || ts.contains(s) || s.equalsIgnoreCase(ts))) {
                                    isAssignedToMe = true;
                                    break;
                                }
                            }
                        }
                    }

                    if (matchesYear && matchesDiv && isAssignedToMe && subject != null) {
                        Boolean isCompleted = ds.child("completed").getValue(Boolean.class);
                        String lastAttendanceDate = ds.child("lastAttendanceDate").getValue(String.class);
                        if (isCompleted == null) isCompleted = false;

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

                        // Apply Tab Filter
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

                            addTeacherSessionCard(sessionId, sessionTitle, subject, sessionType, division, batch, date, day, startTime, endTime, sessionCategory);
                        }
                    }
                }

                if (!hasSessions) {
                    TextView txtEmpty = new TextView(TeacherSessionsActivity.this);
                    String emptyMsg = "No " + (currentTab.equals("ALL") ? "" : currentTab.toLowerCase() + " ") + "sessions found";
                    if (!loggedInTeacher.isEmpty()) {
                        emptyMsg += " for " + loggedInTeacher;
                    }
                    emptyMsg += ".";
                    txtEmpty.setText(emptyMsg);
                    txtEmpty.setTextColor(Color.parseColor("#6B7280"));
                    txtEmpty.setTextSize(14);
                    txtEmpty.setPadding(16, 32, 16, 32);
                    teacherSessionsContainer.addView(txtEmpty);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TeacherSessionsActivity.this, "Failed to load sessions: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addTeacherSessionCard(String sessionId, String sessionName, String subject, String sessionType, String division, String batch, String date, String day, String startTime, String endTime, String category) {
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

        // Category Badge Details
        String badgeText = "SESSION";
        String badgeColor = "#2563EB";
        String badgeBg = "#EEF2FF";

        if (category.equals("TODAY")) {
            badgeText = "TODAY";
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

        // Top Row with Title & Badge
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
        btnRow.setWeightSum(2);

        boolean isPastOrCompleted = "PAST".equalsIgnoreCase(category);
        boolean isUpcoming = "UPCOMING".equalsIgnoreCase(category);

        String actionText = isPastOrCompleted ? "Edit Attendance" : (isUpcoming ? "Upcoming Session" : "Take Attendance");
        String actionColor = isPastOrCompleted ? "#2563EB" : (isUpcoming ? "#94A3B8" : "#4F46E5");

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
        bp1.setMarginEnd(4);
        btnOpen.setLayoutParams(bp1);
        btnRow.addView(btnOpen);

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

        inner.addView(btnRow);

        card.addView(inner);
        teacherSessionsContainer.addView(card);
    }

    private void openAttendance(String sessionId, String sessionName, String subject, String sessionType, String division, String batch, String date, String startTime, String endTime) {
        Intent intent = new Intent(TeacherSessionsActivity.this, MainActivity.class);
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
