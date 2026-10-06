package com.example.s_a;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SubjectAnalyticsActivity extends AppCompatActivity {

    public static class SubStudentStat {
        public String id;
        public String name;
        public String rollNo;
        public String gnr;
        public String year;
        public String division;
        public String batch;

        public int conducted = 0;
        public int present = 0;

        public SubStudentStat(String id, String name, String rollNo, String gnr, String year, String division, String batch) {
            this.id = id;
            this.name = name;
            this.rollNo = rollNo;
            this.gnr = gnr;
            this.year = year;
            this.division = division;
            this.batch = batch;
        }

        public int getPercent() {
            if (conducted == 0) return 0;
            return Math.round((float) present * 100f / conducted);
        }
    }

    private Spinner spinnerYear, spinnerDiv, spinnerSubject, spinnerBatch;
    private EditText etCustomCriteriaThreshold;
    private TextView txtSubjectAvgPercent, txtSubjectSessionsStat, txtSubjectDefaultersStat, lblDefaultersStat;
    private LinearLayout subjectStudentsContainer;

    private DatabaseReference studentsRef, attendanceRef, sessionsRef;
    private final List<SubStudentStat> studentList = new ArrayList<>();
    private final List<String> availableSubjects = new ArrayList<>();

    private String userRole = "Teacher";
    private String loggedInTeacherName = "";
    private String loggedInTeacherSubject = "";

    private String selectedYear = "Third Year";
    private String selectedDiv = "B";
    private String selectedSubject = "All Subjects";
    private String selectedBatch = "All Batches";
    private int criteriaThreshold = 75; // Default 75%, user editable

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_subject_analytics);

        if (getIntent().hasExtra("SELECTED_SUBJECT")) {
            selectedSubject = getIntent().getStringExtra("SELECTED_SUBJECT");
        }

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        userRole = prefs.getString("USER_ROLE", "Teacher");
        loggedInTeacherName = prefs.getString("LOGGED_IN_USER", "").trim();

        spinnerYear = findViewById(R.id.spinnerSubjectYear);
        spinnerDiv = findViewById(R.id.spinnerSubjectDiv);
        spinnerSubject = findViewById(R.id.spinnerSubjectName);
        spinnerBatch = findViewById(R.id.spinnerSubjectBatch);
        etCustomCriteriaThreshold = findViewById(R.id.etCustomCriteriaThreshold);

        txtSubjectAvgPercent = findViewById(R.id.txtSubjectAvgPercent);
        txtSubjectSessionsStat = findViewById(R.id.txtSubjectSessionsStat);
        txtSubjectDefaultersStat = findViewById(R.id.txtSubjectDefaultersStat);
        lblDefaultersStat = findViewById(R.id.lblDefaultersStat);

        subjectStudentsContainer = findViewById(R.id.subjectStudentsContainer);

        Button btnExportExcel = findViewById(R.id.btnExportSubjectExcel);
        Button btnExportPdf = findViewById(R.id.btnExportSubjectPdf);
        Button btnShareWhatsapp = findViewById(R.id.btnShareSubjectWhatsapp);

        studentsRef = FirebaseDatabase.getInstance().getReference("students");
        attendanceRef = FirebaseDatabase.getInstance().getReference("attendance");
        sessionsRef = FirebaseDatabase.getInstance().getReference("sessions");

        setupSpinners();
        setupThresholdInput();

        if (btnExportExcel != null) btnExportExcel.setOnClickListener(v -> generateSubjectExcelReport());
        btnExportPdf.setOnClickListener(v -> generateSubjectPdf());
        btnShareWhatsapp.setOnClickListener(v -> shareWhatsappSubjectReport());
    }

    private void setupThresholdInput() {
        if (etCustomCriteriaThreshold != null) {
            etCustomCriteriaThreshold.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String text = s.toString().trim();
                    if (!text.isEmpty()) {
                        try {
                            criteriaThreshold = Integer.parseInt(text);
                        } catch (Exception e) {
                            criteriaThreshold = 75;
                        }
                    } else {
                        criteriaThreshold = 75;
                    }

                    if (lblDefaultersStat != null) {
                        lblDefaultersStat.setText("Low (<" + criteriaThreshold + "%)");
                    }
                    renderSubjectCards(0);
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void setupSpinners() {
        String[] years = {"First Year", "Second Year", "Third Year", "Fourth Year"};
        spinnerYear.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, years));
        spinnerYear.setSelection(2); // Third Year default

        String[] divs = {"A", "B", "C", "D"};
        spinnerDiv.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, divs));
        spinnerDiv.setSelection(1); // Div B default

        spinnerDiv.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedDiv = spinnerDiv.getSelectedItem().toString();
                updateBatchesForSelectedDivision();
                loadSubjectData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        AdapterView.OnItemSelectedListener filterListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedYear = spinnerYear.getSelectedItem().toString();
                selectedDiv = spinnerDiv.getSelectedItem() != null ? spinnerDiv.getSelectedItem().toString() : "B";
                selectedSubject = spinnerSubject.getSelectedItem() != null ? spinnerSubject.getSelectedItem().toString() : "All Subjects";
                selectedBatch = spinnerBatch.getSelectedItem() != null ? spinnerBatch.getSelectedItem().toString() : "All Batches";

                loadSubjectData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        spinnerYear.setOnItemSelectedListener(filterListener);
        spinnerSubject.setOnItemSelectedListener(filterListener);
        spinnerBatch.setOnItemSelectedListener(filterListener);

        updateBatchesForSelectedDivision();
        loadDynamicSubjectsAndBatches();
    }

    private void updateBatchesForSelectedDivision() {
        String div = spinnerDiv.getSelectedItem() != null ? spinnerDiv.getSelectedItem().toString() : "B";
        List<String> divBatches = new ArrayList<>();
        divBatches.add("All Batches");
        divBatches.add("Lecture");

        for (int b = 1; b <= 10; b++) {
            divBatches.add(div + b);
        }

        spinnerBatch.setAdapter(new ArrayAdapter<>(SubjectAnalyticsActivity.this, android.R.layout.simple_spinner_dropdown_item, divBatches));
        selectedBatch = "All Batches";
    }

    private void loadDynamicSubjectsAndBatches() {
        DatabaseReference facultyRef = FirebaseDatabase.getInstance().getReference("faculty");
        facultyRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String name = ds.child("name").getValue(String.class);
                    String sub = ds.child("subject").getValue(String.class);
                    if (name != null && !loggedInTeacherName.isEmpty() && (name.equalsIgnoreCase(loggedInTeacherName) || name.toLowerCase().contains(loggedInTeacherName.toLowerCase()))) {
                        if (sub != null && !sub.trim().isEmpty()) {
                            loggedInTeacherSubject = sub.trim();
                        }
                        break;
                    }
                }

                sessionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        availableSubjects.clear();
                        availableSubjects.add("All Subjects");

                        for (DataSnapshot ds : snapshot.getChildren()) {
                            String sub = ds.child("subject").getValue(String.class);
                            String faculty = ds.child("faculty").getValue(String.class);

                            boolean isForTeacher = userRole.equalsIgnoreCase("HOD") ||
                                    (loggedInTeacherSubject != null && sub != null && loggedInTeacherSubject.toLowerCase().contains(sub.toLowerCase())) ||
                                    (faculty != null && !loggedInTeacherName.isEmpty() && faculty.toLowerCase().contains(loggedInTeacherName.toLowerCase()));

                            if (isForTeacher && sub != null && !sub.trim().isEmpty() && !availableSubjects.contains(sub.trim())) {
                                availableSubjects.add(sub.trim());
                            }
                        }

                        spinnerSubject.setAdapter(new ArrayAdapter<>(SubjectAnalyticsActivity.this, android.R.layout.simple_spinner_dropdown_item, availableSubjects));
                        if (selectedSubject != null && !selectedSubject.equals("All Subjects")) {
                            for (int i = 0; i < availableSubjects.size(); i++) {
                                if (availableSubjects.get(i).equalsIgnoreCase(selectedSubject) || availableSubjects.get(i).toLowerCase().contains(selectedSubject.toLowerCase())) {
                                    spinnerSubject.setSelection(i);
                                    break;
                                }
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadSubjectData() {
        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot studentSnapshot) {
                studentList.clear();

                for (DataSnapshot ds : studentSnapshot.getChildren()) {
                    String name = ds.child("name").getValue(String.class);
                    String rollNo = ds.child("rollNo").getValue(String.class);
                    String gnr = ds.child("gnr").getValue(String.class);
                    String yr = ds.child("year").getValue(String.class);
                    String div = ds.child("division").getValue(String.class);
                    String batch = ds.child("batch").getValue(String.class);
                    String id = ds.getKey();

                    // Shortlist Student List Batch-wise!
                    boolean matchesBatch = selectedBatch.equals("All Batches") || selectedBatch.equals("Lecture") ||
                            (batch != null && (batch.equalsIgnoreCase(selectedBatch) || batch.toLowerCase().contains(selectedBatch.toLowerCase())));

                    if (yr != null && yr.equalsIgnoreCase(selectedYear) && div != null && div.equalsIgnoreCase(selectedDiv) && matchesBatch) {
                        studentList.add(new SubStudentStat(id, name != null ? name : "Student", rollNo != null ? rollNo : "1", gnr != null ? gnr : "GNR000", yr, div, batch != null ? batch : "B1"));
                    }
                }

                Collections.sort(studentList, (s1, s2) -> {
                    try {
                        int r1 = Integer.parseInt(s1.rollNo.replaceAll("[^0-9]", ""));
                        int r2 = Integer.parseInt(s2.rollNo.replaceAll("[^0-9]", ""));
                        return Integer.compare(r1, r2);
                    } catch (Exception e) {
                        return s1.rollNo.compareToIgnoreCase(s2.rollNo);
                    }
                });

                // Compute Attendance Accurately
                attendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot attSnapshot) {
                        int totalConductedSessions = 0;

                        for (DataSnapshot attRecord : attSnapshot.getChildren()) {
                            String sub = attRecord.child("subject").getValue(String.class);
                            String div = attRecord.child("division").getValue(String.class);
                            String yr = attRecord.child("year").getValue(String.class);
                            String batch = attRecord.child("batch").getValue(String.class);

                            boolean matchDiv = div == null || div.trim().isEmpty() || div.equalsIgnoreCase(selectedDiv);
                            boolean matchYear = yr == null || yr.trim().isEmpty() || yr.equalsIgnoreCase(selectedYear);

                            if (matchDiv && matchYear) {
                                boolean matchSubject = selectedSubject.equals("All Subjects") ||
                                        (sub != null && (sub.equalsIgnoreCase(selectedSubject) || sub.toLowerCase().contains(selectedSubject.toLowerCase()) || selectedSubject.toLowerCase().contains(sub.toLowerCase())));

                                boolean matchBatch = selectedBatch.equals("All Batches") ||
                                        (selectedBatch.equals("Lecture") && (batch == null || batch.trim().isEmpty())) ||
                                        (batch != null && (batch.equalsIgnoreCase(selectedBatch) || batch.toLowerCase().contains(selectedBatch.toLowerCase())));

                                if (matchSubject && matchBatch) {
                                    DataSnapshot recordsSnap = attRecord.child("records");
                                    if (recordsSnap.exists() && recordsSnap.getChildrenCount() > 0) {
                                        totalConductedSessions++;

                                        for (SubStudentStat st : studentList) {
                                            if (recordsSnap.hasChild(st.rollNo)) {
                                                DataSnapshot stSnap = recordsSnap.child(st.rollNo);
                                                Integer status = stSnap.child("status").getValue(Integer.class);
                                                if (status != null && (status == 1 || status == 2)) {
                                                    st.conducted++;
                                                    if (status == 1) st.present++;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        renderSubjectCards(totalConductedSessions);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        renderSubjectCards(0);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(SubjectAnalyticsActivity.this, "Failed to load students: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderSubjectCards(int totalSessions) {
        subjectStudentsContainer.removeAllViews();

        int totalStudents = studentList.size();
        int sumPercent = 0, defaulters = 0;

        for (SubStudentStat st : studentList) {
            int pct = st.getPercent();
            sumPercent += pct;
            if (pct < criteriaThreshold) defaulters++;
        }

        int avgPercent = totalStudents > 0 ? Math.round((float) sumPercent / totalStudents) : 0;

        txtSubjectAvgPercent.setText(avgPercent + "%");
        if (totalSessions > 0) {
            txtSubjectSessionsStat.setText(totalSessions + " Sessions");
        }
        txtSubjectDefaultersStat.setText(defaulters + " Low");

        if (studentList.isEmpty()) {
            TextView txtEmpty = new TextView(this);
            txtEmpty.setText("No student records found for " + selectedYear + " - Div " + selectedDiv + " (" + selectedBatch + ").");
            txtEmpty.setTextColor(Color.parseColor("#64748B"));
            txtEmpty.setTextSize(14);
            txtEmpty.setPadding(16, 24, 16, 24);
            subjectStudentsContainer.addView(txtEmpty);
            return;
        }

        for (SubStudentStat st : studentList) {
            int pct = st.getPercent();

            CardView card = new CardView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 12);
            card.setLayoutParams(params);
            card.setRadius(16 * getResources().getDisplayMetrics().density);
            card.setCardElevation(3 * getResources().getDisplayMetrics().density);
            card.setCardBackgroundColor(Color.WHITE);

            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.HORIZONTAL);
            inner.setGravity(Gravity.CENTER_VERTICAL);
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            inner.setPadding(pad, pad, pad, pad);

            // Roll Badge
            TextView rollBadge = new TextView(this);
            rollBadge.setText(st.rollNo);
            rollBadge.setTextSize(14);
            rollBadge.setTypeface(null, Typeface.BOLD);
            rollBadge.setTextColor(Color.parseColor("#2563EB"));
            rollBadge.setGravity(Gravity.CENTER);
            rollBadge.setBackgroundResource(R.drawable.bg_badge_roll);
            int badgeWidth = (int) (48 * getResources().getDisplayMetrics().density);
            int badgeHeight = (int) (36 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams rollParams = new LinearLayout.LayoutParams(badgeWidth, badgeHeight);
            rollParams.setMarginEnd((int) (12 * getResources().getDisplayMetrics().density));
            rollBadge.setLayoutParams(rollParams);
            inner.addView(rollBadge);

            // Text Info
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView txtName = new TextView(this);
            txtName.setText(st.name);
            txtName.setTextSize(15);
            txtName.setTypeface(null, Typeface.BOLD);
            txtName.setTextColor(Color.parseColor("#0F172A"));
            col.addView(txtName);

            TextView txtSubInfo = new TextView(this);
            txtSubInfo.setText(selectedSubject + ": " + st.present + " / " + st.conducted + " Sessions | Batch: " + st.batch);
            txtSubInfo.setTextSize(12);
            txtSubInfo.setTextColor(Color.parseColor("#64748B"));
            col.addView(txtSubInfo);

            inner.addView(col);

            // Percentage Badge with Criteria Threshold Evaluation
            TextView pctBadge = new TextView(this);
            boolean isEligible = pct >= criteriaThreshold;
            pctBadge.setText(pct + "%\n" + (isEligible ? "Eligible" : "Low (<" + criteriaThreshold + "%)"));
            pctBadge.setTextSize(11);
            pctBadge.setTypeface(null, Typeface.BOLD);
            pctBadge.setGravity(Gravity.CENTER);

            if (isEligible) {
                pctBadge.setTextColor(Color.parseColor("#059669"));
                pctBadge.setBackgroundColor(Color.parseColor("#ECFDF5"));
            } else {
                pctBadge.setTextColor(Color.parseColor("#DC2626"));
                pctBadge.setBackgroundColor(Color.parseColor("#FEF2F2"));
            }
            pctBadge.setPadding(12, 6, 12, 6);
            inner.addView(pctBadge);

            card.addView(inner);
            subjectStudentsContainer.addView(card);
        }
    }

    private void generateSubjectExcelReport() {
        try {
            Calendar now = Calendar.getInstance();
            String dateStr = new SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).format(now.getTime());

            int totalStudents = studentList.size();
            int sumPercent = 0, defaulters = 0;
            for (SubStudentStat st : studentList) {
                int pct = st.getPercent();
                sumPercent += pct;
                if (pct < criteriaThreshold) defaulters++;
            }
            int avgPercent = totalStudents > 0 ? Math.round((float) sumPercent / totalStudents) : 0;

            StringBuilder csv = new StringBuilder();
            csv.append("SUBJECT ATTENDANCE REPORT - TKIET CSBS\n");
            csv.append("Subject:,").append(selectedSubject).append(",Academic Year:,").append(selectedYear).append(",Division:,").append(selectedDiv).append("\n");
            csv.append("Batch:,").append(selectedBatch).append(",Criteria Threshold:,").append(criteriaThreshold).append("%,Report Date:,").append(dateStr).append("\n");
            csv.append("Subject Avg:,").append(avgPercent).append("%,Total Students:,").append(totalStudents).append(",Low Attendance (<").append(criteriaThreshold).append("%):,").append(defaulters).append("\n\n");

            csv.append("Roll Number,Student Name,GNR Number,Attended Sessions,Total Sessions,Percentage (%),Eligibility Status\n");

            for (SubStudentStat st : studentList) {
                int pct = st.getPercent();
                String status = pct >= criteriaThreshold ? "Eligible" : "Low (<" + criteriaThreshold + "%)";
                csv.append(st.rollNo).append(",")
                   .append("\"").append(st.name).append("\",")
                   .append(st.gnr).append(",")
                   .append(st.present).append(",")
                   .append(st.conducted).append(",")
                   .append(pct).append("%,\n")
                   .append(status).append("\n");
            }

            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "ExcelSubjectReports");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "Subject_Report_" + selectedSubject.replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.currentTimeMillis() + ".csv");

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(csv.toString().getBytes("UTF-8"));
            fos.close();

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Export Subject Excel Report (.csv)"));
            Toast.makeText(this, "Subject Excel Report (.csv) Exported Successfully!", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Error exporting Excel report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void generateSubjectPdf() {
        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();

        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();

        Calendar now = Calendar.getInstance();
        String dateStr = new SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()).format(now.getTime());

        paint.setColor(Color.BLACK);
        paint.setTextSize(18);
        paint.setFakeBoldText(true);
        canvas.drawText("SUBJECT ATTENDANCE REPORT - TKIET CSBS", 40, 45, paint);

        paint.setTextSize(12);
        paint.setFakeBoldText(false);
        canvas.drawText("Subject: " + selectedSubject + "  |  Year: " + selectedYear + "  |  Div: " + selectedDiv, 40, 75, paint);
        canvas.drawText("Filter Batch: " + selectedBatch + "  |  Threshold: " + criteriaThreshold + "%  |  Date: " + dateStr, 40, 95, paint);

        int totalStudents = studentList.size();
        int sumPercent = 0, defaulters = 0;
        for (SubStudentStat st : studentList) {
            int pct = st.getPercent();
            sumPercent += pct;
            if (pct < criteriaThreshold) defaulters++;
        }
        int avgPercent = totalStudents > 0 ? Math.round((float) sumPercent / totalStudents) : 0;

        canvas.drawText("Subject Avg: " + avgPercent + "%  |  Total Students: " + totalStudents + "  |  Low Attendance (<" + criteriaThreshold + "%): " + defaulters, 40, 120, paint);

        paint.setFakeBoldText(true);
        canvas.drawText("Roll No", 40, 150, paint);
        canvas.drawText("Student Name", 110, 150, paint);
        canvas.drawText("Attended", 380, 150, paint);
        canvas.drawText("Percentage", 480, 150, paint);

        paint.setStrokeWidth(1);
        canvas.drawLine(40, 160, 555, 160, paint);

        paint.setFakeBoldText(false);
        int y = 180;

        for (SubStudentStat st : studentList) {
            if (y > 800) {
                document.finishPage(page);
                page = document.startPage(pageInfo);
                canvas = page.getCanvas();
                y = 50;
            }

            canvas.drawText(st.rollNo, 40, y, paint);
            canvas.drawText(st.name, 110, y, paint);
            canvas.drawText(st.present + " / " + st.conducted, 380, y, paint);
            canvas.drawText(st.getPercent() + "%", 480, y, paint);

            y += 22;
        }

        document.finishPage(page);

        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "SubjectReports");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "SubjectReport_" + System.currentTimeMillis() + ".pdf");

        try {
            document.writeTo(new FileOutputStream(file));
            document.close();

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share Subject PDF Report"));
            Toast.makeText(this, "Subject PDF Report Generated!", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Error generating PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareWhatsappSubjectReport() {
        Calendar now = Calendar.getInstance();
        String dateStr = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(now.getTime());

        int totalStudents = studentList.size();
        int sumPercent = 0, defaulters = 0;
        for (SubStudentStat st : studentList) {
            int pct = st.getPercent();
            sumPercent += pct;
            if (pct < criteriaThreshold) defaulters++;
        }
        int avgPercent = totalStudents > 0 ? Math.round((float) sumPercent / totalStudents) : 0;

        StringBuilder msg = new StringBuilder();
        msg.append("TKIET CSBS - SUBJECT REPORT\n");
        msg.append("Date: ").append(dateStr).append("\n");
        msg.append("Subject: ").append(selectedSubject).append(" | Batch: ").append(selectedBatch).append("\n");
        msg.append("Year: ").append(selectedYear).append(" | Div: ").append(selectedDiv).append("\n");
        msg.append("Criteria Threshold: ").append(criteriaThreshold).append("%\n\n");
        msg.append("Subject Avg: ").append(avgPercent).append("% | Defaulters (<").append(criteriaThreshold).append("%): ").append(defaulters).append("\n\n");
        msg.append("STUDENT PERCENTAGE LIST:\n");

        for (SubStudentStat st : studentList) {
            msg.append("Roll ").append(st.rollNo).append(" ").append(st.name).append(": ").append(st.getPercent()).append("% (").append(st.present).append("/").append(st.conducted).append(")\n");
        }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, msg.toString());
        startActivity(Intent.createChooser(intent, "Share Subject Report via WhatsApp"));
    }
}
