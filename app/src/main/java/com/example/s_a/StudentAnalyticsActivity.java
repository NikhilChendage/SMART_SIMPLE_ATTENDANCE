package com.example.s_a;

import android.content.Intent;
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

public class StudentAnalyticsActivity extends AppCompatActivity {

    public static class FullStudentStat {
        public String id;
        public String name;
        public String rollNo;
        public String gnr;
        public String year;
        public String division;
        public String batch;

        public int totalConducted = 0;
        public int totalPresent = 0;

        // Subject -> [conducted, present]
        public Map<String, int[]> subjectStats = new HashMap<>();

        public FullStudentStat(String id, String name, String rollNo, String gnr, String year, String division, String batch) {
            this.id = id;
            this.name = name;
            this.rollNo = rollNo;
            this.gnr = gnr;
            this.year = year;
            this.division = division;
            this.batch = batch;
        }

        public int getPercent() {
            if (totalConducted == 0) return 0;
            return Math.round((float) totalPresent * 100f / totalConducted);
        }
    }

    private Spinner spinnerYear, spinnerDiv;
    private EditText etSearchStudentAnalytics, etCustomStudentCriteriaThreshold;
    private LinearLayout studentAnalyticsContainer;

    private DatabaseReference studentsRef, attendanceRef;
    private final List<FullStudentStat> studentList = new ArrayList<>();

