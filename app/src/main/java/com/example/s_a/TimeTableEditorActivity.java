package com.example.s_a;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TimeTableEditorActivity extends AppCompatActivity {

    private String ttId = "default_tt";
    private String ttTitle = "Time Table";
    private String ttSem = "I";
    private String ttAcademicYear = "2026-27";
    private String ttDiv = "A";
    private String ttYear = "Second Year";

    private final String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
    
    private final List<String> periodLabels = new ArrayList<>();
    private final List<String> slotTimes = new ArrayList<>();
    private final List<List<String>> slotData = new ArrayList<>();

    private int selectedStartRow = -1;
    private int selectedDayIndex = -1;

    private DatabaseReference slotsRef, periodsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_timetable_editor);

        if (getIntent().hasExtra("TT_ID")) ttId = getIntent().getStringExtra("TT_ID");
        if (getIntent().hasExtra("TT_TITLE")) ttTitle = getIntent().getStringExtra("TT_TITLE");
        if (getIntent().hasExtra("TT_SEM")) ttSem = getIntent().getStringExtra("TT_SEM");
        if (getIntent().hasExtra("TT_ACADEMIC_YEAR")) ttAcademicYear = getIntent().getStringExtra("TT_ACADEMIC_YEAR");
        if (getIntent().hasExtra("TT_DIV")) ttDiv = getIntent().getStringExtra("TT_DIV");
        if (getIntent().hasExtra("TT_YEAR")) ttYear = getIntent().getStringExtra("TT_YEAR");

        TextView txtTtHeader = findViewById(R.id.txtTtHeader);
        txtTtHeader.setText(ttTitle + "\nCustom Time Table Structure");

        // Initialize with default 6 periods if empty
        if (periodLabels.isEmpty()) {
            for (int i = 1; i <= 6; i++) {
                periodLabels.add("Period " + i);
                slotTimes.add("09.15 to 10.15");
                List<String> rowDays = new ArrayList<>();
                for (int d = 0; d < 6; d++) rowDays.add("Free Slot");
                slotData.add(rowDays);
            }
        }

        slotsRef = FirebaseDatabase.getInstance().getReference("timetable").child(ttId).child("slots");
        periodsRef = FirebaseDatabase.getInstance().getReference("timetable").child(ttId).child("periods");

        Button btnSaveTt = findViewById(R.id.btnSaveTt);
        Button btnAddRow = findViewById(R.id.btnAddRow);

        btnSaveTt.setOnClickListener(v -> {
            saveToFirebase();
            Toast.makeText(this, "Custom Time Table Saved Successfully!", Toast.LENGTH_SHORT).show();
        });

        btnAddRow.setOnClickListener(v -> showAddRowDialog());

        loadFromFirebase();
    }

    private void loadFromFirebase() {
        slotsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    periodLabels.clear();
                    slotTimes.clear();
                    slotData.clear();

                    int r = 0;
                    while (snapshot.hasChild("row_" + r)) {
                        DataSnapshot rowSnap = snapshot.child("row_" + r);
                        periodLabels.add(rowSnap.child("name").getValue(String.class) != null ? rowSnap.child("name").getValue(String.class) : "Period " + (r + 1));
                        slotTimes.add(rowSnap.child("time").getValue(String.class) != null ? rowSnap.child("time").getValue(String.class) : "09.15 to 10.15");

                        List<String> rowDays = new ArrayList<>();
                        for (int d = 0; d < 6; d++) {
                            String val = rowSnap.child("d" + d).getValue(String.class);
                            rowDays.add(val != null ? val : "Free Slot");
                        }
                        slotData.add(rowDays);
                        r++;
                    }
                }
                if (periodLabels.isEmpty()) {
                    for (int i = 1; i <= 6; i++) {
                        periodLabels.add("Period " + i);
                        slotTimes.add("09.15 to 10.15");
                        List<String> rowDays = new ArrayList<>();
                        for (int d = 0; d < 6; d++) rowDays.add("Free Slot");
                        slotData.add(rowDays);
                    }
                }
                renderTableSheet();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                renderTableSheet();
            }
        });
    }

    private void saveToFirebase() {
        DatabaseReference sessionsRef = FirebaseDatabase.getInstance().getReference("sessions");

        for (int r = 0; r < periodLabels.size(); r++) {
            DatabaseReference rowRef = slotsRef.child("row_" + r);
            rowRef.child("name").setValue(periodLabels.get(r));
            rowRef.child("time").setValue(slotTimes.get(r));

            List<String> rowDays = slotData.get(r);
            for (int d = 0; d < 6; d++) {
                String rawText = rowDays.get(d);
                rowRef.child("d" + d).setValue(rawText);

                if (rawText != null && !rawText.contains("Free Slot")) {
                    // Check if this cell is a continuation of previous row's merged 2-hour practical
                    boolean isPrevSameMerged = (r > 0 && r - 1 < slotData.size() &&
                            slotData.get(r - 1).get(d) != null &&
                            slotData.get(r - 1).get(d).equalsIgnoreCase(rawText) &&
                            (rawText.contains("Practical") || rawText.contains("(")));

                    if (isPrevSameMerged) {
                        // Skip duplicate session creation for second period of merged 2-hour practical
                        continue;
                    }

                    boolean isNextSameMerged = (r + 1 < slotData.size() &&
                            slotData.get(r + 1).get(d) != null &&
                            slotData.get(r + 1).get(d).equalsIgnoreCase(rawText) &&
                            (rawText.contains("Practical") || rawText.contains("(")));

                    String[] entries;
                    if (rawText.contains("\n---\n")) {
                        entries = rawText.split("\n---\n");
                    } else {
                        entries = rawText.split("\n");
                    }

                    for (int b = 0; b < entries.length; b++) {
                        String entry = entries[b].trim();
                        if (entry.isEmpty() || entry.equalsIgnoreCase("Free Slot")) continue;

                        String sessionId = "auto_" + ttId + "_r" + r + "_d" + d + (entries.length > 1 ? "_b" + b : "");

                        String subject = entry;
                        String faculty = "";
                        String sessionType = "Lecture";
                        String batch = "";

                        if (entry.contains(" - ")) {
                            String[] parts = entry.split(" - ", 2);
                            subject = parts[0].trim();
                            faculty = parts[1].trim();
                        } else if (entry.contains("\n")) {
                            String[] parts = entry.split("\n", 2);
                            subject = parts[0].trim();
                            faculty = parts[1].trim();
                        }

                        if (subject.contains("(")) {
                            int idx = subject.indexOf('(');
                            int lastIdx = subject.lastIndexOf(')');
                            if (lastIdx > idx) {
                                String subName = subject.substring(0, idx).trim();
                                String details = subject.substring(idx + 1, lastIdx).trim();
                                subject = subName;

                                if (details.toLowerCase().contains("practical") || details.toLowerCase().contains("cont")) {
                                    sessionType = "Practical";
                                } else if (details.toLowerCase().contains("tutorial") || details.toLowerCase().contains("tut")) {
                                    sessionType = "Tutorial";
                                }

                                if (details.toLowerCase().contains("batch")) {
                                    int bIdx = details.toLowerCase().indexOf("batch");
                                    batch = details.substring(bIdx + 5).replaceAll("[^a-zA-Z0-9]", "").trim();
                                } else if (!details.isEmpty() && !details.equalsIgnoreCase("Lecture") && !details.equalsIgnoreCase("Practical") && !details.equalsIgnoreCase("Tutorial")) {
                                    batch = details.replaceAll("[^a-zA-Z0-9]", "").trim();
                                    sessionType = "Practical";
                                }
                            }
                        }

                        String timeRange = slotTimes.get(r);
                        String startTime = timeRange;
                        String endTime = "";
                        if (timeRange.contains("to")) {
                            String[] tParts = timeRange.split("to");
                            startTime = tParts[0].trim();
                            endTime = tParts[1].trim();
                        }

                        // Auto-fetch 2-hour duration if merged with next period
                        if (isNextSameMerged && r + 1 < slotTimes.size()) {
                            String nextTimeRange = slotTimes.get(r + 1);
                            if (nextTimeRange.contains("to")) {
                                String[] nextParts = nextTimeRange.split("to");
                                endTime = nextParts[nextParts.length - 1].trim();
                            }
                        }

                        Map<String, Object> sessionMap = new HashMap<>();
                        sessionMap.put("id", sessionId);
                        sessionMap.put("subject", subject);
                        sessionMap.put("faculty", faculty);
                        sessionMap.put("sessionType", sessionType);
                        sessionMap.put("year", ttYear != null ? ttYear : "Second Year");
                        sessionMap.put("division", ttDiv != null ? ttDiv : "A");
                        sessionMap.put("batch", batch);
                        sessionMap.put("day", days[d]);
                        sessionMap.put("startTime", startTime);
                        sessionMap.put("endTime", endTime);

                        sessionsRef.child(sessionId).setValue(sessionMap);
                    }
                }
            }
        }
    }

    private void showAddRowDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Custom Period / Break Row");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        TextView lblName = new TextView(this);
        lblName.setText("Row Name (e.g. Period 7, Seminar, Recess, Lunch)");
        layout.addView(lblName);
        final EditText etName = new EditText(this);
        etName.setHint("Enter row name");
        layout.addView(etName);

        TextView lblTime = new TextView(this);
        lblTime.setText("Time Slot");
        lblTime.setPadding(0, 10, 0, 0);
        layout.addView(lblTime);
        final EditText etTime = new EditText(this);
        etTime.setHint("Enter Time (e.g. 04.00 to 05.00)");
        layout.addView(etTime);

        builder.setView(layout);
        builder.setPositiveButton("ADD ROW", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String time = etTime.getText().toString().trim();

            if (name.isEmpty()) name = "Custom Period";
            if (time.isEmpty()) time = "04.00 to 05.00";

            periodLabels.add(name);
            slotTimes.add(time);
            List<String> rowDays = new ArrayList<>();
            for (int d = 0; d < 6; d++) rowDays.add("Free Slot");
            slotData.add(rowDays);

            saveToFirebase();
            renderTableSheet();
            Toast.makeText(this, "Custom Row Added Successfully!", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("CANCEL", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    private String formatShort(String raw) {
        if (raw == null || raw.trim().isEmpty() || raw.equals("Free Slot")) return "Free Slot";

        String normalized = raw.replace("\r\n", "\n").trim();
        String[] entries;
        if (normalized.contains("\n---\n")) {
            entries = normalized.split("\n---\n");
        } else {
            entries = normalized.split("\n");
        }
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < entries.length; i++) {
            String entry = entries[i].trim();
            if (entry.isEmpty() || entry.equalsIgnoreCase("Free Slot")) continue;

            // 1. Clean newlines inside parentheses
            StringBuilder cleanEntry = new StringBuilder();
            boolean inParen = false;
            for (char c : entry.toCharArray()) {
                if (c == '(') inParen = true;
                if (c == ')') inParen = false;
                if (inParen && c == '\n') {
                    cleanEntry.append(" ");
                } else {
                    cleanEntry.append(c);
                }
            }
            entry = cleanEntry.toString();

            // 2. Extract valid lines and join lines starting with '('
            String[] lines = entry.split("\n");
            List<String> validLines = new ArrayList<>();
            for (String l : lines) {
                l = l.trim();
                if (l.isEmpty()) continue;
                if (l.startsWith("(") && !validLines.isEmpty()) {
                    int lastIdx = validLines.size() - 1;
                    validLines.set(lastIdx, validLines.get(lastIdx) + " " + l);
                } else {
                    validLines.add(l);
                }
            }

            if (validLines.isEmpty()) continue;

            String firstLine = validLines.get(0);
            String faculty = "";
            if (validLines.size() > 1) {
                StringBuilder facSb = new StringBuilder();
                for (int k = 1; k < validLines.size(); k++) {
                    if (facSb.length() > 0) facSb.append(" ");
                    facSb.append(validLines.get(k));
                }
                faculty = facSb.toString();
            } else if (firstLine.contains(" - ")) {
                String[] p = firstLine.split(" - ", 2);
                firstLine = p[0].trim();
                faculty = p[1].trim();
            }

            String subject = firstLine;
            String type = "Lecture";
            String batch = "";

            if (firstLine.contains("(")) {
                int idx = firstLine.indexOf('(');
                int lastIdx = firstLine.lastIndexOf(')');
                if (lastIdx > idx) {
                    subject = firstLine.substring(0, idx).trim();
                    String details = firstLine.substring(idx + 1, lastIdx).trim();

                    if (details.toLowerCase().contains("practical") || details.toLowerCase().contains("cont")) {
                        type = "Practical";
                    } else if (details.toLowerCase().contains("tutorial") || details.toLowerCase().contains("tut")) {
                        type = "Tutorial";
                    }

                    if (details.toLowerCase().contains("batch")) {
                        int bIdx = details.toLowerCase().indexOf("batch");
                        batch = details.substring(bIdx + 5).replaceAll("[^a-zA-Z0-9]", "").trim();
                    } else if (!details.isEmpty() && !details.equalsIgnoreCase("Lecture") && !details.equalsIgnoreCase("Practical") && !details.equalsIgnoreCase("Tutorial")) {
                        batch = details.replaceAll("[^a-zA-Z0-9]", "").trim();
                        type = "Practical";
                    }
                }
            }

            String acrSubject = getAcronym(subject);

            if (sb.length() > 0) sb.append("\n");

            if (type.equalsIgnoreCase("Practical")) {
                sb.append(acrSubject).append("(").append(batch.isEmpty() ? "P" : batch).append(")");
            } else if (type.equalsIgnoreCase("Tutorial")) {
                sb.append(acrSubject).append("(Tut)");
            } else {
                sb.append(acrSubject);
            }
        }

        return sb.length() > 0 ? sb.toString() : "Free Slot";
    }

    private String getAcronym(String subject) {
        if (subject == null || subject.trim().isEmpty()) return "SUB";
        String s = subject.trim();

        // Direct mapping for known subjects
        String lower = s.toLowerCase();
        if (lower.contains("machine learning")) return "ML";
        if (lower.contains("fundamentals of management")) return "FOM";
        if (lower.contains("data analysis") || lower.contains("design and analysis of algorithms") || lower.contains("daa")) return "DAA";
        if (lower.contains("data structure")) return "DS";
        if (lower.contains("java")) return "JAVA";
        if (lower.contains("database") || lower.contains("dbms")) return "DBMS";
        if (lower.contains("operating system") || lower.contains("os")) return "OS";
        if (lower.contains("computer network") || lower.contains("cn")) return "CN";
        if (lower.contains("software engineering") || lower.contains("se")) return "SE";
        if (lower.contains("artificial intelligence") || lower.contains("ai")) return "AI";

        // If already short / uppercase (e.g. "JAVA", "ML", "DAA", "FOM")
        if (s.length() <= 5 && s.matches("[a-zA-Z0-9+#-]+")) {
            return s.toUpperCase();
        }

        // Auto-generate acronym from first letters of words
        String[] words = s.split("\\s+");
        if (words.length > 1) {
            StringBuilder acr = new StringBuilder();
            for (String w : words) {
                String cleanWord = w.replaceAll("[^a-zA-Z0-9]", "");
                if (cleanWord.length() > 0 && !cleanWord.equalsIgnoreCase("and") && !cleanWord.equalsIgnoreCase("of") && !cleanWord.equalsIgnoreCase("in")) {
                    acr.append(Character.toUpperCase(cleanWord.charAt(0)));
                }
            }
            if (acr.length() >= 2) {
                return acr.toString();
            }
        }

        return s.length() > 6 ? s.substring(0, 6).toUpperCase() : s.toUpperCase();
    }

    private void renderTableSheet() {
        TableLayout table = findViewById(R.id.tableTimeTable);
        table.removeAllViews();

        TableRow headerRow = new TableRow(this);
        headerRow.setBackgroundColor(Color.parseColor("#FFFFFF"));

        String[] headers = {"Period / Row", "Time", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (String h : headers) {
            TextView tv = new TextView(this);
            tv.setText(h);
            tv.setTextColor(Color.parseColor("#475569"));
            tv.setTypeface(null, Typeface.BOLD);
            tv.setPadding(12, 16, 12, 16);
            tv.setGravity(Gravity.CENTER);
            headerRow.addView(tv);
        }
        table.addView(headerRow);

        for (int r = 0; r < periodLabels.size(); r++) {
            final int rowIndex = r;
            TableRow row = new TableRow(this);
            row.setBackgroundColor(Color.parseColor("#FFFFFF"));

            TextView tvPeriod = new TextView(this);
            tvPeriod.setText(periodLabels.get(r));
            tvPeriod.setPadding(16, 24, 16, 24);
            tvPeriod.setGravity(Gravity.CENTER);
            tvPeriod.setTypeface(null, Typeface.BOLD);
            tvPeriod.setTextColor(Color.parseColor("#4F46E5"));
            tvPeriod.setBackgroundResource(R.drawable.bg_cell_period);
            tvPeriod.setOnClickListener(v -> showEditRowDialog(rowIndex));
            
            TableRow.LayoutParams periodParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.MATCH_PARENT);
            periodParams.setMargins(8, 8, 4, 8);
            tvPeriod.setLayoutParams(periodParams);
            row.addView(tvPeriod);

            TextView tvTime = new TextView(this);
            tvTime.setText(slotTimes.get(r).replace(" to ", "\n"));
            tvTime.setPadding(16, 24, 16, 24);
            tvTime.setGravity(Gravity.CENTER);
            tvTime.setTextColor(Color.parseColor("#475569"));
            tvTime.setTextSize(12);
            tvTime.setTypeface(null, Typeface.BOLD);
            tvTime.setBackgroundResource(R.drawable.bg_cell_time);
            tvTime.setOnClickListener(v -> showEditRowDialog(rowIndex));
            
            TableRow.LayoutParams timeParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.MATCH_PARENT);
            timeParams.setMargins(4, 8, 8, 8);
            tvTime.setLayoutParams(timeParams);
            row.addView(tvTime);

            List<String> rowDays = slotData.get(r);
            for (int d = 0; d < 6; d++) {
                final int dayIndex = d;
                TextView tvCell = new TextView(this);

                String rawText = rowDays.get(d);
                String cellText = formatShort(rawText);

                // Check for 2-Hour Practical continuation vs 1-Hour Lectures
                boolean isPrevSamePractical = false;
                if (rowIndex > 0 && r - 1 < slotData.size()) {
                    String prevRaw = slotData.get(r - 1).get(d);
                    if (prevRaw != null && rawText != null && !rawText.contains("Free Slot") && rawText.equalsIgnoreCase(prevRaw) && (rawText.contains("Practical") || rawText.contains("("))) {
                        isPrevSamePractical = true;
                    }
                }

                boolean isNextSamePractical = false;
                if (!isPrevSamePractical && rowIndex + 1 < slotData.size()) {
                    String nextRaw = slotData.get(rowIndex + 1).get(d);
                    if (nextRaw != null && rawText != null && !rawText.contains("Free Slot") && rawText.equalsIgnoreCase(nextRaw) && (rawText.contains("Practical") || rawText.contains("("))) {
                        isNextSamePractical = true;
                    }
                }

                boolean isSelectedCell = (rowIndex == selectedStartRow && dayIndex == selectedDayIndex);

                tvCell.setOnClickListener(v -> {
                    if (selectedStartRow != -1 && selectedDayIndex == dayIndex && rowIndex != selectedStartRow) {
                        int r1 = Math.min(selectedStartRow, rowIndex);
                        int r2 = Math.max(selectedStartRow, rowIndex);
                        showMergePeriodsDialog(r1, r2, dayIndex);
                    } else if (selectedStartRow != -1) {
                        selectedStartRow = -1;
                        selectedDayIndex = -1;
                        renderTableSheet();
                    } else {
                        showEditSlotDialog(rowIndex, dayIndex);
                    }
                });

                tvCell.setOnLongClickListener(v -> {
                    if (selectedStartRow == -1) {
                        selectedStartRow = rowIndex;
                        selectedDayIndex = dayIndex;
                        renderTableSheet();
                        Toast.makeText(this, "Period " + (rowIndex + 1) + " (" + days[dayIndex] + ") selected. Tap another period on " + days[dayIndex] + " to merge.", Toast.LENGTH_LONG).show();
                    } else if (selectedDayIndex == dayIndex && rowIndex != selectedStartRow) {
                        int r1 = Math.min(selectedStartRow, rowIndex);
                        int r2 = Math.max(selectedStartRow, rowIndex);
                        showMergePeriodsDialog(r1, r2, dayIndex);
                    } else {
                        selectedStartRow = rowIndex;
                        selectedDayIndex = dayIndex;
                        renderTableSheet();
                        Toast.makeText(this, "Period " + (rowIndex + 1) + " (" + days[dayIndex] + ") selected. Tap another period on " + days[dayIndex] + " to merge.", Toast.LENGTH_LONG).show();
                    }
                    return true;
                });

                int topMargin = 8;
                int bottomMargin = 8;

                if (isSelectedCell) {
                    tvCell.setBackgroundColor(Color.parseColor("#C7D2FE")); // Selected Highlight Blue
                    tvCell.setTextColor(Color.parseColor("#1E1B4B"));
                    tvCell.setText(cellText);
                } else if (isNextSamePractical) {
                    // Top half of merged 2-hour practical card
                    tvCell.setBackgroundResource(R.drawable.bg_cell_practical_top);
                    tvCell.setTextColor(Color.parseColor("#065F46"));
                    tvCell.setText(cellText + "\n(2 Hrs)");
                    bottomMargin = 0; // Connects seamlessly to bottom cell!
                } else if (isPrevSamePractical) {
                    // Bottom half of merged 2-hour practical card
                    tvCell.setBackgroundResource(R.drawable.bg_cell_practical_bottom);
                    tvCell.setTextColor(Color.parseColor("#065F46"));
                    tvCell.setText("↳ (Practical)");
                    topMargin = 0; // Connects seamlessly to top cell!
                } else if (rawText != null && (rawText.contains("Practical") || rawText.contains("("))) {
                    tvCell.setBackgroundResource(R.drawable.bg_cell_practical);
                    tvCell.setTextColor(Color.parseColor("#065F46"));
                    tvCell.setText(cellText);
                } else if (rawText != null && rawText.contains("Tutorial")) {
                    tvCell.setBackgroundResource(R.drawable.bg_cell_tutorial);
                    tvCell.setTextColor(Color.parseColor("#92400E"));
                    tvCell.setText(cellText);
                } else if (rawText == null || rawText.contains("Free Slot")) {
                    tvCell.setBackgroundResource(R.drawable.bg_cell_free);
                    tvCell.setTextColor(Color.parseColor("#94A3B8"));
                    tvCell.setText(cellText);
                } else {
                    tvCell.setBackgroundResource(R.drawable.bg_cell_lecture);
                    tvCell.setTextColor(Color.parseColor("#3730A3"));
                    tvCell.setText(cellText);
                }

                TableRow.LayoutParams cellParams = new TableRow.LayoutParams(260, TableRow.LayoutParams.MATCH_PARENT);
                cellParams.setMargins(8, topMargin, 8, bottomMargin);
                tvCell.setLayoutParams(cellParams);

                row.addView(tvCell);
            }

            table.addView(row);
        }
    }

    private void showEditRowDialog(int r) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit or Delete Row: " + periodLabels.get(r));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        TextView lblName = new TextView(this);
        lblName.setText("Row Name");
        layout.addView(lblName);
        final EditText etName = new EditText(this);
        etName.setText(periodLabels.get(r));
        layout.addView(etName);

        TextView lblTime = new TextView(this);
        lblTime.setText("Time Slot");
        lblTime.setPadding(0, 10, 0, 0);
        layout.addView(lblTime);
        final EditText etTime = new EditText(this);
        etTime.setText(slotTimes.get(r));
        layout.addView(etTime);

        builder.setView(layout);
        builder.setPositiveButton("UPDATE", (dialog, which) -> {
            String newName = etName.getText().toString().trim();
            String newTime = etTime.getText().toString().trim();

            if (!newName.isEmpty()) periodLabels.set(r, newName);
            if (!newTime.isEmpty()) slotTimes.set(r, newTime);

            saveToFirebase();
            renderTableSheet();
            Toast.makeText(this, "Row Updated Successfully!", Toast.LENGTH_SHORT).show();
        });

        builder.setNeutralButton("DELETE ROW", (dialog, which) -> {
            periodLabels.remove(r);
            slotTimes.remove(r);
            slotData.remove(r);

            slotsRef.removeValue().addOnSuccessListener(aVoid -> {
                saveToFirebase();
                renderTableSheet();
                Toast.makeText(this, "Row Deleted Successfully!", Toast.LENGTH_SHORT).show();
            });
        });

        builder.setNegativeButton("CANCEL", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    private void showEditSlotDialog(int r, int d) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit " + periodLabels.get(r) + " on " + days[d]);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        String currentCellText = slotData.get(r).get(d);
        boolean hasExisting = currentCellText != null && !currentCellText.trim().isEmpty() && !currentCellText.equals("Free Slot");

        List<String> existingEntries = new ArrayList<>();
        if (hasExisting) {
            String[] split;
            if (currentCellText.contains("\n---\n")) {
                split = currentCellText.split("\n---\n");
            } else {
                split = currentCellText.split("\n");
            }
            for (String s : split) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("───")) {
                    existingEntries.add(trimmed);
                }
            }
        }

        final Spinner spinnerTarget = new Spinner(this);
        if (hasExisting) {
            TextView lblTarget = new TextView(this);
            lblTarget.setText("Target Batch / Slot Entry");
            lblTarget.setTypeface(null, Typeface.BOLD);
            lblTarget.setTextColor(Color.parseColor("#0F172A"));
            layout.addView(lblTarget);

            List<String> targetOptions = new ArrayList<>();
            targetOptions.add("+ Add New Parallel Batch");
            for (int i = 0; i < existingEntries.size(); i++) {
                String e = existingEntries.get(i);
                targetOptions.add("Edit Entry " + (i + 1) + ": " + e);
            }

            spinnerTarget.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, targetOptions));
            layout.addView(spinnerTarget);
        }

        TextView lblSub = new TextView(this);
        lblSub.setText("Subject");
        lblSub.setPadding(0, 10, 0, 0);
        layout.addView(lblSub);
        final Spinner spinnerSubject = new Spinner(this);
        layout.addView(spinnerSubject);

        TextView lblFac = new TextView(this);
        lblFac.setText("Faculty / Teacher");
        lblFac.setPadding(0, 10, 0, 0);
        layout.addView(lblFac);
        final Spinner spinnerFaculty = new Spinner(this);
        layout.addView(spinnerFaculty);

        final List<String> facultyNames = new ArrayList<>();
        final List<String> allSubjectNames = new ArrayList<>();
        final Map<String, List<String>> facultySubjectMap = new HashMap<>();

        DatabaseReference facultyRef = FirebaseDatabase.getInstance().getReference("faculty");
        facultyRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                facultyNames.clear();
                allSubjectNames.clear();
                facultySubjectMap.clear();

                for (DataSnapshot ds : snapshot.getChildren()) {
                    String name = ds.child("name").getValue(String.class);
                    String rawSubjects = ds.child("subject").getValue(String.class);

                    if (name != null && !name.trim().isEmpty()) {
                        String cleanName = name.trim();
                        if (!facultyNames.contains(cleanName)) {
                            facultyNames.add(cleanName);
                        }

                        List<String> teacherSubs = new ArrayList<>();
                        if (rawSubjects != null && !rawSubjects.trim().isEmpty()) {
                            // AUTO-SEPARATE MULTIPLE SUBJECTS (split by comma, semicolon, slash)
                            String[] splitSubs = rawSubjects.split("[,;/]+");
                            for (String sub : splitSubs) {
                                String cleanSub = sub.trim();
                                if (!cleanSub.isEmpty()) {
                                    if (!teacherSubs.contains(cleanSub)) {
                                        teacherSubs.add(cleanSub);
                                    }
                                    if (!allSubjectNames.contains(cleanSub)) {
                                        allSubjectNames.add(cleanSub);
                                    }
                                }
                            }
                        }
                        if (teacherSubs.isEmpty()) {
                            teacherSubs.add("General Subject");
                        }
                        facultySubjectMap.put(cleanName, teacherSubs);
                    }
                }

                if (facultyNames.isEmpty()) facultyNames.add("No Faculty Found");
                if (allSubjectNames.isEmpty()) allSubjectNames.add("General Subject");

                spinnerFaculty.setAdapter(new ArrayAdapter<>(TimeTableEditorActivity.this, android.R.layout.simple_spinner_dropdown_item, facultyNames));

                // Auto-update subjects dropdown whenever selected faculty changes
                spinnerFaculty.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        String selectedFac = spinnerFaculty.getSelectedItem() != null ? spinnerFaculty.getSelectedItem().toString() : "";
                        List<String> subjectsForSelectedFac = facultySubjectMap.get(selectedFac);

                        if (subjectsForSelectedFac != null && !subjectsForSelectedFac.isEmpty()) {
                            spinnerSubject.setAdapter(new ArrayAdapter<>(TimeTableEditorActivity.this, android.R.layout.simple_spinner_dropdown_item, subjectsForSelectedFac));
                        } else {
                            spinnerSubject.setAdapter(new ArrayAdapter<>(TimeTableEditorActivity.this, android.R.layout.simple_spinner_dropdown_item, allSubjectNames));
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TimeTableEditorActivity.this, "Failed to load faculty: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        TextView lblType = new TextView(this);
        lblType.setText("Session Type");
        lblType.setPadding(0, 10, 0, 0);
        layout.addView(lblType);
        final Spinner spinnerType = new Spinner(this);
        String[] sessionTypes = {"Lecture", "Practical", "Tutorial"};
        spinnerType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sessionTypes));
        layout.addView(spinnerType);

        TextView lblBatch = new TextView(this);
        lblBatch.setText("Batch");
        lblBatch.setPadding(0, 10, 0, 0);
        lblBatch.setVisibility(View.GONE);
        layout.addView(lblBatch);

        final Spinner spinnerBatch = new Spinner(this);
        spinnerBatch.setVisibility(View.GONE);
        layout.addView(spinnerBatch);

        Runnable updateBatches = () -> {
            String div = ttDiv != null ? ttDiv : "A";
            String[] batches = new String[10];
            for (int b = 0; b < 10; b++) {
                batches[b] = div + (b + 1);
            }
            spinnerBatch.setAdapter(new ArrayAdapter<>(TimeTableEditorActivity.this, android.R.layout.simple_spinner_dropdown_item, batches));
        };

        spinnerType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String type = spinnerType.getSelectedItem().toString();
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

        if (hasExisting) {
            spinnerTarget.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position > 0 && position <= existingEntries.size()) {
                        String entry = existingEntries.get(position - 1);
                        String subName = entry;
                        String facName = "";
                        String batchVal = "";
                        String typeVal = "Lecture";

                        if (entry.contains(" - ")) {
                            String[] p = entry.split(" - ", 2);
                            subName = p[0].trim();
                            facName = p[1].trim();
                        } else if (entry.contains("\n")) {
                            String[] p = entry.split("\n", 2);
                            subName = p[0].trim();
                            facName = p[1].trim();
                        }

                        if (subName.contains("(")) {
                            int idx = subName.indexOf('(');
                            int lastIdx = subName.lastIndexOf(')');
                            if (lastIdx > idx) {
                                String details = subName.substring(idx + 1, lastIdx).trim();
                                subName = subName.substring(0, idx).trim();

                                if (details.toLowerCase().contains("practical") || details.toLowerCase().contains("cont")) {
                                    typeVal = "Practical";
                                } else if (details.toLowerCase().contains("tutorial") || details.toLowerCase().contains("tut")) {
                                    typeVal = "Tutorial";
                                }

                                if (details.toLowerCase().contains("batch")) {
                                    int bIdx = details.toLowerCase().indexOf("batch");
                                    batchVal = details.substring(bIdx + 5).replaceAll("[^a-zA-Z0-9]", "").trim();
                                } else if (!details.isEmpty() && !details.equalsIgnoreCase("Lecture") && !details.equalsIgnoreCase("Practical") && !details.equalsIgnoreCase("Tutorial")) {
                                    batchVal = details.replaceAll("[^a-zA-Z0-9]", "").trim();
                                    typeVal = "Practical";
                                }
                            }
                        }

                        for (int i = 0; i < spinnerSubject.getCount(); i++) {
                            if (spinnerSubject.getItemAtPosition(i).toString().equalsIgnoreCase(subName)) {
                                spinnerSubject.setSelection(i);
                                break;
                            }
                        }
                        for (int i = 0; i < spinnerFaculty.getCount(); i++) {
                            if (spinnerFaculty.getItemAtPosition(i).toString().equalsIgnoreCase(facName)) {
                                spinnerFaculty.setSelection(i);
                                break;
                            }
                        }

                        if (typeVal.equalsIgnoreCase("Practical")) {
                            spinnerType.setSelection(1);
                            spinnerBatch.setVisibility(View.VISIBLE);
                            lblBatch.setVisibility(View.VISIBLE);
                            updateBatches.run();
                            if (!batchVal.isEmpty()) {
                                for (int i = 0; i < spinnerBatch.getCount(); i++) {
                                    if (spinnerBatch.getItemAtPosition(i).toString().equalsIgnoreCase(batchVal)) {
                                        spinnerBatch.setSelection(i);
                                        break;
                                    }
                                }
                            }
                        } else if (typeVal.equalsIgnoreCase("Tutorial")) {
                            spinnerType.setSelection(2);
                            spinnerBatch.setVisibility(View.GONE);
                            lblBatch.setVisibility(View.GONE);
                        } else {
                            spinnerType.setSelection(0);
                            spinnerBatch.setVisibility(View.GONE);
                            lblBatch.setVisibility(View.GONE);
                        }
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        TextView lblTime = new TextView(this);
        lblTime.setText("Custom Time Slot");
        lblTime.setPadding(0, 10, 0, 0);
        layout.addView(lblTime);
        final EditText etTime = new EditText(this);
        etTime.setHint("Enter Time (e.g. 09.15 to 10.15)");
        etTime.setText(slotTimes.get(r));
        layout.addView(etTime);

        builder.setView(layout);
        builder.setPositiveButton("UPDATE", (dialog, which) -> {
            String subject = spinnerSubject.getSelectedItem() != null ? spinnerSubject.getSelectedItem().toString() : "Subject";
            String faculty = spinnerFaculty.getSelectedItem() != null ? spinnerFaculty.getSelectedItem().toString() : "Faculty";
            String type = spinnerType.getSelectedItem().toString();
            String batch = (spinnerBatch.getVisibility() == View.VISIBLE && spinnerBatch.getSelectedItem() != null) ? spinnerBatch.getSelectedItem().toString() : "";
            String time = etTime.getText().toString().trim();

            if (time.isEmpty()) {
                time = slotTimes.get(r);
            }

            String newSlotFormatted;
            if (type.equalsIgnoreCase("Practical")) {
                newSlotFormatted = subject + "(" + (batch.isEmpty() ? "P" : batch) + ") - " + faculty;

                // Auto-fill 2-hour Practical into the next period slot for Day 'd' if empty
                if (r + 1 < slotData.size()) {
                    List<String> nextRowDays = slotData.get(r + 1);
                    String nextVal = nextRowDays.get(d);
                    if (nextVal == null || nextVal.trim().isEmpty() || nextVal.equalsIgnoreCase("Free Slot")) {
                        nextRowDays.set(d, newSlotFormatted);
                    }
                }
            } else if (type.equalsIgnoreCase("Tutorial")) {
                newSlotFormatted = subject + "(Tut) - " + faculty;
            } else {
                newSlotFormatted = subject + " - " + faculty;
            }

            int selectedTarget = hasExisting ? spinnerTarget.getSelectedItemPosition() : 0;
            String finalFormatted;

            if (!hasExisting || selectedTarget == 0) {
                if (hasExisting) {
                    finalFormatted = currentCellText + "\n" + newSlotFormatted;
                } else {
                    finalFormatted = newSlotFormatted;
                }
            } else {
                existingEntries.set(selectedTarget - 1, newSlotFormatted);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < existingEntries.size(); i++) {
                    if (i > 0) sb.append("\n");
                    sb.append(existingEntries.get(i));
                }
                finalFormatted = sb.toString();
            }

            slotData.get(r).set(d, finalFormatted);
            slotTimes.set(r, time);

            saveToFirebase();
            renderTableSheet();
            Toast.makeText(this, "Slot Updated Successfully!", Toast.LENGTH_SHORT).show();
        });

        builder.setNeutralButton("CLEAR / REMOVE", (dialog, which) -> {
            int selectedTarget = hasExisting ? spinnerTarget.getSelectedItemPosition() : 0;
            if (!hasExisting || selectedTarget == 0) {
                slotData.get(r).set(d, "Free Slot");
            } else {
                existingEntries.remove(selectedTarget - 1);
                if (existingEntries.isEmpty()) {
                    slotData.get(r).set(d, "Free Slot");
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < existingEntries.size(); i++) {
                        if (i > 0) sb.append("\n");
                        sb.append(existingEntries.get(i));
                    }
                    slotData.get(r).set(d, sb.toString());
                }
            }
            saveToFirebase();
            renderTableSheet();
            Toast.makeText(this, "Slot updated successfully", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("CANCEL", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    private void showMergePeriodsDialog(int r1, int r2, int dayIdx) {
        String p1Name = periodLabels.get(r1);
        String p2Name = periodLabels.get(r2);
        String dayName = days[dayIdx];

        String p1Time = slotTimes.get(r1);
        String p2Time = slotTimes.get(r2);
        String startStr = p1Time.contains("to") ? p1Time.split("to")[0].trim() : p1Time.trim();
        String endStr = p2Time.contains("to") ? p2Time.split("to")[p2Time.split("to").length - 1].trim() : p2Time.trim();
        String combinedDurationStr = startStr + " to " + endStr;

        String currentR1 = slotData.get(r1).get(dayIdx);
        String currentR2 = slotData.get(r2).get(dayIdx);

        List<String> existingEntries = new ArrayList<>();
        for (String src : new String[]{currentR1, currentR2}) {
            if (src != null && !src.trim().isEmpty() && !src.equalsIgnoreCase("Free Slot")) {
                String[] split = src.split("\n");
                for (String s : split) {
                    String trimmed = s.trim();
                    if (!trimmed.isEmpty() && !trimmed.equalsIgnoreCase("Free Slot") && !existingEntries.contains(trimmed)) {
                        existingEntries.add(trimmed);
                    }
                }
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Merge " + p1Name + " & " + p2Name + " on " + dayName);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        TextView lblInfo = new TextView(this);
        lblInfo.setText("Auto-Fetched 2-Hour Duration: " + combinedDurationStr);
        lblInfo.setTypeface(null, Typeface.BOLD);
        lblInfo.setTextSize(15);
        lblInfo.setTextColor(Color.parseColor("#059669"));
        lblInfo.setPadding(0, 0, 0, 12);
        layout.addView(lblInfo);

        TextView lblMsg = new TextView(this);
        if (!existingEntries.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String e : existingEntries) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(formatShort(e));
            }
            lblMsg.setText("Merge " + p1Name + " and " + p2Name + " on " + dayName + " for " + sb.toString() + " into a single 2-Hour Practical Block (" + combinedDurationStr + ")?");
        } else {
            lblMsg.setText("Merge " + p1Name + " and " + p2Name + " on " + dayName + " into a single 2-Hour Practical Block (" + combinedDurationStr + ")?");
        }
        lblMsg.setTextColor(Color.parseColor("#0F172A"));
        lblMsg.setTextSize(14);
        layout.addView(lblMsg);

        builder.setView(layout);
        builder.setPositiveButton("MERGE PERIODS", (dialog, which) -> {
            if (!existingEntries.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < existingEntries.size(); i++) {
                    if (i > 0) sb.append("\n");
                    sb.append(existingEntries.get(i));
                }
                String combinedText = sb.toString();

                slotData.get(r1).set(dayIdx, combinedText);
                slotData.get(r2).set(dayIdx, combinedText);

                selectedStartRow = -1;
                selectedDayIndex = -1;

                saveToFirebase();
                renderTableSheet();
                Toast.makeText(this, "Merged " + p1Name + " & " + p2Name + " on " + dayName + " Successfully!", Toast.LENGTH_SHORT).show();
            } else {
                selectedStartRow = -1;
                selectedDayIndex = -1;
                showEditSlotDialog(r1, dayIdx);
            }
        });

        builder.setNegativeButton("CANCEL", (dialog, which) -> {
            selectedStartRow = -1;
            selectedDayIndex = -1;
            renderTableSheet();
            dialog.dismiss();
        });

        builder.create().show();
    }
}
