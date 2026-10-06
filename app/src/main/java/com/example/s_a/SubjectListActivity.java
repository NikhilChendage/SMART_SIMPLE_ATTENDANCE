package com.example.s_a;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

import java.util.ArrayList;
import java.util.List;

public class SubjectListActivity extends AppCompatActivity {

    private LinearLayout subjectsContainer;
    private DatabaseReference facultyRef, sessionsRef;
    private String userRole = "Teacher";
    private String loggedInTeacherName = "";
    private String loggedInTeacherSubject = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_subject_list);

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        userRole = prefs.getString("USER_ROLE", "Teacher");
        loggedInTeacherName = prefs.getString("LOGGED_IN_USER", "").trim();

        TextView txtSubtitle = findViewById(R.id.txtSubjectListSubtitle);
        if (txtSubtitle != null && !loggedInTeacherName.isEmpty() && userRole.equalsIgnoreCase("Teacher")) {
            txtSubtitle.setText("Assigned subjects for " + loggedInTeacherName);
        }

        subjectsContainer = findViewById(R.id.subjectsContainer);
        facultyRef = FirebaseDatabase.getInstance().getReference("faculty");
        sessionsRef = FirebaseDatabase.getInstance().getReference("sessions");

        loadSubjectsFromFirebase();
    }

    private void loadSubjectsFromFirebase() {
        facultyRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot facultySnapshot) {
                for (DataSnapshot ds : facultySnapshot.getChildren()) {
                    String name = ds.child("name").getValue(String.class);
                    String sub = ds.child("subject").getValue(String.class);

                    if (name != null && !loggedInTeacherName.isEmpty() && (name.equalsIgnoreCase(loggedInTeacherName) || name.toLowerCase().contains(loggedInTeacherName.toLowerCase()))) {
                        if (sub != null && !sub.trim().isEmpty()) {
                            loggedInTeacherSubject = sub.trim();
                        }
                        break;
                    }
                }

                sessionsRef.addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot sessionSnapshot) {
                        List<String> subjectList = new ArrayList<>();

                        for (DataSnapshot ds : sessionSnapshot.getChildren()) {
                            String sub = ds.child("subject").getValue(String.class);
                            String faculty = ds.child("faculty").getValue(String.class);

                            // For Teacher: SHOW ONLY ASSIGNED SUBJECTS! For HOD: SHOW ALL SUBJECTS!
                            boolean isForTeacher = userRole.equalsIgnoreCase("HOD") ||
                                    (loggedInTeacherSubject != null && sub != null && loggedInTeacherSubject.toLowerCase().contains(sub.toLowerCase())) ||
                                    (faculty != null && !loggedInTeacherName.isEmpty() && faculty.toLowerCase().contains(loggedInTeacherName.toLowerCase()));

                            if (isForTeacher && sub != null && !sub.trim().isEmpty() && !subjectList.contains(sub.trim())) {
                                subjectList.add(sub.trim());
                            }
                        }

                        if (subjectList.isEmpty()) {
                            if (!loggedInTeacherSubject.isEmpty()) {
                                String[] split = loggedInTeacherSubject.split("[,;/]+");
                                for (String s : split) {
                                    if (!s.trim().isEmpty() && !subjectList.contains(s.trim())) {
                                        subjectList.add(s.trim());
                                    }
                                }
                            }
                        }

                        if (subjectList.isEmpty()) {
                            subjectList.add("JAVA PROGRAMMING");
                            subjectList.add("MACHINE LEARNING");
                        }

                        renderSubjectCards(subjectList);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(SubjectListActivity.this, "Failed to load subjects: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(SubjectListActivity.this, "Failed to load faculty: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderSubjectCards(List<String> subjects) {
        subjectsContainer.removeAllViews();

        for (String subjectName : subjects) {
            CardView card = new CardView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 16);
            card.setLayoutParams(params);
            card.setRadius(18 * getResources().getDisplayMetrics().density);
            card.setCardElevation(4 * getResources().getDisplayMetrics().density);
            card.setCardBackgroundColor(Color.WHITE);

            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.HORIZONTAL);
            inner.setGravity(Gravity.CENTER_VERTICAL);
            int pad = (int) (18 * getResources().getDisplayMetrics().density);
            inner.setPadding(pad, pad, pad, pad);

            ImageView icon = new ImageView(this);
            icon.setImageResource(R.drawable.ic_attendance);
            icon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#2563EB")));
            icon.setBackgroundResource(R.drawable.ic_launcher_background);
            icon.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EEF2FF")));
            int iconSize = (int) (48 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.setMarginEnd((int) (16 * getResources().getDisplayMetrics().density));
            icon.setLayoutParams(iconParams);
            icon.setPadding(12, 12, 12, 12);
            inner.addView(icon);

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView txtTitle = new TextView(this);
            txtTitle.setText(subjectName);
            txtTitle.setTextSize(18);
            txtTitle.setTypeface(null, Typeface.BOLD);
            txtTitle.setTextColor(Color.parseColor("#0F172A"));
            col.addView(txtTitle);

            TextView txtSub = new TextView(this);
            txtSub.setText("Tap to view " + subjectName + " attendance reports");
            txtSub.setTextSize(13);
            txtSub.setTextColor(Color.parseColor("#64748B"));
            col.addView(txtSub);

            inner.addView(col);

            card.addView(inner);
            card.setOnClickListener(v -> {
                Intent intent = new Intent(SubjectListActivity.this, SubjectAnalyticsActivity.class);
                intent.putExtra("SELECTED_SUBJECT", subjectName);
                startActivity(intent);
            });

            subjectsContainer.addView(card);
        }
    }
}
