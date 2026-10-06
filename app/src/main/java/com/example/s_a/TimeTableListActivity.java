package com.example.s_a;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class TimeTableListActivity extends AppCompatActivity {

    public static class TimeTableItem {
        public String id;
        public String title;
        public String year;
        public String division;
        public String semester;
        public String academicYear;

        public TimeTableItem() {}

        public TimeTableItem(String id, String title, String year, String division, String semester, String academicYear) {
            this.id = id;
            this.title = title;
            this.year = year;
            this.division = division;
            this.semester = semester;
            this.academicYear = academicYear;
        }
    }

    private final List<TimeTableItem> ttList = new ArrayList<>();
    private LinearLayout ttContainer;
    private DatabaseReference ttRef;
    private String userRole = "Teacher";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_timetable_list);

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        userRole = prefs.getString("USER_ROLE", "Teacher");

        ttContainer = findViewById(R.id.txtTtHeader != 0 ? R.id.ttContainer : R.id.ttContainer); // safe
        Button btnCreateTimeTable = findViewById(R.id.btnCreateTimeTable);

        if (userRole.equals("Teacher")) {
            btnCreateTimeTable.setVisibility(View.GONE);
        }

        ttRef = FirebaseDatabase.getInstance().getReference("timetable");

        btnCreateTimeTable.setOnClickListener(v -> showCreateDialog(null));

        loadTimeTablesFromFirebase();
    }

    private void loadTimeTablesFromFirebase() {
        ttRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                ttList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    TimeTableItem item = ds.getValue(TimeTableItem.class);
                    if (item != null) {
                        if (item.id == null) item.id = ds.getKey();
                        ttList.add(item);
                    }
                }
                refreshList();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TimeTableListActivity.this, "Failed to load timetables: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshList() {
        ttContainer.removeAllViews();
        for (int i = 0; i < ttList.size(); i++) {
            TimeTableItem item = ttList.get(i);
            CardView card = new CardView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 16);
            card.setLayoutParams(params);
            card.setRadius(16 * getResources().getDisplayMetrics().density);
            card.setCardElevation(2 * getResources().getDisplayMetrics().density);
            card.setCardBackgroundColor(Color.WHITE);

            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (20 * getResources().getDisplayMetrics().density);
            inner.setPadding(pad, pad, pad, pad);

            TextView txtTitle = new TextView(this);
            txtTitle.setText(item.title);
            txtTitle.setTextSize(18);
            txtTitle.setTypeface(null, Typeface.BOLD);
            txtTitle.setTextColor(Color.parseColor("#0F172A"));
            inner.addView(txtTitle);

            TextView txtSub = new TextView(this);
            txtSub.setText("Sem: " + item.semester + " | Academic Year: " + item.academicYear);
            txtSub.setTextSize(14);
            txtSub.setTextColor(Color.parseColor("#64748B"));
            txtSub.setPadding(0, 4, 0, 16);
            inner.addView(txtSub);

            // Action Buttons Row
            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setWeightSum(userRole.equals("HOD") ? 3 : 1);

            int marginPx = (int)(4 * getResources().getDisplayMetrics().density);

            Button btnOpen = new Button(this);
            btnOpen.setText(userRole.equals("HOD") ? "Open" : "Open & Update Slots");
            btnOpen.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4F46E5")));
            btnOpen.setTextColor(Color.WHITE);
            btnOpen.setAllCaps(false);
            LinearLayout.LayoutParams bp1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, userRole.equals("HOD") ? 1.2f : 1f);
            bp1.setMarginEnd(marginPx);
            btnOpen.setLayoutParams(bp1);
            btnOpen.setOnClickListener(v -> {
                Intent intent = new Intent(TimeTableListActivity.this, TimeTableEditorActivity.class);
                intent.putExtra("TT_ID", item.id);
                intent.putExtra("TT_TITLE", item.title);
                intent.putExtra("TT_YEAR", item.year);
                intent.putExtra("TT_DIV", item.division);
                intent.putExtra("TT_SEM", item.semester);
                intent.putExtra("TT_ACADEMIC_YEAR", item.academicYear);
                startActivity(intent);
            });
            btnRow.addView(btnOpen);

            if (userRole.equals("HOD")) {
                Button btnEdit = new Button(this);
                btnEdit.setText("Edit");
                btnEdit.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#485465")));
                btnEdit.setTextColor(Color.WHITE);
                btnEdit.setAllCaps(false);
                LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f);
                bp2.setMargins(marginPx, 0, marginPx, 0);
                btnEdit.setLayoutParams(bp2);
                btnEdit.setOnClickListener(v -> showCreateDialog(item));
                btnRow.addView(btnEdit);

                Button btnDelete = new Button(this);
                btnDelete.setText("Delete");
                btnDelete.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E32B32")));
                btnDelete.setTextColor(Color.WHITE);
                btnDelete.setAllCaps(false);
                LinearLayout.LayoutParams bp3 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f);
                bp3.setMargins(marginPx, 0, 0, 0);
                btnDelete.setLayoutParams(bp3);
                btnDelete.setOnClickListener(v -> {
                    if (item.id != null) {
                        ttRef.child(item.id).removeValue()
                                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Time Table deleted from database", Toast.LENGTH_SHORT).show());
                    }
                });
                btnRow.addView(btnDelete);
            }

            inner.addView(btnRow);
            card.addView(inner);
            ttContainer.addView(card);
        }
    }

    private void showCreateDialog(TimeTableItem itemToEdit) {
        if (!userRole.equals("HOD")) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(itemToEdit == null ? "Create New Time Table" : "Edit Time Table");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        final EditText etSem = new EditText(this);
        etSem.setHint("Semester (e.g. I)");
        if (itemToEdit != null) etSem.setText(itemToEdit.semester);
        else etSem.setText("I");
        layout.addView(etSem);

        final EditText etAcYear = new EditText(this);
        etAcYear.setHint("Academic Year (e.g. 2026-27)");
        if (itemToEdit != null) etAcYear.setText(itemToEdit.academicYear);
        else etAcYear.setText("2026-27");
        layout.addView(etAcYear);

        TextView lblYear = new TextView(this);
        lblYear.setText("Year");
        lblYear.setPadding(0, 10, 0, 2);
        layout.addView(lblYear);
        final Spinner spinnerYear = new Spinner(this);
        String[] years = {"First Year", "Second Year", "Third Year", "Fourth Year"};
        spinnerYear.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, years));
        if (itemToEdit != null) {
            for (int i = 0; i < years.length; i++) {
                if (years[i].equalsIgnoreCase(itemToEdit.year)) {
                    spinnerYear.setSelection(i);
                    break;
                }
            }
        } else {
            spinnerYear.setSelection(2);
        }
        layout.addView(spinnerYear);

        TextView lblDiv = new TextView(this);
        lblDiv.setText("Division");
        lblDiv.setPadding(0, 10, 0, 2);
        layout.addView(lblDiv);
        final Spinner spinnerDiv = new Spinner(this);
        String[] divisions = new String[26];
        for (int i = 0; i < 26; i++) {
            divisions[i] = String.valueOf((char) ('A' + i));
        }
        spinnerDiv.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, divisions));
        if (itemToEdit != null) {
            for (int i = 0; i < divisions.length; i++) {
                if (divisions[i].equalsIgnoreCase(itemToEdit.division)) {
                    spinnerDiv.setSelection(i);
                    break;
                }
            }
        } else {
            spinnerDiv.setSelection(1);
        }
        layout.addView(spinnerDiv);

        builder.setView(layout);
        builder.setPositiveButton(itemToEdit == null ? "CREATE" : "SAVE", (dialog, which) -> {
            String sem = etSem.getText().toString().trim();
            String argAcYear = etAcYear.getText().toString().trim();
            String year = spinnerYear.getSelectedItem().toString();
            String div = spinnerDiv.getSelectedItem().toString();
            String title = year + " - Division " + div;

            String id = itemToEdit != null ? itemToEdit.id : ttRef.push().getKey();
            if (id == null) id = String.valueOf(System.currentTimeMillis());

            TimeTableItem newItem = new TimeTableItem(id, title, year, div, sem, argAcYear);
            ttRef.child(id).setValue(newItem)
                    .addOnSuccessListener(aVoid -> Toast.makeText(this, itemToEdit == null ? "Time Table Created Successfully!" : "Time Table Updated Successfully!", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });
        builder.setNegativeButton("CANCEL", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }
}
