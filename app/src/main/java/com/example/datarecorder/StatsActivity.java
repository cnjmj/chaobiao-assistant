package com.example.datarecorder;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class StatsActivity extends AppCompatActivity {
    private long meterId; private DatabaseHelper dbHelper; private Meter meter;
    private Spinner spnYear, spnMode; private LinearLayout tableContainer;
    private LineChart chartUsage, chartCost;
    private int currentMode = 0; // 0=按日, 1=按月

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_stats);
        meterId = getIntent().getLongExtra("meter_id", -1); if (meterId <= 0) { finish(); return; }
        dbHelper = new DatabaseHelper(this); meter = dbHelper.getMeter(meterId); if (meter == null) { finish(); return; }
        spnYear = findViewById(R.id.spn_year); spnMode = findViewById(R.id.spn_mode);
        tableContainer = findViewById(R.id.table_container);
        chartUsage = findViewById(R.id.chart_usage); chartCost = findViewById(R.id.chart_cost);

        // 按日/按月 切换
        String[] modes = {"按日", "按月"};
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modes);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnMode.setAdapter(modeAdapter);
        spnMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { currentMode = pos; refreshCharts(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // 年份
        List<Record> allRecs = dbHelper.getRecordsByMeter(meterId);
        int curYear = Calendar.getInstance().get(Calendar.YEAR);
        final ArrayList<String> years = new ArrayList<>(); years.add("全部");
        if (!allRecs.isEmpty()) {
            Calendar cal = Calendar.getInstance(); cal.setTimeInMillis(allRecs.get(allRecs.size()-1).getTimestamp());
            for (int y = curYear; y >= cal.get(Calendar.YEAR); y--) years.add(String.valueOf(y));
        } else years.add(String.valueOf(curYear));
        ArrayAdapter<String> ya = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, years);
        ya.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnYear.setAdapter(ya);
        spnYear.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { refreshCharts(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        refreshCharts();
    }

    private int getSelectedYear() {
        String s = spnYear.getSelectedItem().toString();
        return s.equals("全部") ? -1 : Integer.parseInt(s);
    }

    private void refreshCharts() {
        int year = getSelectedYear();
        if (currentMode == 0) updateDaily(year); else updateMonthly(year);
    }

    private void updateDaily(int year) {
        List<Record> allRecs = dbHelper.getRecordsByMeter(meterId);
        if (allRecs.isEmpty()) return;
        Calendar cal = Calendar.getInstance();
        // 按日汇总: key = "MM/dd"
        TreeMap<String, Double> dayUsage = new TreeMap<>(), dayCost = new TreeMap<>();
        TreeMap<String, String> daySortKey = new TreeMap<>(); // for proper date sorting
        SimpleDateFormat keyFmt = new SimpleDateFormat("MM/dd", Locale.CHINA);
        SimpleDateFormat sortFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

        for (int i = allRecs.size()-1; i >= 0; i--) {
            Record r = allRecs.get(i); cal.setTimeInMillis(r.getTimestamp());
            if (year > 0 && cal.get(Calendar.YEAR) != year) continue;
            String key = keyFmt.format(r.getTimestamp());
            String sortK = sortFmt.format(r.getTimestamp());
            daySortKey.put(sortK, key);
            if (r.getUsageDiff() > 0) { Double p = dayUsage.get(key); dayUsage.put(key, (p!=null?p:0)+r.getUsageDiff()); }
            if (r.getCostDiff() > 0) { Double p = dayCost.get(key); dayCost.put(key, (p!=null?p:0)+r.getCostDiff()); }
        }
        ArrayList<String> labels = new ArrayList<>(); for (String k : daySortKey.values()) if (!labels.contains(k)) labels.add(k);
        if (labels.isEmpty()) return;
        buildChartsAndTable(labels, dayUsage, dayCost, "日用量趋势 (" + meter.getUnit() + ")", "日费用趋势 (元)");
    }

    private void updateMonthly(int year) {
        List<Record> allRecs = dbHelper.getRecordsByMeter(meterId);
        if (allRecs.isEmpty()) return;
        Calendar cal = Calendar.getInstance();
        TreeMap<String, Double> mUsage = new TreeMap<>(), mCost = new TreeMap<>();
        SimpleDateFormat keyFmt = new SimpleDateFormat("yyyy-MM", Locale.CHINA);
        for (int i = allRecs.size()-1; i >= 0; i--) {
            Record r = allRecs.get(i); cal.setTimeInMillis(r.getTimestamp());
            if (year > 0 && cal.get(Calendar.YEAR) != year) continue;
            String key = keyFmt.format(r.getTimestamp());
            if (r.getUsageDiff() > 0) { Double p = mUsage.get(key); mUsage.put(key, (p!=null?p:0)+r.getUsageDiff()); }
            if (r.getCostDiff() > 0) { Double p = mCost.get(key); mCost.put(key, (p!=null?p:0)+r.getCostDiff()); }
        }
        ArrayList<String> labels = new ArrayList<>(mUsage.keySet());
        if (labels.isEmpty()) return;
        buildChartsAndTable(labels, mUsage, mCost, "月用量趋势 (" + meter.getUnit() + ")", "月费用趋势 (元)");
    }

    private void buildChartsAndTable(ArrayList<String> labels, TreeMap<String, Double> usageMap, TreeMap<String, Double> costMap, String usageTitle, String costTitle) {
        double totalU = 0, totalC = 0;
        // 用量曲线
        ArrayList<Entry> uEntries = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            double u = usageMap.containsKey(labels.get(i)) ? usageMap.get(labels.get(i)) : 0;
            uEntries.add(new Entry(i, (float)u)); totalU += u;
        }
        LineDataSet uSet = new LineDataSet(uEntries, usageTitle);
        styleDataSet(uSet, Color.parseColor("#009688"), Color.parseColor("#B2DFDB"), Color.parseColor("#4DB6AC"));
        setupChart(chartUsage, new LineData(uSet), labels, usageTitle);

        // 费用曲线
        ArrayList<Entry> cEntries = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            double c = costMap.containsKey(labels.get(i)) ? costMap.get(labels.get(i)) : 0;
            cEntries.add(new Entry(i, (float)c)); totalC += c;
        }
        LineDataSet cSet = new LineDataSet(cEntries, costTitle);
        styleDataSet(cSet, Color.parseColor("#FF7043"), Color.parseColor("#FFCCBC"), Color.parseColor("#FF8A65"));
        setupChart(chartCost, new LineData(cSet), labels, costTitle);

        // 表格
        buildTable(labels, usageMap, costMap, totalU, totalC);
    }

    private void styleDataSet(LineDataSet set, int mainColor, int holeColor, int fillColor) {
        set.setColor(mainColor); set.setCircleColor(mainColor); set.setCircleHoleColor(holeColor);
        set.setCircleRadius(4f); set.setCircleHoleRadius(2f); set.setLineWidth(2.5f);
        set.setValueTextSize(10f); set.setValueTextColor(Color.parseColor("#333333"));
        set.setDrawFilled(true); set.setFillColor(fillColor); set.setFillAlpha(40);
    }

    private void setupChart(LineChart chart, LineData data, ArrayList<String> labels, String desc) {
        chart.setData(data); chart.getDescription().setText(desc); chart.getDescription().setTextSize(12f); chart.getDescription().setTextColor(Color.parseColor("#666666"));
        chart.setTouchEnabled(true); chart.setDragEnabled(true); chart.setScaleEnabled(false); chart.setPinchZoom(false);
        chart.setDrawGridBackground(false); chart.setExtraOffsets(10,10,20,10);
        XAxis xl = chart.getXAxis(); xl.setPosition(XAxis.XAxisPosition.BOTTOM);
        xl.setValueFormatter(new IndexAxisValueFormatter(labels)); xl.setGranularity(1f);
        xl.setTextColor(Color.parseColor("#666666")); xl.setTextSize(11f); xl.setDrawGridLines(false); xl.setDrawAxisLine(true); xl.setAxisLineColor(Color.parseColor("#CCCCCC"));
        YAxis yl = chart.getAxisLeft(); yl.setTextColor(Color.parseColor("#666666")); yl.setTextSize(11f);
        yl.setDrawGridLines(true); yl.setGridColor(Color.parseColor("#EEEEEE")); yl.setDrawAxisLine(true); yl.setAxisLineColor(Color.parseColor("#CCCCCC")); yl.setAxisMinimum(0f);
        chart.getAxisRight().setEnabled(false);
        Legend l = chart.getLegend(); l.setTextColor(Color.parseColor("#333333")); l.setTextSize(12f); l.setForm(Legend.LegendForm.LINE);
        chart.animateX(800); chart.invalidate();
    }

    private void buildTable(ArrayList<String> labels, TreeMap<String,Double> uMap, TreeMap<String,Double> cMap, double totalU, double totalC) {
        tableContainer.removeAllViews();
        View header = getLayoutInflater().inflate(R.layout.item_table_row, null);
        String periodLabel = currentMode == 0 ? "日期" : "月份";
        ((TextView)header.findViewById(R.id.tv_col1)).setText(periodLabel);
        ((TextView)header.findViewById(R.id.tv_col2)).setText("用量(" + meter.getUnit() + ")");
        ((TextView)header.findViewById(R.id.tv_col3)).setText("费用(元)");
        header.setBackgroundColor(Color.parseColor("#E0E0E0")); tableContainer.addView(header);
        for (String m : labels) {
            View row = getLayoutInflater().inflate(R.layout.item_table_row, null);
            ((TextView)row.findViewById(R.id.tv_col1)).setText(m);
            double u = uMap.containsKey(m) ? uMap.get(m) : 0; double c = cMap.containsKey(m) ? cMap.get(m) : 0;
            ((TextView)row.findViewById(R.id.tv_col2)).setText(String.format(Locale.CHINA, "%.2f", u));
            ((TextView)row.findViewById(R.id.tv_col3)).setText(String.format(Locale.CHINA, "%.2f", c));
            tableContainer.addView(row);
        }
        View total = getLayoutInflater().inflate(R.layout.item_table_row, null);
        ((TextView)total.findViewById(R.id.tv_col1)).setText("合计"); ((TextView)total.findViewById(R.id.tv_col1)).setTypeface(null, android.graphics.Typeface.BOLD);
        ((TextView)total.findViewById(R.id.tv_col2)).setText(String.format(Locale.CHINA, "%.2f", totalU)); ((TextView)total.findViewById(R.id.tv_col2)).setTypeface(null, android.graphics.Typeface.BOLD);
        ((TextView)total.findViewById(R.id.tv_col3)).setText(String.format(Locale.CHINA, "%.2f", totalC)); ((TextView)total.findViewById(R.id.tv_col3)).setTypeface(null, android.graphics.Typeface.BOLD);
        total.setBackgroundColor(Color.parseColor("#E8F5E9")); tableContainer.addView(total);
    }
}