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

public class SessionYearsActivity extends AppCompatActivity {

    private LinearLayout yearsContainer;
    private DatabaseReference timetableRef, sessionsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_session_years);

        yearsContainer = findViewById(R.id.yearsContainer);
        timetableRef = FirebaseDatabase.getInstance().getReference("timetable");
        sessionsRef = FirebaseDatabase.getInstance().getReference("sessions");

        loadYearsFromFirebase();
    }

    private void loadYearsFromFirebase() {
        timetableRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot ttSnapshot) {
                List<String> activeYears = new ArrayList<>();

                for (DataSnapshot ds : ttSnapshot.getChildren()) {
                    String year = ds.child("year").getValue(String.class);
                    if (year != null && !year.trim().isEmpty() && !activeYears.contains(year.trim())) {
                        activeYears.add(year.trim());
                    }
                }

                sessionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot sessionSnapshot) {
                        for (DataSnapshot ds : sessionSnapshot.getChildren()) {
                            String year = ds.child("year").getValue(String.class);
                            if (year != null && !year.trim().isEmpty() && !activeYears.contains(year.trim())) {
                                activeYears.add(year.trim());
                            }
                        }

                        if (activeYears.isEmpty()) {
                            activeYears.add("First Year");
                            activeYears.add("Second Year");
                            activeYears.add("Third Year");
                            activeYears.add("Fourth Year");
                        }

                        renderYearCards(activeYears);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (activeYears.isEmpty()) {
                            activeYears.add("Second Year");
                            activeYears.add("Third Year");
                        }
                        renderYearCards(activeYears);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(SessionYearsActivity.this, "Failed to load years: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderYearCards(List<String> years) {
        yearsContainer.removeAllViews();

        for (String yearName : years) {
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
            txtTitle.setText(yearName);
            txtTitle.setTextSize(18);
            txtTitle.setTypeface(null, Typeface.BOLD);
            txtTitle.setTextColor(Color.parseColor("#0F172A"));
            col.addView(txtTitle);

            TextView txtSub = new TextView(this);
            txtSub.setText("Tap to view assigned divisions");
            txtSub.setTextSize(13);
            txtSub.setTextColor(Color.parseColor("#64748B"));
            col.addView(txtSub);

            inner.addView(col);

            card.addView(inner);
            card.setOnClickListener(v -> {
                Intent intent = new Intent(SessionYearsActivity.this, SessionDivisionsActivity.class);
                intent.putExtra("SELECTED_YEAR", yearName);
                startActivity(intent);
            });

            yearsContainer.addView(card);
        }
    }
}
