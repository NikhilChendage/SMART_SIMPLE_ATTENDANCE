package com.example.s_a;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class StudentManagementActivity extends AppCompatActivity {

    public static class Student {
        public String id;
        public String name;
        public String rollNo;
        public String gnr;
        public String phone;
        public String email;
        public String year;
        public String division;
        public String batch;
        public boolean isSelected;

        public Student() {} // Required for Firebase

        public Student(String id, String name, String rollNo, String gnr, String phone, String email, String year, String division, String batch) {
            this.id = id;
            this.name = name;
            this.rollNo = rollNo;
            this.gnr = gnr;
            this.phone = phone;
            this.email = email;
            this.year = year;
            this.division = division;
            this.batch = batch;
            this.isSelected = false;
        }
    }

    private final List<Student> studentList = new ArrayList<>();
    private LinearLayout studentsContainer;
    private EditText etSearch;
    private CheckBox cbSelectAll;
    
    private TextView txtSortRollAsc, txtSortRollDesc, txtSortNameAsc, txtSortNameDesc;
    private String selectedYearFilter = null;
    private String selectedDivFilter = null;
    private String sortOrder = "ROLL_ASC"; // ROLL_ASC, ROLL_DESC, NAME_ASC, NAME_DESC
    private String searchQuery = "";
    private DatabaseReference studentsRef;
    private ActivityResultLauncher<String> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_student_management);

        if (getIntent().hasExtra("SELECTED_YEAR")) {
            selectedYearFilter = getIntent().getStringExtra("SELECTED_YEAR");
        }
        if (getIntent().hasExtra("SELECTED_DIV")) {
            selectedDivFilter = getIntent().getStringExtra("SELECTED_DIV");
        }

        TextView txtHeaderSubtitle = findViewById(R.id.txtHeaderSubtitle);
        if (txtHeaderSubtitle != null && (selectedYearFilter != null || selectedDivFilter != null)) {
            String sub = (selectedYearFilter != null ? selectedYearFilter : "") + (selectedDivFilter != null ? " - Division " + selectedDivFilter : "") + " Records";
            txtHeaderSubtitle.setText(sub);
        }

        studentsContainer = findViewById(R.id.studentsContainer);
        etSearch = findViewById(R.id.etSearch);
        cbSelectAll = findViewById(R.id.cbSelectAll);
        Button btnDeleteSelected = findViewById(R.id.btnDeleteSelected);

        txtSortRollAsc = findViewById(R.id.txtSortRollAsc);
        txtSortRollDesc = findViewById(R.id.txtSortRollDesc);
        txtSortNameAsc = findViewById(R.id.txtSortNameAsc);
        txtSortNameDesc = findViewById(R.id.txtSortNameDesc);

        Button btnAddStudent = findViewById(R.id.btnAddStudent);
        Button btnImport = findViewById(R.id.btnImport);

        studentsRef = FirebaseDatabase.getInstance().getReference("students");

        setupSortTabs();
        setupSearch();

        btnAddStudent.setOnClickListener(v -> showAddOrEditStudentDialog(null));

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        importAndFilterFile(uri);
                    }
                }
        );

        btnImport.setOnClickListener(v -> filePickerLauncher.launch("*/*"));

        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (Student s : getFilteredStudents()) {
                s.isSelected = isChecked;
            }
            refreshStudentCards();
        });

        btnDeleteSelected.setOnClickListener(v -> {
            List<Student> toDelete = new ArrayList<>();
            for (Student s : studentList) {
                if (s.isSelected) {
                    toDelete.add(s);
                }
            }
            if (toDelete.isEmpty()) {
                Toast.makeText(this, "No students selected", Toast.LENGTH_SHORT).show();
                return;
            }
            for (Student s : toDelete) {
                if (s.id != null) {
                    studentsRef.child(s.id).removeValue();
                }
            }
            cbSelectAll.setChecked(false);
            Toast.makeText(this, "Deleted " + toDelete.size() + " students from database", Toast.LENGTH_SHORT).show();
        });

        loadStudentsFromFirebase();
    }

    private void importAndFilterFile(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) {
                Toast.makeText(this, "Cannot open selected file", Toast.LENGTH_SHORT).show();
                return;
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            byte[] fileBytes = buffer.toByteArray();
            is.close();

            // 1. Check if file is XLSX (ZIP container PK\x03\x04 or .xlsx)
            boolean isXlsx = false;
            if (fileBytes.length > 4 && fileBytes[0] == 'P' && fileBytes[1] == 'K' && fileBytes[2] == 3 && fileBytes[3] == 4) {
                isXlsx = true;
            } else if (uri.toString().toLowerCase().contains(".xlsx") || uri.toString().toLowerCase().contains(".xls")) {
                isXlsx = true;
            }

            if (isXlsx) {
                parseXlsxStreamAndImport(fileBytes);
                return;
            }

            // 2. Check if file is PDF (%PDF or .pdf)
            boolean isPdf = false;
            if (fileBytes.length > 4 && fileBytes[0] == '%' && fileBytes[1] == 'P' && fileBytes[2] == 'D' && fileBytes[3] == 'F') {
                isPdf = true;
            } else if (uri.toString().toLowerCase().contains(".pdf")) {
                isPdf = true;
            }

            if (isPdf) {
                parsePdfStreamAndImport(fileBytes);
                return;
            }

            // 3. Otherwise, process as plain CSV / TSV text file
            BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(fileBytes)));

            String line;
            int importedCount = 0;

            int nameIdx = -1;
            int rollIdx = -1;
            int gnrIdx = -1;
            int phoneIdx = -1;
            int emailIdx = -1;
            int yearIdx = -1;
            int divIdx = -1;
            int batchIdx = -1;

            boolean isFirstRow = true;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] cols = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
                if (cols.length < 2) {
                    if (line.contains("\t")) {
                        cols = line.split("\t");
                    } else if (line.contains(";")) {
                        cols = line.split(";");
                    }
                }

                if (isFirstRow) {
                    isFirstRow = false;
                    for (int c = 0; c < cols.length; c++) {
                        String header = cols[c].replace("\"", "").toLowerCase().trim();
                        if (header.contains("full name") || header.contains("student name") || header.equalsIgnoreCase("name") || header.contains("full_name")) {
                            nameIdx = c;
                        } else if (header.contains("roll") || header.contains("r.no") || header.contains("rollno") || header.contains("roll number") || header.contains("roll_no")) {
                            rollIdx = c;
                        } else if (header.contains("grn") || header.contains("gnr")) {
                            gnrIdx = c;
                        } else if (header.contains("phone") || header.contains("mobile") || header.contains("contact") || header.contains("number")) {
                            phoneIdx = c;
                        } else if (header.contains("email") || header.contains("mail")) {
                            emailIdx = c;
                        } else if (header.contains("year") || header.contains("academic")) {
                            yearIdx = c;
                        } else if (header.contains("div") || header.contains("division")) {
                            divIdx = c;
                        } else if (header.contains("batch")) {
                            batchIdx = c;
                        }
                    }

                    if (nameIdx == -1) nameIdx = 0;
                    if (rollIdx == -1) rollIdx = 1;
                    if (gnrIdx == -1) gnrIdx = 2;
                    if (phoneIdx == -1) phoneIdx = 3;
                    if (emailIdx == -1) emailIdx = 4;
                    if (yearIdx == -1) yearIdx = 5;
                    if (divIdx == -1) divIdx = 6;
                    if (batchIdx == -1) batchIdx = 7;

                    String firstCol = cols[0].replace("\"", "").toLowerCase().trim();
                    if (firstCol.contains("name") || firstCol.contains("roll")) {
                        continue;
                    }
                }

                int effNameIdx = nameIdx;
                int effRollIdx = rollIdx;

                if (cols.length > 1) {
                    String col0 = cols[0].replace("\"", "").trim();
                    String col1 = cols[1].replace("\"", "").trim();

                    if (col0.matches("\\d+") && !col1.matches("\\d+")) {
                        effRollIdx = 0;
                        effNameIdx = 1;
                    } else if (!col0.matches("\\d+") && col1.matches("\\d+")) {
                        effNameIdx = 0;
                        effRollIdx = 1;
                    }
                }

                if (effRollIdx < cols.length && effNameIdx < cols.length) {
                    String rollNo = cols[effRollIdx].replace("\"", "").trim();
                    String name = cols[effNameIdx].replace("\"", "").trim();

                    if (!rollNo.isEmpty() && !name.isEmpty() && !name.equalsIgnoreCase("full name") && !name.equalsIgnoreCase("name") && !rollNo.equalsIgnoreCase("roll number")) {
                        String gnr = (gnrIdx != -1 && gnrIdx < cols.length) ? cols[gnrIdx].replace("\"", "").trim() : "GNR000";
                        String phone = (phoneIdx != -1 && phoneIdx < cols.length) ? cols[phoneIdx].replace("\"", "").trim() : "1234567890";
                        String email = (emailIdx != -1 && emailIdx < cols.length) ? cols[emailIdx].replace("\"", "").trim() : "adc@gmail.com";
                        String year = (yearIdx != -1 && yearIdx < cols.length) ? cols[yearIdx].replace("\"", "").trim() : "Third Year";
                        String div = (divIdx != -1 && divIdx < cols.length) ? cols[divIdx].replace("\"", "").trim() : "B";
                        String batch = (batchIdx != -1 && batchIdx < cols.length) ? cols[batchIdx].replace("\"", "").trim() : "B1";

                        String id = studentsRef.push().getKey();
                        if (id == null) id = String.valueOf(System.currentTimeMillis());

                        Student student = new Student(id, name, rollNo, gnr, phone, email, year, div, batch);
                        studentsRef.child(id).setValue(student);
                        importedCount++;
                    }
                }
            }

            Toast.makeText(this, "Import Complete: " + importedCount + " student rows imported successfully!", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, "Error importing file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void parseXlsxStreamAndImport(byte[] xlsxBytes) {
        try {
            List<String> sharedStrings = new ArrayList<>();
            Map<String, Map<String, String>> rowsMap = new HashMap<>();

            ByteArrayInputStream bais = new ByteArrayInputStream(xlsxBytes);
            ZipInputStream zip = new ZipInputStream(bais);
            ZipEntry entry;

            String sheet1Xml = null;
            String sharedStringsXml = null;

            while ((entry = zip.getNextEntry()) != null) {
                String entryName = entry.getName().toLowerCase();
                if (entryName.endsWith("sharedstrings.xml")) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[2048];
                    int len;
                    while ((len = zip.read(buf)) > 0) baos.write(buf, 0, len);
                    sharedStringsXml = baos.toString("UTF-8");
                } else if (entryName.endsWith("sheet1.xml") || entryName.endsWith("sheet.xml")) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[2048];
                    int len;
                    while ((len = zip.read(buf)) > 0) baos.write(buf, 0, len);
                    sheet1Xml = baos.toString("UTF-8");
                }
            }
            zip.close();

            if (sharedStringsXml != null) {
                Pattern tPattern = Pattern.compile("<t[^>]*>([\\s\\S]*?)</t>");
                Matcher tMatcher = tPattern.matcher(sharedStringsXml);
                while (tMatcher.find()) {
                    String text = tMatcher.group(1).replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").trim();
                    sharedStrings.add(text);
                }
            }

            if (sheet1Xml != null) {
                Pattern cellPattern = Pattern.compile("<c r=\"([A-Z]+)(\\d+)\"(?:[^>]*?t=\"([^\"]+)\")?[^>]*?>(?:<v>([\\s\\S]*?)</v>)?");
                Matcher cellMatcher = cellPattern.matcher(sheet1Xml);

                while (cellMatcher.find()) {
                    String colLetter = cellMatcher.group(1);
                    String rowNum = cellMatcher.group(2);
                    String type = cellMatcher.group(3);
                    String val = cellMatcher.group(4);

                    if (val != null) {
                        String cellValue = val.trim();
                        if ("s".equalsIgnoreCase(type)) {
                            try {
                                int sstIdx = Integer.parseInt(cellValue);
                                if (sstIdx >= 0 && sstIdx < sharedStrings.size()) {
                                    cellValue = sharedStrings.get(sstIdx);
                                }
                            } catch (Exception ignored) {}
                        }

                        if (!rowsMap.containsKey(rowNum)) {
                            rowsMap.put(rowNum, new HashMap<>());
                        }
                        rowsMap.get(rowNum).put(colLetter, cellValue);
                    }
                }
            }

            String nameCol = "A";
            String rollCol = "B";
            String gnrCol = "C";
            String phoneCol = "D";
            String emailCol = "E";
            String yearCol = "F";
            String divCol = "G";
            String batchCol = "H";

            if (rowsMap.containsKey("1")) {
                Map<String, String> row1 = rowsMap.get("1");
                for (Map.Entry<String, String> cell : row1.entrySet()) {
                    String h = cell.getValue().toLowerCase().trim();
                    String col = cell.getKey();
                    if (h.contains("full name") || h.contains("student name") || h.equals("name")) nameCol = col;
                    else if (h.contains("roll")) rollCol = col;
                    else if (h.contains("grn") || h.contains("gnr")) gnrCol = col;
                    else if (h.contains("phone") || h.contains("mobile")) phoneCol = col;
                    else if (h.contains("email")) emailCol = col;
                    else if (h.contains("year") || h.contains("academic")) yearCol = col;
                    else if (h.contains("div")) divCol = col;
                    else if (h.contains("batch")) batchCol = col;
                }
            }

            int importedCount = 0;
            List<Integer> sortedRowKeys = new ArrayList<>();
            for (String rK : rowsMap.keySet()) {
                try {
                    int rN = Integer.parseInt(rK);
                    if (rN > 1) sortedRowKeys.add(rN);
                } catch (Exception ignored) {}
            }
            Collections.sort(sortedRowKeys);

            for (int rN : sortedRowKeys) {
                Map<String, String> row = rowsMap.get(String.valueOf(rN));
                if (row == null) continue;

                String name = row.get(nameCol);
                String rollNo = row.get(rollCol);

                if (name == null || name.trim().isEmpty()) name = row.get("A");
                if (rollNo == null || rollNo.trim().isEmpty()) rollNo = row.get("B");

                if (name != null && !name.trim().isEmpty() && rollNo != null && !rollNo.trim().isEmpty()) {
                    name = name.trim();
                    rollNo = rollNo.trim();

                    if (name.equalsIgnoreCase("full name") || name.equalsIgnoreCase("name")) continue;

                    String gnr = row.get(gnrCol) != null ? row.get(gnrCol).trim() : "GNR000";
                    String phone = row.get(phoneCol) != null ? row.get(phoneCol).trim() : "1234567890";
                    String email = row.get(emailCol) != null ? row.get(emailCol).trim() : "adc@gmail.com";
                    String year = row.get(yearCol) != null ? row.get(yearCol).trim() : "Third Year";
                    String div = row.get(divCol) != null ? row.get(divCol).trim() : "B";
                    String batch = row.get(batchCol) != null ? row.get(batchCol).trim() : "B1";

                    gnr = gnr.replaceAll("(?<=\\d)O(?=\\w)", "0");

                    String id = studentsRef.push().getKey();
                    if (id == null) id = String.valueOf(System.currentTimeMillis());

                    Student student = new Student(id, name, rollNo, gnr, phone, email, year, div, batch);
                    studentsRef.child(id).setValue(student);
                    importedCount++;
                }
            }

            Toast.makeText(this, "Excel Import Complete: " + importedCount + " students imported from Excel file!", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, "Error parsing Excel sheet: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void parsePdfStreamAndImport(byte[] pdfBytes) {
        try {
            String pdfRaw = new String(pdfBytes, "ISO-8859-1");
            List<String> tokens = new ArrayList<>();

            Pattern streamPattern = Pattern.compile("stream[\\r\\n]+([\\s\\S]*?)[\\r\\n]+endstream");
            Matcher streamMatcher = streamPattern.matcher(pdfRaw);

            while (streamMatcher.find()) {
                String streamContent = streamMatcher.group(1);
                byte[] streamBytes = streamContent.getBytes("ISO-8859-1");

                try {
                    ByteArrayInputStream bais = new ByteArrayInputStream(streamBytes);
                    if (bais.available() > 2) {
                        bais.read();
                        bais.read();
                    }
                    InflaterInputStream inflater = new InflaterInputStream(bais);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[2048];
                    int len;
                    while ((len = inflater.read(buf)) > 0) {
                        baos.write(buf, 0, len);
                    }
                    String decompressed = baos.toString("ISO-8859-1");

                    Pattern tjPattern = Pattern.compile("\\[([\\s\\S]*?)\\]\\s*TJ|\\(([^()]+)\\)\\s*Tj");
                    Matcher tjMatcher = tjPattern.matcher(decompressed);

                    while (tjMatcher.find()) {
                        String match = tjMatcher.group(0);
                        Pattern innerP = Pattern.compile("\\(([^()]*)\\)");
                        Matcher innerM = innerP.matcher(match);
                        StringBuilder fullStr = new StringBuilder();
                        while (innerM.find()) {
                            fullStr.append(innerM.group(1));
                        }
                        String cleanToken = fullStr.toString().trim();
                        if (!cleanToken.isEmpty()) {
                            tokens.add(cleanToken);
                        }
                    }
                } catch (Exception ignored) {}
            }

            List<String> dataTokens = new ArrayList<>();
            for (String t : tokens) {
                String lower = t.toLowerCase();
                if (lower.contains("full name") || lower.contains("roll number") || lower.contains("grn number") ||
                    lower.contains("phone number") || lower.contains("email address") || lower.contains("academic year") ||
                    lower.contains("division") || lower.contains("batch") || lower.equals("en")) {
                    continue;
                }
                dataTokens.add(t);
            }

            int importedCount = 0;
            for (int i = 0; i < dataTokens.size(); i++) {
                String token = dataTokens.get(i);
                if (i + 1 < dataTokens.size() && dataTokens.get(i + 1).matches("\\d+")) {
                    String name = token;
                    String rollNo = dataTokens.get(i + 1);
                    String gnr = (i + 2 < dataTokens.size()) ? dataTokens.get(i + 2) : "GNR000";
                    String phone = (i + 3 < dataTokens.size() && dataTokens.get(i + 3).matches("\\d+")) ? dataTokens.get(i + 3) : "1234567890";
                    String email = (i + 4 < dataTokens.size() && dataTokens.get(i + 4).contains("@")) ? dataTokens.get(i + 4) : "adc@gmail.com";
                    String year = (i + 5 < dataTokens.size()) ? dataTokens.get(i + 5) : "Third Year";
                    String div = (i + 6 < dataTokens.size()) ? dataTokens.get(i + 6) : "B";
                    String batch = (i + 7 < dataTokens.size()) ? dataTokens.get(i + 7) : "B1";

                    gnr = gnr.replaceAll("(?<=\\d)O(?=\\w)", "0");

                    String id = studentsRef.push().getKey();
                    if (id == null) id = String.valueOf(System.currentTimeMillis());

                    Student student = new Student(id, name, rollNo, gnr, phone, email, year, div, batch);
                    studentsRef.child(id).setValue(student);
                    importedCount++;

                    i += 7;
                }
            }

            Toast.makeText(this, "PDF Import Complete: " + importedCount + " students imported from PDF document!", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, "Error parsing PDF document: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadStudentsFromFirebase() {
        studentsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                studentList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Student student = ds.getValue(Student.class);
                    if (student != null) {
                        if (student.id == null) student.id = ds.getKey();
                        studentList.add(student);
                    }
                }
                refreshStudentCards();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(StudentManagementActivity.this, "Failed to load students: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupSortTabs() {
        TextView[] sortTabs = {txtSortRollAsc, txtSortRollDesc, txtSortNameAsc, txtSortNameDesc};
        String[] orders = {"ROLL_ASC", "ROLL_DESC", "NAME_ASC", "NAME_DESC"};

        for (int i = 0; i < sortTabs.length; i++) {
            final String order = orders[i];
            final TextView tabView = sortTabs[i];
            if (tabView != null) {
                tabView.setOnClickListener(v -> {
                    sortOrder = order;
                    for (TextView t : sortTabs) {
                        if (t != null) {
                            t.setTextColor(Color.parseColor("#64748B"));
                            t.setTypeface(null, Typeface.NORMAL);
                        }
                    }
                    tabView.setTextColor(Color.parseColor("#2563EB"));
                    tabView.setTypeface(null, Typeface.BOLD);
                    refreshStudentCards();
                });
            }
        }
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().toLowerCase().trim();
                refreshStudentCards();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private List<Student> getFilteredStudents() {
        List<Student> filtered = new ArrayList<>();
        for (Student s : studentList) {
            boolean matchesYear = selectedYearFilter == null || selectedYearFilter.isEmpty() || (s.year != null && s.year.equalsIgnoreCase(selectedYearFilter));
            boolean matchesDiv = selectedDivFilter == null || selectedDivFilter.isEmpty() || (s.division != null && s.division.equalsIgnoreCase(selectedDivFilter));

            boolean matchesSearch = searchQuery.isEmpty() ||
                    (s.name != null && s.name.toLowerCase().contains(searchQuery)) ||
                    (s.rollNo != null && s.rollNo.toLowerCase().contains(searchQuery)) ||
                    (s.gnr != null && s.gnr.toLowerCase().contains(searchQuery));

            if (matchesYear && matchesDiv && matchesSearch) {
                filtered.add(s);
            }
        }

        // Apply Ascending / Descending Sorting
        Collections.sort(filtered, (s1, s2) -> {
            if ("ROLL_ASC".equals(sortOrder)) {
                try {
                    int r1 = Integer.parseInt(s1.rollNo.replaceAll("[^0-9]", ""));
                    int r2 = Integer.parseInt(s2.rollNo.replaceAll("[^0-9]", ""));
                    return Integer.compare(r1, r2);
                } catch (Exception e) {
                    return s1.rollNo.compareToIgnoreCase(s2.rollNo);
                }
            } else if ("ROLL_DESC".equals(sortOrder)) {
                try {
                    int r1 = Integer.parseInt(s1.rollNo.replaceAll("[^0-9]", ""));
                    int r2 = Integer.parseInt(s2.rollNo.replaceAll("[^0-9]", ""));
                    return Integer.compare(r2, r1);
                } catch (Exception e) {
                    return s2.rollNo.compareToIgnoreCase(s1.rollNo);
                }
            } else if ("NAME_ASC".equals(sortOrder)) {
                return s1.name.compareToIgnoreCase(s2.name);
            } else if ("NAME_DESC".equals(sortOrder)) {
                return s2.name.compareToIgnoreCase(s1.name);
            }
            return 0;
        });

        return filtered;
    }

    private void refreshStudentCards() {
        studentsContainer.removeAllViews();
        List<Student> filtered = getFilteredStudents();

        for (int i = 0; i < filtered.size(); i++) {
            final Student student = filtered.get(i);
            CardView card = new CardView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 8);
            card.setLayoutParams(params);
            card.setRadius(14 * getResources().getDisplayMetrics().density);
            card.setCardElevation(2 * getResources().getDisplayMetrics().density);
            card.setCardBackgroundColor(Color.WHITE);

            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.HORIZONTAL);
            inner.setGravity(Gravity.CENTER_VERTICAL);
            int padHorizontal = (int) (14 * getResources().getDisplayMetrics().density);
            int padVertical = (int) (10 * getResources().getDisplayMetrics().density);
            inner.setPadding(padHorizontal, padVertical, padHorizontal, padVertical);

            CheckBox cb = new CheckBox(this);
            cb.setChecked(student.isSelected);
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> student.isSelected = isChecked);
            inner.addView(cb);

            // Compact Roll Number Badge
            TextView rollBadge = new TextView(this);
            rollBadge.setText(student.rollNo != null ? student.rollNo : "-");
            rollBadge.setTextSize(14);
            rollBadge.setTypeface(null, Typeface.BOLD);
            rollBadge.setTextColor(Color.parseColor("#2563EB"));
            rollBadge.setGravity(Gravity.CENTER);
            rollBadge.setBackgroundResource(R.drawable.bg_badge_roll);
            int badgeWidth = (int) (52 * getResources().getDisplayMetrics().density);
            int badgeHeight = (int) (38 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams rollParams = new LinearLayout.LayoutParams(badgeWidth, badgeHeight);
            rollParams.setMarginStart((int) (4 * getResources().getDisplayMetrics().density));
            rollParams.setMarginEnd((int) (12 * getResources().getDisplayMetrics().density));
            rollBadge.setLayoutParams(rollParams);
            inner.addView(rollBadge);

            // Student Name & Secondary Info Column
            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            nameParams.setMarginEnd((int) (8 * getResources().getDisplayMetrics().density));
            textCol.setLayoutParams(nameParams);

            TextView txtName = new TextView(this);
            txtName.setText(student.name);
            txtName.setTextSize(15);
            txtName.setTypeface(null, Typeface.BOLD);
            txtName.setTextColor(Color.parseColor("#0F172A"));
            txtName.setSingleLine(true);
            txtName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            textCol.addView(txtName);

            TextView txtSubInfo = new TextView(this);
            txtSubInfo.setText("GNR: " + (student.gnr != null ? student.gnr : "N/A") + " | Batch: " + (student.batch != null ? student.batch : "N/A"));
            txtSubInfo.setTextSize(12);
            txtSubInfo.setTextColor(Color.parseColor("#64748B"));
            textCol.addView(txtSubInfo);

            inner.addView(textCol);

            // Edit Button
            Button btnEdit = new Button(this);
            btnEdit.setText("Edit");
            btnEdit.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4F46E5")));
            btnEdit.setTextColor(Color.WHITE);
            btnEdit.setAllCaps(false);
            btnEdit.setTextSize(12);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    (int) (64 * getResources().getDisplayMetrics().density),
                    (int) (38 * getResources().getDisplayMetrics().density)
            );
            btnEdit.setLayoutParams(bp);
            btnEdit.setPadding(0, 0, 0, 0);
            btnEdit.setOnClickListener(v -> showAddOrEditStudentDialog(student));
            inner.addView(btnEdit);

            card.addView(inner);
            studentsContainer.addView(card);
        }
    }

    private void showAddOrEditStudentDialog(Student studentToEdit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(studentToEdit == null ? "Add New Student" : "Edit Student");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        final EditText etName = new EditText(this);
        etName.setHint("Full Name");
        if (studentToEdit != null) etName.setText(studentToEdit.name);
        layout.addView(etName);

        final EditText etRoll = new EditText(this);
        etRoll.setHint("Roll Number");
        if (studentToEdit != null) etRoll.setText(studentToEdit.rollNo);
        layout.addView(etRoll);

        final EditText etGnr = new EditText(this);
        etGnr.setHint("GNR Number");
        if (studentToEdit != null) etGnr.setText(studentToEdit.gnr);
        layout.addView(etGnr);

        final EditText etPhone = new EditText(this);
        etPhone.setHint("Phone Number");
        etPhone.setInputType(InputType.TYPE_CLASS_PHONE);
        if (studentToEdit != null) etPhone.setText(studentToEdit.phone);
        layout.addView(etPhone);

        final EditText etEmail = new EditText(this);
        etEmail.setHint("Email Address");
        etEmail.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        if (studentToEdit != null) etEmail.setText(studentToEdit.email);
        layout.addView(etEmail);

        TextView lblYear = new TextView(this);
        lblYear.setText("Academic Year");
        lblYear.setPadding(0, 10, 0, 2);
        layout.addView(lblYear);
        final Spinner spinnerYear = new Spinner(this);
        String[] years = {"First Year", "Second Year", "Third Year", "Fourth Year"};
        spinnerYear.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, years));
        if (studentToEdit != null) {
            for (int i = 0; i < years.length; i++) {
                if (years[i].equalsIgnoreCase(studentToEdit.year)) {
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

        if (studentToEdit != null) {
            for (int i = 0; i < divisions.length; i++) {
                if (divisions[i].equalsIgnoreCase(studentToEdit.division)) {
                    spinnerDiv.setSelection(i);
                    break;
                }
            }
        }
        layout.addView(spinnerDiv);

        TextView lblDivBatch = new TextView(this);
        lblDivBatch.setText("Batch (Auto-sorted by Division)");
        lblDivBatch.setPadding(0, 10, 0, 2);
        layout.addView(lblDivBatch);

        final Spinner spinnerBatch = new Spinner(this);
        layout.addView(spinnerBatch);

        Runnable updateBatchesForDiv = () -> {
            String selectedDiv = spinnerDiv.getSelectedItem() != null ? spinnerDiv.getSelectedItem().toString() : "A";
            String[] batches = new String[10];
            for (int b = 0; b < 10; b++) {
                batches[b] = selectedDiv + (b + 1);
            }
            spinnerBatch.setAdapter(new ArrayAdapter<>(StudentManagementActivity.this, android.R.layout.simple_spinner_dropdown_item, batches));

            if (studentToEdit != null && studentToEdit.batch != null) {
                for (int i = 0; i < batches.length; i++) {
                    if (batches[i].equalsIgnoreCase(studentToEdit.batch)) {
                        spinnerBatch.setSelection(i);
                        break;
                    }
                }
            }
        };

        spinnerDiv.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateBatchesForDiv.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        builder.setView(layout);
        builder.setPositiveButton(studentToEdit == null ? "SAVE" : "UPDATE", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String rollNo = etRoll.getText().toString().trim();
            String gnr = etGnr.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String year = spinnerYear.getSelectedItem().toString();
            String div = spinnerDiv.getSelectedItem().toString();
            String batch = spinnerBatch.getSelectedItem() != null ? spinnerBatch.getSelectedItem().toString() : "";

            if (name.isEmpty() || rollNo.isEmpty()) {
                Toast.makeText(this, "Name and Roll Number are required", Toast.LENGTH_SHORT).show();
                return;
            }

            String id = studentToEdit != null ? studentToEdit.id : studentsRef.push().getKey();
            if (id == null) id = String.valueOf(System.currentTimeMillis());

            Student student = new Student(id, name, rollNo, gnr.isEmpty() ? "GNR000" : gnr, phone.isEmpty() ? "9876543210" : phone, email.isEmpty() ? "student@tkiet.ac.in" : email, year, div, batch);
            studentsRef.child(id).setValue(student)
                    .addOnSuccessListener(aVoid -> Toast.makeText(this, studentToEdit == null ? "Student added successfully" : "Student updated successfully", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });

        builder.setNegativeButton("CANCEL", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }
}