    private String selectedYear = "Third Year";
    private String selectedDiv = "B";
    private String searchQuery = "";
    private int criteriaThreshold = 75; // Default 75%, user editable

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_student_analytics);

        spinnerYear = findViewById(R.id.spinnerStudentYear);
        spinnerDiv = findViewById(R.id.spinnerStudentDiv);
        etCustomStudentCriteriaThreshold = findViewById(R.id.etCustomStudentCriteriaThreshold);
        etSearchStudentAnalytics = findViewById(R.id.etSearchStudentAnalytics);

        studentAnalyticsContainer = findViewById(R.id.studentAnalyticsContainer);

        studentsRef = FirebaseDatabase.getInstance().getReference("students");
        attendanceRef = FirebaseDatabase.getInstance().getReference("attendance");

        setupSpinners();
        setupThresholdInput();
        setupSearch();
    }

    private void setupThresholdInput() {
        if (etCustomStudentCriteriaThreshold != null) {
            etCustomStudentCriteriaThreshold.addTextChangedListener(new TextWatcher() {
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
                    renderStudentCards();
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

        AdapterView.OnItemSelectedListener spinnerListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedYear = spinnerYear.getSelectedItem().toString();
                selectedDiv = spinnerDiv.getSelectedItem().toString();

                loadStudentAnalyticsData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        spinnerYear.setOnItemSelectedListener(spinnerListener);
        spinnerDiv.setOnItemSelectedListener(spinnerListener);
    }

    private void setupSearch() {
        if (etSearchStudentAnalytics != null) {
            etSearchStudentAnalytics.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    searchQuery = s.toString().trim().toLowerCase();
                    renderStudentCards();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void loadStudentAnalyticsData() {
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

                    if (yr != null && yr.equalsIgnoreCase(selectedYear) && div != null && div.equalsIgnoreCase(selectedDiv)) {
                        studentList.add(new FullStudentStat(id, name != null ? name : "Student", rollNo != null ? rollNo : "1", gnr != null ? gnr : "GNR000", yr, div, batch != null ? batch : "B1"));
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
                        for (DataSnapshot attRecord : attSnapshot.getChildren()) {
                            String sub = attRecord.child("subject").getValue(String.class);
                            String div = attRecord.child("division").getValue(String.class);
                            String yr = attRecord.child("year").getValue(String.class);

                            boolean matchDiv = div == null || div.trim().isEmpty() || div.equalsIgnoreCase(selectedDiv);
                            boolean matchYear = yr == null || yr.trim().isEmpty() || yr.equalsIgnoreCase(selectedYear);

                            if (matchDiv && matchYear && sub != null) {
                                DataSnapshot recordsSnap = attRecord.child("records");
                                if (recordsSnap.exists() && recordsSnap.getChildrenCount() > 0) {
                                    for (FullStudentStat st : studentList) {
                                        if (recordsSnap.hasChild(st.rollNo)) {
                                            DataSnapshot stSnap = recordsSnap.child(st.rollNo);
                                            Integer status = stSnap.child("status").getValue(Integer.class);
                                            if (status != null && (status == 1 || status == 2)) {
                                                st.totalConducted++;
                                                if (status == 1) st.totalPresent++;

                                                if (!st.subjectStats.containsKey(sub)) {
                                                    st.subjectStats.put(sub, new int[]{0, 0});
                                                }
                                                int[] arr = st.subjectStats.get(sub);
                                                if (arr != null) {
                                                    arr[0]++;
                                                    if (status == 1) arr[1]++;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        renderStudentCards();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        renderStudentCards();
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(StudentAnalyticsActivity.this, "Failed to load students: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderStudentCards() {
        studentAnalyticsContainer.removeAllViews();

        List<FullStudentStat> filtered = new ArrayList<>();
        for (FullStudentStat st : studentList) {
            if (searchQuery.isEmpty() ||
                st.name.toLowerCase().contains(searchQuery) ||
                st.rollNo.toLowerCase().contains(searchQuery) ||
                st.gnr.toLowerCase().contains(searchQuery)) {
                filtered.add(st);
            }
        }

        if (filtered.isEmpty()) {
            TextView txtEmpty = new TextView(this);
            txtEmpty.setText("No student records found for " + selectedYear + " - Division " + selectedDiv + ".");
            txtEmpty.setTextColor(Color.parseColor("#64748B"));
            txtEmpty.setTextSize(14);
            txtEmpty.setPadding(16, 24, 16, 24);
            studentAnalyticsContainer.addView(txtEmpty);
            return;
        }

        for (FullStudentStat st : filtered) {
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
            txtSubInfo.setText("Overall: " + st.totalPresent + " / " + st.totalConducted + " Sessions | Batch: " + st.batch);
            txtSubInfo.setTextSize(12);
            txtSubInfo.setTextColor(Color.parseColor("#64748B"));
            col.addView(txtSubInfo);

            inner.addView(col);

            // Percentage Badge with Custom Criteria Evaluation
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
            card.setOnClickListener(v -> showStudentBreakdownDialog(st));

            studentAnalyticsContainer.addView(card);
        }
    }

    private void showStudentBreakdownDialog(FullStudentStat st) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(st.name + " (" + st.rollNo + ")");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        TextView lblOverall = new TextView(this);
        boolean isEligible = st.getPercent() >= criteriaThreshold;
        lblOverall.setText("Overall Attendance: " + st.getPercent() + "% (" + st.totalPresent + "/" + st.totalConducted + ") " + (isEligible ? "[Eligible]" : "[Low <" + criteriaThreshold + "%]"));
        lblOverall.setTextSize(15);
        lblOverall.setTypeface(null, Typeface.BOLD);
        lblOverall.setTextColor(isEligible ? Color.parseColor("#059669") : Color.parseColor("#DC2626"));
        lblOverall.setPadding(0, 0, 0, 16);
        layout.addView(lblOverall);

        TextView lblSubHeader = new TextView(this);
        lblSubHeader.setText("Subject-Wise Percentage Breakdown:");
        lblSubHeader.setTextSize(14);
        lblSubHeader.setTypeface(null, Typeface.BOLD);
        lblSubHeader.setTextColor(Color.parseColor("#0F172A"));
        lblSubHeader.setPadding(0, 0, 0, 8);
        layout.addView(lblSubHeader);

        if (st.subjectStats.isEmpty()) {
            TextView txtNoSub = new TextView(this);
            txtNoSub.setText("No subject attendance recorded yet.");
            txtNoSub.setTextColor(Color.parseColor("#64748B"));
            layout.addView(txtNoSub);
        } else {
            for (Map.Entry<String, int[]> entry : st.subjectStats.entrySet()) {
                String subName = entry.getKey();
                int[] arr = entry.getValue();
                int cond = arr[0];
                int pres = arr[1];
                int p = cond > 0 ? Math.round((float) pres * 100f / cond) : 0;

                TextView txtSubRow = new TextView(this);
                txtSubRow.setText("• " + subName + ": " + p + "% (" + pres + "/" + cond + ")");
                txtSubRow.setTextSize(13);
                txtSubRow.setTextColor(Color.parseColor("#334155"));
                txtSubRow.setPadding(8, 4, 8, 4);
                layout.addView(txtSubRow);
            }
        }

        builder.setView(layout);
        builder.setPositiveButton("EXCEL REPORT", (dialog, which) -> generateSingleStudentExcel(st));
        builder.setNeutralButton("PDF REPORT", (dialog, which) -> generateSingleStudentPdf(st));
        builder.setNegativeButton("CLOSE", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    private void generateSingleStudentExcel(FullStudentStat st) {
        try {
            Calendar now = Calendar.getInstance();
            String dateStr = new SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).format(now.getTime());

            StringBuilder csv = new StringBuilder();
            csv.append("INDIVIDUAL STUDENT ATTENDANCE REPORT - TKIET CSBS\n");
            csv.append("Student Name:,").append("\"").append(st.name).append("\",Roll Number:,").append(st.rollNo).append("\n");
            csv.append("GNR Number:,").append(st.gnr).append(",Batch:,").append(st.batch).append(",Division:,").append(selectedDiv).append("\n");
            csv.append("Overall Attendance:,").append(st.getPercent()).append("%,Attended Sessions:,").append(st.totalPresent).append(",Total Sessions:,").append(st.totalConducted).append("\n");
            csv.append("Criteria Threshold:,").append(criteriaThreshold).append("%,Report Date:,").append(dateStr).append("\n\n");

            csv.append("Subject Name,Attended Sessions,Total Conducted,Subject Percentage (%),Eligibility Status\n");

            for (Map.Entry<String, int[]> entry : st.subjectStats.entrySet()) {
                String subName = entry.getKey();
                int[] arr = entry.getValue();
                int cond = arr[0];
                int pres = arr[1];
                int p = cond > 0 ? Math.round((float) pres * 100f / cond) : 0;
                String status = p >= criteriaThreshold ? "Eligible" : "Low (<" + criteriaThreshold + "%)";

                csv.append("\"").append(subName).append("\",")
                   .append(pres).append(",")
                   .append(cond).append(",")
                   .append(p).append("%,\n")
                   .append(status).append("\n");
            }

            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "StudentExcelReports");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "Student_" + st.rollNo + "_" + System.currentTimeMillis() + ".csv");

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(csv.toString().getBytes("UTF-8"));
            fos.close();

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share Student Excel Report (.csv)"));
            Toast.makeText(this, "Student Excel Report (.csv) Exported Successfully!", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Error generating Student Excel Report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void generateSingleStudentPdf(FullStudentStat st) {
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
        canvas.drawText("INDIVIDUAL STUDENT ATTENDANCE REPORT", 40, 45, paint);

        paint.setTextSize(12);
        paint.setFakeBoldText(false);
        canvas.drawText("Student Name: " + st.name, 40, 75, paint);
        canvas.drawText("Roll No: " + st.rollNo + "  |  GNR: " + st.gnr + "  |  Batch: " + st.batch, 40, 95, paint);
        canvas.drawText("Year: " + selectedYear + "  |  Division: " + selectedDiv + "  |  Criteria Threshold: " + criteriaThreshold + "%", 40, 115, paint);

        paint.setFakeBoldText(true);
        canvas.drawText("Overall Attendance: " + st.getPercent() + "% (" + st.totalPresent + "/" + st.totalConducted + " Sessions)", 40, 140, paint);

        canvas.drawText("Subject", 40, 170, paint);
        canvas.drawText("Attended", 320, 170, paint);
        canvas.drawText("Percentage", 440, 170, paint);

        paint.setStrokeWidth(1);
        canvas.drawLine(40, 180, 555, 180, paint);

        paint.setFakeBoldText(false);
        int y = 200;

        for (Map.Entry<String, int[]> entry : st.subjectStats.entrySet()) {
            String subName = entry.getKey();
            int[] arr = entry.getValue();
            int cond = arr[0];
            int pres = arr[1];
            int p = cond > 0 ? Math.round((float) pres * 100f / cond) : 0;

            canvas.drawText(subName, 40, y, paint);
            canvas.drawText(pres + " / " + cond, 320, y, paint);
            canvas.drawText(p + "%", 440, y, paint);

            y += 24;
        }

        document.finishPage(page);

        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "StudentAnalyticsReports");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "Student_" + st.rollNo + "_" + System.currentTimeMillis() + ".pdf");

        try {
            document.writeTo(new FileOutputStream(file));
            document.close();

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share Student PDF Report"));
            Toast.makeText(this, "Student PDF Report Generated!", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Error generating Student PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
