package com.example.s_a;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CreateSessionActivity extends AppCompatActivity {

    private EditText etSubject, etDate, etStartTime, etEndTime, etRoom;
    private TextView txtDay, lblBatch;
    private Spinner spinnerFaculty, spinnerSessionType, spinnerYear, spinnerDivision, spinnerBatch;
    private final Calendar calendar = Calendar.getInstance();
    private DatabaseReference sessionsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_session);

        etSubject = findViewById(R.id.etSubject);
        etDate = findViewById(R.id.etDate);
        etStartTime = findViewById(R.id.etStartTime);
        etEndTime = findViewById(R.id.etEndTime);
        etRoom = findViewById(R.id.etRoom);
        txtDay = findViewById(R.id.txtDay);
        lblBatch = findViewById(R.id.lblBatch);
        spinnerFaculty = findViewById(R.id.spinnerFaculty);
        spinnerSessionType = findViewById(R.id.spinnerSessionType);
        spinnerYear = findViewById(R.id.spinnerYear);
        spinnerDivision = findViewById(R.id.spinnerDivision);
        spinnerBatch = findViewById(R.id.spinnerBatch);
        Button btnCreateSession = findViewById(R.id.btnCreateSession);

        sessionsRef = FirebaseDatabase.getInstance().getReference("sessions");

        // Load Faculty List from Firebase
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String loggedInUser = prefs.getString("LOGGED_IN_USER", "").trim();

        DatabaseReference facultyRef = FirebaseDatabase.getInstance().getReference("faculty");
        facultyRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> facultyNames = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String name = ds.child("name").getValue(String.class);
                    if (name != null && !name.trim().isEmpty() && !facultyNames.contains(name.trim())) {
                        facultyNames.add(name.trim());
                    }
                }
                if (facultyNames.isEmpty()) {
                    facultyNames.add("General Faculty");
                }
                spinnerFaculty.setAdapter(new ArrayAdapter<>(CreateSessionActivity.this, android.R.layout.simple_spinner_dropdown_item, facultyNames));

                if (!loggedInUser.isEmpty()) {
                    for (int i = 0; i < facultyNames.size(); i++) {
                        String fn = facultyNames.get(i);
                        if (fn.equalsIgnoreCase(loggedInUser) || fn.toLowerCase().contains(loggedInUser.toLowerCase()) || loggedInUser.toLowerCase().contains(fn.toLowerCase())) {
                            spinnerFaculty.setSelection(i);
                            break;
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        // Populate Spinners
        String[] sessionTypes = {"Lecture", "Practical", "Tutorial"};
        spinnerSessionType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sessionTypes));

        String[] years = {"First Year", "Second Year", "Third Year", "Fourth Year"};
        spinnerYear.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, years));

        String[] divisions = new String[26];
        for (int i = 0; i < 26; i++) {
            divisions[i] = String.valueOf((char) ('A' + i));
        }
        spinnerDivision.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, divisions));

        Runnable updateBatches = () -> {
            String selectedDiv = spinnerDivision.getSelectedItem() != null ? spinnerDivision.getSelectedItem().toString() : "A";
            String[] batches = new String[10];
            for (int b = 0; b < 10; b++) {
                batches[b] = selectedDiv + (b + 1);
            }
            spinnerBatch.setAdapter(new ArrayAdapter<>(CreateSessionActivity.this, android.R.layout.simple_spinner_dropdown_item, batches));
        };

        spinnerDivision.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateBatches.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerSessionType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String type = spinnerSessionType.getSelectedItem().toString();
                if (type.equalsIgnoreCase("Practical")) {
                    lblBatch.setVisibility(View.VISIBLE);
                    spinnerBatch.setVisibility(View.VISIBLE);
                    updateBatches.run();
                } else {
                    lblBatch.setVisibility(View.GONE);
                    spinnerBatch.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Date Picker
        etDate.setOnClickListener(v -> {
            DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, month);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                etDate.setText(sdf.format(calendar.getTime()));

                SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
                txtDay.setText("Day: " + dayFormat.format(calendar.getTime()));
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
            dialog.show();
        });

        // Start Time Picker
        etStartTime.setOnClickListener(v -> {
            TimePickerDialog dialog = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                cal.set(Calendar.MINUTE, minute);
                SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                etStartTime.setText(sdf.format(cal.getTime()));
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false);
            dialog.show();
        });

        // End Time Picker
        etEndTime.setOnClickListener(v -> {
            TimePickerDialog dialog = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                cal.set(Calendar.MINUTE, minute);
                SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                etEndTime.setText(sdf.format(cal.getTime()));
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false);
            dialog.show();
        });

        btnCreateSession.setOnClickListener(v -> {
            String subject = etSubject.getText().toString().trim();
            String faculty = spinnerFaculty.getSelectedItem() != null ? spinnerFaculty.getSelectedItem().toString() : "";
            String sessionType = spinnerSessionType.getSelectedItem().toString();
            String year = spinnerYear.getSelectedItem().toString();
            String division = spinnerDivision.getSelectedItem().toString();
            String batch = (spinnerBatch.getVisibility() == View.VISIBLE && spinnerBatch.getSelectedItem() != null) ? spinnerBatch.getSelectedItem().toString() : "";
            String date = etDate.getText().toString().trim();
            String startTime = etStartTime.getText().toString().trim();
            String endTime = etEndTime.getText().toString().trim();
            String room = etRoom.getText().toString().trim();

            if (subject.isEmpty()) {
                Toast.makeText(this, "Please enter subject", Toast.LENGTH_SHORT).show();
                return;
            }

            String sessionId = sessionsRef.push().getKey();
            if (sessionId == null) sessionId = String.valueOf(System.currentTimeMillis());

            Map<String, Object> sessionMap = new HashMap<>();
            sessionMap.put("id", sessionId);
            sessionMap.put("subject", subject);
            sessionMap.put("faculty", faculty);
            sessionMap.put("sessionType", sessionType);
            sessionMap.put("year", year);
            sessionMap.put("division", division);
            sessionMap.put("batch", batch);
            sessionMap.put("date", date);
            sessionMap.put("startTime", startTime);
            sessionMap.put("endTime", endTime);
            sessionMap.put("room", room);

            sessionsRef.child(sessionId).setValue(sessionMap)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Session Created & Saved to Firebase!", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });
    }
}
