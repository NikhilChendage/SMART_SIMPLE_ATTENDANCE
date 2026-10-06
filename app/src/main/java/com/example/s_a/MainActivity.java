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
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    public static class StudentAttendanceItem {
        public String id, name, rollNo, year, division, batch;
        public int status; // 0: Not Marked, 1: Present, 2: Absent

        public StudentAttendanceItem() {}

        public StudentAttendanceItem(String id, String name, String rollNo, String year, String division, String batch, int status) {
            this.id = id;
            this.name = name;
            this.rollNo = rollNo;
            this.year = year;
            this.division = division;
            this.batch = batch;
            this.status = status;
        }
    }

    private final List<StudentAttendanceItem> assignedStudents = new ArrayList<>();
    private GridLayout gridLayout;
    private TextView txtPresent, txtAbsent, txtNotMarked, txtSessionInfo;

    private String sessionName = "General Session";
    private String sessionSubject = "General Subject";
    private String sessionType = "Lecture";
    private String sessionYear = "TY";
    private String sessionDiv = "A";
    private String sessionBatch = "";
    private String sessionDate = "";
    private String sessionStartTime = "";
    private String sessionEndTime = "";
    private String sessionId = "";
    private DatabaseReference attendanceRef, studentsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        txtPresent = findViewById(R.id.txtPresent);
        txtAbsent = findViewById(R.id.txtAbsent);
        txtNotMarked = findViewById(R.id.txtNotMarked);
        txtSessionInfo = findViewById(R.id.txtSessionInfo);
        gridLayout = findViewById(R.id.gridLayout);
        
        TextView txtHeader = findViewById(R.id.txtHeader);
        String passedName = getIntent().getStringExtra("SESSION_NAME");
        if (passedName != null) sessionName = passedName;

        String sub = getIntent().getStringExtra("SESSION_SUBJECT");
        if (sub != null) sessionSubject = sub;
        else sessionSubject = sessionName;

        String type = getIntent().getStringExtra("SESSION_TYPE");
        if (type != null) sessionType = type;

        String yr = getIntent().getStringExtra("SESSION_YEAR");
        if (yr != null) sessionYear = yr;

        String div = getIntent().getStringExtra("SESSION_DIV");
        if (div != null) sessionDiv = div;

        String batch = getIntent().getStringExtra("SESSION_BATCH");
        if (batch != null) sessionBatch = batch;

        sessionDate = getIntent().getStringExtra("SESSION_DATE");
        sessionStartTime = getIntent().getStringExtra("SESSION_START_TIME");
        sessionEndTime = getIntent().getStringExtra("SESSION_END_TIME");

        txtHeader.setText(sessionSubject + "\nAttendance");

        String dayOfWeek = "Today";
        if (sessionDate != null && !sessionDate.isEmpty()) {
            try {
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date parsedDate = format.parse(sessionDate);
                if (parsedDate != null) {
                    dayOfWeek = new SimpleDateFormat("EEEE", Locale.getDefault()).format(parsedDate);
                }
            } catch (Exception ignored) {}
        }

        String infoText = "Subject: " + sessionSubject +
                "\nDay: " + dayOfWeek + " | Date: " + (sessionDate != null && !sessionDate.isEmpty() ? sessionDate : "Today") +
                "\nTime: " + (sessionStartTime != null && !sessionStartTime.isEmpty() ? sessionStartTime : "-") +
                (sessionEndTime != null && !sessionEndTime.isEmpty() ? " to " + sessionEndTime : "") +
                "\nType: " + sessionType + " | Div: " + sessionDiv + (sessionBatch.isEmpty() ? "" : " | Batch: " + sessionBatch);
        txtSessionInfo.setText(infoText);

        sessionId = getIntent().getStringExtra("SESSION_ID");
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = (sessionSubject + "_" + sessionDiv + "_" + sessionBatch).replaceAll("[^a-zA-Z0-9_]", "_");
        }
        attendanceRef = FirebaseDatabase.getInstance().getReference("attendance").child(sessionId);
        studentsRef = FirebaseDatabase.getInstance().getReference("students");

        Button btnMarkAll = findViewById(R.id.btnMarkAll);
        Button btnReset = findViewById(R.id.btnReset);
        Button btnSharePdf = findViewById(R.id.btnSharePdf);
        Button btnShareWhatsappText = findViewById(R.id.btnShareWhatsappText);

        btnMarkAll.setOnClickListener(v -> {
            for (StudentAttendanceItem s : assignedStudents) {
                s.status = 1; // Present
            }
            refreshGridSquares();
            saveAttendanceToFirebase();
            Toast.makeText(this, "All Marked Present & Auto-Saved to DB", Toast.LENGTH_SHORT).show();
        });

        btnReset.setOnClickListener(v -> {
            for (StudentAttendanceItem s : assignedStudents) {
                s.status = 0; // Not Marked
            }
            refreshGridSquares();
            saveAttendanceToFirebase();
            Toast.makeText(this, "Attendance Reset & Auto-Saved to DB", Toast.LENGTH_SHORT).show();
        });

        Button btnExportExcel = findViewById(R.id.btnExportExcel);
        if (btnExportExcel != null) btnExportExcel.setOnClickListener(v -> exportExcelReport());
        btnSharePdf.setOnClickListener(v -> generateAndSharePdf());
        btnShareWhatsappText.setOnClickListener(v -> shareWhatsappText());

        loadStudentsAndAttendance();
    }

    private void loadStudentsAndAttendance() {
        studentsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot studentSnapshot) {
                assignedStudents.clear();
                for (DataSnapshot ds : studentSnapshot.getChildren()) {
                    String name = ds.child("name").getValue(String.class);
                    String rollNo = ds.child("rollNo").getValue(String.class);
                    String yr = ds.child("year").getValue(String.class);
                    String div = ds.child("division").getValue(String.class);
                    String batch = ds.child("batch").getValue(String.class);
                    String id = ds.getKey();

                    if (div != null && div.equalsIgnoreCase(sessionDiv)) {
                        if (sessionBatch.isEmpty() || (batch != null && batch.equalsIgnoreCase(sessionBatch))) {
                            assignedStudents.add(new StudentAttendanceItem(id, name != null ? name : "Student", rollNo != null ? rollNo : "1", yr != null ? yr : sessionYear, div, batch != null ? batch : "", 0));
                        }
                    }
                }

                Collections.sort(assignedStudents, (s1, s2) -> {
                    try {
                        int r1 = Integer.parseInt(s1.rollNo.replaceAll("[^0-9]", ""));
                        int r2 = Integer.parseInt(s2.rollNo.replaceAll("[^0-9]", ""));
                        return Integer.compare(r1, r2);
                    } catch (NumberFormatException e) {
                        return s1.rollNo.compareTo(s2.rollNo);
                    }
                });

                attendanceRef.child("records").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot attSnapshot) {
                        for (StudentAttendanceItem student : assignedStudents) {
                            if (attSnapshot.hasChild(student.rollNo)) {
                                Integer st = attSnapshot.child(student.rollNo).child("status").getValue(Integer.class);
                                if (st != null) student.status = st;
                            }
                        }
                        refreshGridSquares();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        refreshGridSquares();
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Failed to load students: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveAttendanceToFirebase() {
        Date now = new Date();
        String todayFormattedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now);
        String todayDayName = new SimpleDateFormat("EEEE", Locale.getDefault()).format(now);
        String finalDate = (sessionDate != null && !sessionDate.trim().isEmpty()) ? sessionDate : todayFormattedDate;

        DatabaseReference recordsRef = attendanceRef.child("records");
        for (StudentAttendanceItem s : assignedStudents) {
            recordsRef.child(s.rollNo).child("name").setValue(s.name);
            recordsRef.child(s.rollNo).child("status").setValue(s.status);
        }
        attendanceRef.child("subject").setValue(sessionSubject);
        attendanceRef.child("type").setValue(sessionType);
        attendanceRef.child("division").setValue(sessionDiv);
        attendanceRef.child("batch").setValue(sessionBatch);
        attendanceRef.child("date").setValue(finalDate);
        attendanceRef.child("day").setValue(todayDayName);
        attendanceRef.child("time").setValue(sessionStartTime);
        attendanceRef.child("completed").setValue(true);

        // Update session state in Firebase "sessions/{sessionId}"
        if (sessionId != null && !sessionId.isEmpty()) {
            DatabaseReference currentSessionRef = FirebaseDatabase.getInstance().getReference("sessions").child(sessionId);
            currentSessionRef.child("completed").setValue(true);
            currentSessionRef.child("lastAttendanceDate").setValue(finalDate);
            currentSessionRef.child("date").setValue(finalDate);
            currentSessionRef.child("day").setValue(todayDayName);
        }

        Toast.makeText(this, "Attendance Saved & Archived as Past Session!", Toast.LENGTH_SHORT).show();
    }

    private void refreshGridSquares() {
        gridLayout.removeAllViews();
        int present = 0, absent = 0, notMarked = 0;

        for (StudentAttendanceItem student : assignedStudents) {
            if (student.status == 1) present++;
            else if (student.status == 2) absent++;
            else notMarked++;

            Button button = new Button(this);
            button.setText(student.rollNo);
            
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = 120;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(8, 8, 8, 8);
            button.setLayoutParams(params);
            button.setAllCaps(false);
            button.setTextSize(16);
            button.setTypeface(null, Typeface.BOLD);

            if (student.status == 1) {
                button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50"))); // Green
                button.setTextColor(Color.WHITE);
            } else if (student.status == 2) {
                button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F44336"))); // Red
                button.setTextColor(Color.WHITE);
            } else {
                button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFFFFF"))); // White
                button.setTextColor(Color.BLACK);
            }

            button.setOnClickListener(v -> {
                if (student.status == 0) student.status = 1; // Present
                else if (student.status == 1) student.status = 2; // Absent
                else student.status = 0; // Not Marked

                refreshGridSquares();
                saveAttendanceToFirebase();
            });

            gridLayout.addView(button);
        }

        txtPresent.setText("Present\n" + present);
        txtAbsent.setText("Absent\n" + absent);
        txtNotMarked.setText("Not Marked\n" + notMarked);
    }

    private void shareWhatsappText() {
        StringBuilder presentRolls = new StringBuilder();
        StringBuilder absentRolls = new StringBuilder();

        for (StudentAttendanceItem s : assignedStudents) {
            if (s.status == 1) {
                if (presentRolls.length() > 0) presentRolls.append(",");
                presentRolls.append(s.rollNo);
            } else {
                if (absentRolls.length() > 0) absentRolls.append(",");
                absentRolls.append(s.rollNo);
            }
        }

        Calendar now = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

        String realDate = dateFormat.format(now.getTime());
        String realDay = dayFormat.format(now.getTime());
        String realTime = timeFormat.format(now.getTime());

        String displayDate = (sessionDate != null && !sessionDate.trim().isEmpty()) ? sessionDate : realDate;
        String displayTime = (sessionStartTime != null && !sessionStartTime.trim().isEmpty()) ? sessionStartTime + (sessionEndTime != null && !sessionEndTime.trim().isEmpty() ? " to " + sessionEndTime : "") : realTime;

        String batchStr = (!sessionBatch.isEmpty()) ? "PRACTICAL BATCH-" + sessionBatch + "\n" : "";

        String message = "TKIET CSBS\n" +
                "Date: " + displayDate + " (" + realDay + ")\n" +
                "DIVISION " + sessionDiv + "\n" +
                "Subject:- " + sessionSubject + "\n" +
                "Time: " + displayTime + "\n" +
                batchStr + "\n" +
                "PRESENT STUDENTS:- " + (presentRolls.length() > 0 ? presentRolls.toString() : "None") + "\n\n" +
                "ABSENT STUDENT:- " + (absentRolls.length() > 0 ? absentRolls.toString() : "None");

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, message);
        startActivity(Intent.createChooser(intent, "Share Attendance Text via WhatsApp"));
    }

    private void exportExcelReport() {
        try {
            Calendar now = Calendar.getInstance();
            String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now.getTime());

            StringBuilder csv = new StringBuilder();
            csv.append("ATTENDANCE REPORT - TKIET CSBS\n");
            csv.append("Subject:,").append(sessionSubject).append("\n");
            csv.append("Type:,").append(sessionType).append(",Division:,").append(sessionDiv).append(",Batch:,").append(sessionBatch).append("\n");
            csv.append("Date:,").append(sessionDate.isEmpty() ? dateStr : sessionDate).append(",Time:,").append(sessionStartTime).append("\n\n");

            csv.append("Roll Number,Student Name,Attendance Status,Year,Division,Batch\n");

            for (StudentAttendanceItem s : assignedStudents) {
                String statusStr = (s.status == 1) ? "Present" : (s.status == 2) ? "Absent" : "Not Marked";
                csv.append(s.rollNo).append(",")
                   .append("\"").append(s.name).append("\",")
                   .append(statusStr).append(",")
                   .append(s.year).append(",")
                   .append(s.division).append(",")
                   .append(s.batch).append("\n");
            }

            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "ExcelReports");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "Attendance_" + sessionSubject.replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.currentTimeMillis() + ".csv");

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(csv.toString().getBytes("UTF-8"));
            fos.close();

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Export Excel Report (.csv)"));
            Toast.makeText(this, "Excel Report (.csv) Exported Successfully!", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Error exporting Excel report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void generateAndSharePdf() {
        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();

        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();

        Calendar now = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

        String realDate = dateFormat.format(now.getTime());
        String realDay = dayFormat.format(now.getTime());
        String realTime = timeFormat.format(now.getTime());

        String displayDate = (sessionDate != null && !sessionDate.trim().isEmpty()) ? sessionDate : realDate;
        String displayTime = (sessionStartTime != null && !sessionStartTime.trim().isEmpty()) ? sessionStartTime + (sessionEndTime != null && !sessionEndTime.trim().isEmpty() ? " to " + sessionEndTime : "") : realTime;

        paint.setColor(Color.BLACK);
        paint.setTextSize(18);
        paint.setFakeBoldText(true);
        canvas.drawText("ATTENDANCE REPORT - TKIET CSBS", 40, 45, paint);

        paint.setTextSize(12);
        paint.setFakeBoldText(false);
        canvas.drawText("Subject: " + sessionSubject, 40, 75, paint);
        canvas.drawText("Type: " + sessionType + "  |  Division: " + sessionDiv + (sessionBatch.isEmpty() ? "" : "  |  Batch: " + sessionBatch), 40, 95, paint);
        canvas.drawText("Date: " + displayDate + " (" + realDay + ")  |  Time: " + displayTime, 40, 115, paint);

        canvas.drawText("Report Generated At: " + realDate + " " + realTime, 40, 135, paint);

        int present = 0, absent = 0, notMarked = 0;
        for (StudentAttendanceItem s : assignedStudents) {
            if (s.status == 1) present++;
            else if (s.status == 2) absent++;
            else notMarked++;
        }

        canvas.drawText("Summary -> Present: " + present + " | Absent: " + absent + " | Not Marked: " + notMarked, 40, 165, paint);

        paint.setFakeBoldText(true);
        canvas.drawText("Roll No", 40, 195, paint);
        canvas.drawText("Student Name", 130, 195, paint);
        canvas.drawText("Status", 450, 195, paint);

        paint.setStrokeWidth(1);
        canvas.drawLine(40, 205, 555, 205, paint);

        paint.setFakeBoldText(false);
        int y = 225;

        for (StudentAttendanceItem s : assignedStudents) {
            if (y > 800) {
                document.finishPage(page);
                page = document.startPage(pageInfo);
                canvas = page.getCanvas();
                y = 50;
            }
            String statusStr = (s.status == 1) ? "Present" : (s.status == 2) ? "Absent" : "Not Marked";

            canvas.drawText(s.rollNo, 40, y, paint);
            canvas.drawText(s.name, 130, y, paint);
            canvas.drawText(statusStr, 450, y, paint);

            y += 22;
        }

        document.finishPage(page);

        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "AttendanceReports");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "Attendance_" + System.currentTimeMillis() + ".pdf");

        try {
            document.writeTo(new FileOutputStream(file));
            document.close();

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share Attendance Text via WhatsApp"));

        } catch (IOException e) {
            Toast.makeText(this, "Error generating PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
            document.close();
        }
    }
}
