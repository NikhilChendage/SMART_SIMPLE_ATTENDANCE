package com.example.s_a;

import android.content.Intent;
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

public class StudentDivisionsActivity extends AppCompatActivity {

    private LinearLayout studentDivisionsContainer;
    private DatabaseReference studentsRef;
    private String selectedYear = "Second Year";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_student_divisions);

        if (getIntent().hasExtra("SELECTED_YEAR")) {
            selectedYear = getIntent().getStringExtra("SELECTED_YEAR");
        }

        TextView txtStudentYearTitle = findViewById(R.id.txtStudentYearTitle);
        if (txtStudentYearTitle != null) {
            txtStudentYearTitle.setText(selectedYear + " - Divisions");
        }

        studentDivisionsContainer = findViewById(R.id.studentDivisionsContainer);
        studentsRef = FirebaseDatabase.getInstance().getReference("students");

        loadStudentDivisionsFromFirebase();
    }

    private void loadStudentDivisionsFromFirebase() {
        studentsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> activeDivs = new ArrayList<>();

                for (DataSnapshot ds : snapshot.getChildren()) {
                    String year = ds.child("year").getValue(String.class);
                    String div = ds.child("division").getValue(String.class);
                    if (year != null && year.equalsIgnoreCase(selectedYear) && div != null && !div.trim().isEmpty()) {
                        String cleanDiv = "Division " + div.trim().toUpperCase();
                        if (!activeDivs.contains(cleanDiv)) {
                            activeDivs.add(cleanDiv);
                        }
                    }
                }

                if (activeDivs.isEmpty()) {
                    activeDivs.add("Division A");
                    activeDivs.add("Division B");
                }

                renderDivisionCards(activeDivs);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(StudentDivisionsActivity.this, "Failed to load divisions: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderDivisionCards(List<String> divisions) {
        studentDivisionsContainer.removeAllViews();

        for (String divName : divisions) {
            String divLetter = divName.replace("Division", "").trim();

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
            icon.setImageResource(R.drawable.ic_students);
            icon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#059669")));
            icon.setBackgroundResource(R.drawable.ic_launcher_background);
            icon.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#ECFDF5")));
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
            txtTitle.setText(divName);
            txtTitle.setTextSize(18);
            txtTitle.setTypeface(null, Typeface.BOLD);
            txtTitle.setTextColor(Color.parseColor("#0F172A"));
            col.addView(txtTitle);

            TextView txtSub = new TextView(this);
            txtSub.setText("Tap to view " + selectedYear + " " + divName + " students");
            txtSub.setTextSize(13);
            txtSub.setTextColor(Color.parseColor("#64748B"));
            col.addView(txtSub);

            inner.addView(col);

            card.addView(inner);
            card.setOnClickListener(v -> {
                Intent intent = new Intent(StudentDivisionsActivity.this, StudentManagementActivity.class);
                intent.putExtra("SELECTED_YEAR", selectedYear);
                intent.putExtra("SELECTED_DIV", divLetter);
                startActivity(intent);
            });

            studentDivisionsContainer.addView(card);
        }
    }
}
