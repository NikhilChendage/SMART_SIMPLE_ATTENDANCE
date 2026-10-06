package com.example.s_a;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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

public class FacultyManagementActivity extends AppCompatActivity {

    public static class Faculty {
        public String id;
        public String name;
        public String subject;
        public String email;
        public String phone;
        public String designation;
        public String password;

        public Faculty() {} // Required for Firebase

        public Faculty(String id, String name, String subject, String email, String phone, String designation, String password) {
            this.id = id;
            this.name = name;
            this.subject = subject;
            this.email = email;
            this.phone = phone;
            this.designation = designation;
            this.password = password;
        }
    }

    private final List<Faculty> facultyList = new ArrayList<>();
    private LinearLayout facultyContainer;
    private DatabaseReference facultyRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_faculty_management);

        facultyContainer = findViewById(R.id.facultyContainer);
        Button btnAddFaculty = findViewById(R.id.btnAddFaculty);

        facultyRef = FirebaseDatabase.getInstance().getReference("faculty");

        btnAddFaculty.setOnClickListener(v -> showAddOrEditDialog(null));

        loadFacultyFromFirebase();
    }

    private void loadFacultyFromFirebase() {
        facultyRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                facultyList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Faculty faculty = ds.getValue(Faculty.class);
                    if (faculty != null) {
                        if (faculty.id == null) faculty.id = ds.getKey();
                        facultyList.add(faculty);
                    }
                }
                refreshFacultyCards();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(FacultyManagementActivity.this, "Failed to load faculty: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshFacultyCards() {
        int childCount = facultyContainer.getChildCount();
        if (childCount > 1) {
            facultyContainer.removeViews(1, childCount - 1);
        }

        for (int i = 0; i < facultyList.size(); i++) {
            Faculty faculty = facultyList.get(i);

            CardView card = new CardView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 16);
            card.setLayoutParams(params);
            card.setRadius(18 * getResources().getDisplayMetrics().density);
            card.setCardElevation(3 * getResources().getDisplayMetrics().density);
            card.setCardBackgroundColor(Color.WHITE);

            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (18 * getResources().getDisplayMetrics().density);
            inner.setPadding(pad, pad, pad, pad);

            // Header row with Avatar + Name & Designation
            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            topRow.setPadding(0, 0, 0, 12);

            TextView avatar = new TextView(this);
            String firstLetter = (faculty.name != null && !faculty.name.trim().isEmpty()) ? faculty.name.trim().substring(0, 1).toUpperCase() : "F";
            avatar.setText(firstLetter);
            avatar.setTextSize(18);
            avatar.setTypeface(null, Typeface.BOLD);
            avatar.setTextColor(Color.parseColor("#2563EB"));
            avatar.setGravity(Gravity.CENTER);
            int avatarSize = (int) (46 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(avatarSize, avatarSize);
            avatarParams.setMarginEnd((int) (12 * getResources().getDisplayMetrics().density));
            avatar.setLayoutParams(avatarParams);
            avatar.setBackgroundResource(R.drawable.ic_launcher_background);
            avatar.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EEF2FF")));
            topRow.addView(avatar);

            LinearLayout nameCol = new LinearLayout(this);
            nameCol.setOrientation(LinearLayout.VERTICAL);
            nameCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView txtName = new TextView(this);
            txtName.setText(faculty.name);
            txtName.setTextSize(18);
            txtName.setTypeface(null, Typeface.BOLD);
            txtName.setTextColor(Color.parseColor("#0F172A"));
            nameCol.addView(txtName);

            TextView txtDesig = new TextView(this);
            txtDesig.setText(faculty.designation != null ? faculty.designation : "Faculty");
            txtDesig.setTextSize(13);
            txtDesig.setTextColor(Color.parseColor("#2563EB"));
            txtDesig.setTypeface(null, Typeface.BOLD);
            nameCol.addView(txtDesig);

            topRow.addView(nameCol);
            inner.addView(topRow);

            TextView txtSub = new TextView(this);
            txtSub.setText("Subjects: " + (faculty.subject != null ? faculty.subject : "N/A"));
            txtSub.setTextSize(14);
            txtSub.setTypeface(null, Typeface.BOLD);
            txtSub.setTextColor(Color.parseColor("#059669"));
            txtSub.setPadding(0, 0, 0, 4);
            inner.addView(txtSub);

            TextView txtEmail = new TextView(this);
            txtEmail.setText("Email: " + (faculty.email != null ? faculty.email : "N/A"));
            txtEmail.setTextSize(13);
            txtEmail.setTextColor(Color.parseColor("#64748B"));
            txtEmail.setPadding(0, 0, 0, 2);
            inner.addView(txtEmail);

            TextView txtPhone = new TextView(this);
            txtPhone.setText("Phone: " + (faculty.phone != null ? faculty.phone : "N/A") + " | Pass: " + (faculty.password != null ? faculty.password : ""));
            txtPhone.setTextSize(13);
            txtPhone.setTextColor(Color.parseColor("#64748B"));
            txtPhone.setPadding(0, 0, 0, 16);
            inner.addView(txtPhone);

            // Action Buttons
            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setWeightSum(2);

            Button btnEdit = new Button(this);
            btnEdit.setText("Edit");
            btnEdit.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#2563EB")));
            btnEdit.setTextColor(Color.WHITE);
            btnEdit.setAllCaps(false);
            LinearLayout.LayoutParams bp1 = new LinearLayout.LayoutParams(0, (int)(44 * getResources().getDisplayMetrics().density), 1f);
            bp1.setMarginEnd(6);
            btnEdit.setLayoutParams(bp1);
            btnEdit.setOnClickListener(v -> showAddOrEditDialog(faculty));
            btnRow.addView(btnEdit);

            Button btnDelete = new Button(this);
            btnDelete.setText("Delete");
            btnDelete.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#DC2626")));
            btnDelete.setTextColor(Color.WHITE);
            btnDelete.setAllCaps(false);
            LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(0, (int)(44 * getResources().getDisplayMetrics().density), 1f);
            bp2.setMarginStart(6);
            btnDelete.setLayoutParams(bp2);
            btnDelete.setOnClickListener(v -> {
                if (faculty.id != null) {
                    facultyRef.child(faculty.id).removeValue()
                            .addOnSuccessListener(aVoid -> Toast.makeText(this, "Faculty deleted", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });
            btnRow.addView(btnDelete);

            inner.addView(btnRow);
            card.addView(inner);
            facultyContainer.addView(card);
        }
    }

    private void showAddOrEditDialog(Faculty facultyToEdit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(facultyToEdit == null ? "Add Faculty" : "Edit Faculty");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        final EditText etName = new EditText(this);
        etName.setHint("Faculty Name");
        if (facultyToEdit != null) etName.setText(facultyToEdit.name);
        layout.addView(etName);

        final EditText etSub = new EditText(this);
        etSub.setHint("Assigned Subject(s) (e.g. JAVA, ML, DAA)");
        if (facultyToEdit != null) etSub.setText(facultyToEdit.subject);
        layout.addView(etSub);

        final EditText etEmail = new EditText(this);
        etEmail.setHint("Email");
        if (facultyToEdit != null) etEmail.setText(facultyToEdit.email);
        layout.addView(etEmail);

        final EditText etPhone = new EditText(this);
        etPhone.setHint("Phone Number (Login ID)");
        if (facultyToEdit != null) etPhone.setText(facultyToEdit.phone);
        layout.addView(etPhone);

        final EditText etPass = new EditText(this);
        etPass.setHint("Assigned Password");
        if (facultyToEdit != null) etPass.setText(facultyToEdit.password);
        else etPass.setText("12345");
        layout.addView(etPass);

        final EditText etDesig = new EditText(this);
        etDesig.setHint("Designation");
        if (facultyToEdit != null) etDesig.setText(facultyToEdit.designation);
        else etDesig.setText("Assistant Professor");
        layout.addView(etDesig);

        builder.setView(layout);
        builder.setPositiveButton(facultyToEdit == null ? "ADD" : "SAVE", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String sub = etSub.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String pass = etPass.getText().toString().trim();
            String desig = etDesig.getText().toString().trim();

            if (name.isEmpty() || phone.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Name, Phone and Password are required", Toast.LENGTH_SHORT).show();
                return;
            }

            String id = facultyToEdit != null ? facultyToEdit.id : facultyRef.push().getKey();
            if (id == null) id = String.valueOf(System.currentTimeMillis());

            Faculty faculty = new Faculty(id, name, sub, email, phone, desig, pass);
            facultyRef.child(id).setValue(faculty)
                    .addOnSuccessListener(aVoid -> Toast.makeText(this, facultyToEdit == null ? "Faculty added successfully" : "Faculty updated successfully", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });
        builder.setNegativeButton("CANCEL", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }
}
