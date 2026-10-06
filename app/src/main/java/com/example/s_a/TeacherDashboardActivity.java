package com.example.s_a;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
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

public class TeacherDashboardActivity extends AppCompatActivity {

    private LinearLayout todaySessionsContainer;
    private DatabaseReference sessionsRef;
    private String loggedInTeacherName = "";
    private String loggedInTeacherSubject = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_teacher_dashboard);

        View imgProfile = findViewById(R.id.imgProfile);
        imgProfile.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(TeacherDashboardActivity.this, imgProfile);
            popup.getMenu().add("Profile");
            popup.setOnMenuItemClickListener(item -> {
                if ("Profile".equals(item.getTitle())) {
                    startActivity(new Intent(TeacherDashboardActivity.this, ProfileActivity.class));
                }
                return true;
            });
            popup.show();
        });

        View btnSessions = findViewById(R.id.btnSessions);
        View btnTimeTable = findViewById(R.id.btnTimeTable);
        View btnManageStudents = findViewById(R.id.btnManageStudents);
        View btnAnalytics = findViewById(R.id.btnAnalytics);

        btnSessions.setOnClickListener(v -> startActivity(new Intent(this, SessionYearsActivity.class)));
        btnTimeTable.setOnClickListener(v -> startActivity(new Intent(this, TimeTableListActivity.class)));
        btnManageStudents.setOnClickListener(v -> startActivity(new Intent(this, StudentYearsActivity.class)));
        if (btnAnalytics != null) btnAnalytics.setOnClickListener(v -> startActivity(new Intent(this, AttendanceAnalyticsActivity.class)));

        todaySessionsContainer = findViewById(R.id.todaySessionsContainer);
        sessionsRef = FirebaseDatabase.getInstance().getReference("sessions");

        loadTodaySessions();
    }

    private void loadTodaySessions() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
        String todayDayName = dayFormat.format(calendar.getTime()); // e.g. "Monday"

        SimpleDateFormat dateFormat1 = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String todayDate1 = dateFormat1.format(calendar.getTime()); // e.g. "2025-05-18"

        SimpleDateFormat dateFormat2 = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        String todayDate2 = dateFormat2.format(calendar.getTime()); // e.g. "18-05-2025"

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String loggedUser = prefs.getString("LOGGED_IN_USER", "").trim();
        String loggedEmail = prefs.getString("LOGGED_IN_EMAIL", "").trim();
        String loggedPhone = prefs.getString("LOGGED_IN_PHONE", "").trim();
        String loggedKey = prefs.getString("LOGGED_IN_KEY", "").trim();

        // Resolve teacher's official name and subject from Firebase "faculty" node
        DatabaseReference facultyRef = FirebaseDatabase.getInstance().getReference("faculty");
        facultyRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!loggedKey.isEmpty() && snapshot.hasChild(loggedKey)) {
                    DataSnapshot teacherSnap = snapshot.child(loggedKey);
                    String name = teacherSnap.child("name").getValue(String.class);
                    String subject = teacherSnap.child("subject").getValue(String.class);
                    if (name != null && !name.trim().isEmpty()) {
                        loggedInTeacherName = name.trim();
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
                        boolean matchName = !loggedUser.isEmpty() && name != null && loggedUser.equalsIgnoreCase(name.trim());

                        if (matchEmail || matchPhone || matchName) {
                            if (name != null && !name.trim().isEmpty()) loggedInTeacherName = name.trim();
                            if (subject != null && !subject.trim().isEmpty()) loggedInTeacherSubject = subject.trim();
                            prefs.edit().putString("LOGGED_IN_USER", loggedInTeacherName).putString("LOGGED_IN_KEY", ds.getKey()).apply();
                            break;
                        }
                    }
                }

                if (loggedInTeacherName.isEmpty()) {
                    loggedInTeacherName = loggedUser;
                }

                TextView txtDashboardTitle = findViewById(R.id.txtDashboardTitle);
                if (txtDashboardTitle != null && !loggedInTeacherName.isEmpty()) {
                    txtDashboardTitle.setText(loggedInTeacherName + " Dashboard");
                }

                // Query Today's Sessions
                sessionsRef.addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        todaySessionsContainer.removeAllViews();
                        boolean hasSessions = false;

                        for (DataSnapshot ds : snapshot.getChildren()) {
                            String sessionId = ds.getKey();
                            String subject = ds.child("subject").getValue(String.class);
                            String faculty = ds.child("faculty").getValue(String.class);
                            String sessionType = ds.child("sessionType").getValue(String.class);
                            String division = ds.child("division").getValue(String.class);
                            String batch = ds.child("batch").getValue(String.class);
                            String date = ds.child("date").getValue(String.class);
                            String day = ds.child("day").getValue(String.class);
                            String startTime = ds.child("startTime").getValue(String.class);
                            String endTime = ds.child("endTime").getValue(String.class);

                            // Robust Date & Day Matching for TODAY
                            boolean isToday = false;
                            if (date != null && !date.trim().isEmpty()) {
                                String cleanDate = date.trim().replaceAll("[/.]", "-");
                                isToday = cleanDate.equalsIgnoreCase(todayDate1) || cleanDate.equalsIgnoreCase(todayDate2);
                            }

                            if (!isToday && day != null && !day.trim().isEmpty()) {
                                String cleanDay = day.trim().toLowerCase();
                                String cleanTodayDay = todayDayName.trim().toLowerCase();
                                isToday = cleanDay.equalsIgnoreCase(cleanTodayDay) ||
                                          cleanDay.contains(cleanTodayDay) ||
                                          cleanTodayDay.contains(cleanDay) ||
                                          (cleanDay.length() >= 3 && cleanTodayDay.startsWith(cleanDay.substring(0, 3)));
                            }

                            if (!isToday && (date == null || date.trim().isEmpty()) && (day == null || day.trim().isEmpty())) {
                                isToday = true;
                            }

                            // Subject & Teacher Matching
                            boolean isForThisTeacher = false;

                            // 1. Direct Faculty Name Match
                            if (faculty != null && !faculty.trim().isEmpty()) {
                                String f = faculty.trim().toLowerCase();
                                String t = loggedInTeacherName.trim().toLowerCase();

                                if (!t.isEmpty() && (f.contains(t) || t.contains(f) || f.equalsIgnoreCase(t))) {
                                    isForThisTeacher = true;
                                } else if (!t.isEmpty()) {
                                    String[] teacherParts = t.split("\\s+");
                                    for (String part : teacherParts) {
                                        if (part.length() >= 3 && f.contains(part)) {
                                            isForThisTeacher = true;
                                            break;
                                        }
                                    }
                                }
                            }

                            // 2. If faculty is empty/unassigned, match by Teacher's Assigned Subject(s)!
                            if (!isForThisTeacher && (faculty == null || faculty.trim().isEmpty())) {
                                if (subject != null && loggedInTeacherSubject != null && !loggedInTeacherSubject.isEmpty()) {
                                    String s = subject.trim().toLowerCase();
                                    String[] assignedSubs = loggedInTeacherSubject.split("[,;/]+");
                                    for (String sub : assignedSubs) {
                                        String ts = sub.trim().toLowerCase();
                                        if (!ts.isEmpty() && (s.contains(ts) || ts.contains(s) || s.equalsIgnoreCase(ts))) {
                                            isForThisTeacher = true;
                                            break;
                                        }
                                    }
                                }
                            }

                            if (isToday && isForThisTeacher && subject != null) {
                                hasSessions = true;
                                String sessionTitle = subject + " (" + (sessionType != null ? sessionType : "Lecture") + " - Div " + (division != null ? division : "A") + (batch != null && !batch.isEmpty() ? " - Batch " + batch : "") + ")";

                                if (faculty != null && !faculty.isEmpty()) {
                                    sessionTitle += "\nFaculty: " + faculty;
                                }

                                String timeInfo = (startTime != null && !startTime.isEmpty() ? startTime : "") + (endTime != null && !endTime.isEmpty() ? " to " + endTime : "");
                                if (!timeInfo.isEmpty()) {
                                    sessionTitle += "\nTime: " + timeInfo;
                                }

                                addTodaySessionCard(sessionId, sessionTitle, subject, sessionType, division, batch, date, startTime, endTime);
                            }
                        }

                        if (!hasSessions) {
                            TextView txtEmpty = new TextView(TeacherDashboardActivity.this);
                            String emptyMsg = "No sessions assigned for today (" + todayDayName + ")";
                            if (!loggedInTeacherName.isEmpty()) {
                                emptyMsg += " for " + loggedInTeacherName;
                            }
                            emptyMsg += ".";
                            txtEmpty.setText(emptyMsg);
                            txtEmpty.setTextColor(Color.parseColor("#6B7280"));
                            txtEmpty.setTextSize(14);
                            txtEmpty.setPadding(8, 16, 8, 16);
                            todaySessionsContainer.addView(txtEmpty);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(TeacherDashboardActivity.this, "Failed to load today's sessions: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Fallback
            }
        });
    }

    private void addTodaySessionCard(String sessionId, String sessionName, String subject, String sessionType, String division, String batch, String date, String startTime, String endTime) {
        CardView card = new CardView(TeacherDashboardActivity.this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 12);
        card.setLayoutParams(params);
        card.setRadius(16 * getResources().getDisplayMetrics().density);
        card.setCardElevation(2 * getResources().getDisplayMetrics().density);
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout inner = new LinearLayout(TeacherDashboardActivity.this);
        inner.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        inner.setPadding(pad, pad, pad, pad);

        TextView txtTitle = new TextView(TeacherDashboardActivity.this);
        txtTitle.setText(sessionName);
        txtTitle.setTextSize(15);
        txtTitle.setTypeface(null, Typeface.BOLD);
        txtTitle.setTextColor(Color.parseColor("#1F2937"));
        txtTitle.setPadding(0, 0, 0, 12);
        inner.addView(txtTitle);

        Button btnOpen = new Button(TeacherDashboardActivity.this);
        btnOpen.setText("Take Attendance");
        btnOpen.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4F46E5")));
        btnOpen.setTextColor(Color.WHITE);
        btnOpen.setAllCaps(false);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int)(45 * getResources().getDisplayMetrics().density)
        );
        btnOpen.setLayoutParams(bp);
        btnOpen.setOnClickListener(v -> {
            Intent intent = new Intent(TeacherDashboardActivity.this, MainActivity.class);
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
        });
        inner.addView(btnOpen);

        card.addView(inner);
        todaySessionsContainer.addView(card);
    }
}
