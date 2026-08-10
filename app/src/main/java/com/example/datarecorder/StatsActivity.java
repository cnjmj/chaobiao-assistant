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
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.DateValidatorPointForward;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class StatsActivity extends AppCompatActivity {
    private long meterId; private DatabaseHelper dbHelper; private Meter meter;
    private Spinner spnMode; private LinearLayout tableContainer;
    private LineChart chartUsage, chartCost;
    private TextView tvDateRange;
    private int currentMode = 0; // 0=按日, 1=按月
    private long rangeStartMs, rangeEndMs;
    private SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_stats);
        meterId = getIntent().getLongExtra("meter_id", -1); if (meterId <= 0) { finish(); return; }
        dbHelper = new DatabaseHelper(this); meter = dbHelper.getMeter(meterId); if (meter == null) { finish(); return; }
        spnMode = findViewById(R.id.spn_mode);
        tableContainer = findViewById(R.id.table_container);
        chartUsage = findViewById(R.id.chart_usage); chartCost = findViewById(R.id.chart_cost);
        tvDateRange = findViewById(R.id.tv_date_range);

        // 默认范围：最近一次记录所在月份的1号 ~ 今天
        initDefaultRange();
        updateDateLabel();

        // 点击日期范围文本，弹出Material日期范围选择器
        tvDateRange.setOnClickListener(v -> showDateRangePicker());

        // 按日/按月
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, new String[]{"按日", "按月"});
        modeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spnMode.setAdapter(modeAdapter);
        spnMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { currentMode = pos; refreshCharts(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        refreshCharts();
    }

    private void showDateRangePicker() {
        MaterialDatePicker.Builder<androidx.core.util.Pair<Long, Long>> builder = MaterialDatePicker.Builder.dateRangePicker();
        builder.setTitleText("选择日期范围");
        builder.setSelection(new androidx.core.util.Pair<>(rangeStartMs, rangeEndMs));
        MaterialDatePicker<androidx.core.util.Pair<Long, Long>> picker = builder.build();
        picker.addOnPositiveButtonClickListener(selection -> {
            Long start = selection.first;
            Long end = selection.second;
            if (start != null && end != null) {
                rangeStartMs = start;
                rangeEndMs = end;
                updateDateLabel();
                refreshCharts();
            }
        });
        picker.show(getSupportFragmentManager(), "DATE_RANGE_PICKER");
    }

    /** 默认范围：最近一次记录所在月份的1号 ~ 今天 */
    private void initDefaultRange() {
        Calendar endCal = Calendar.getInstance();
        endCal.set(Calendar.HOUR_OF_DAY, 23);
        endCal.set(Calendar.MINUTE, 59);
        endCal.set(Calendar.SECOND, 59);
        endCal.set(Calendar.MILLISECOND, 999);
        rangeEndMs = endCal.getTimeInMillis();

        Calendar startCal = Calendar.getInstance();
        List<Record> recs = dbHelper.getRecordsByMeter(meterId);
        if (!recs.isEmpty()) {
            Record latest = recs.get(0);
            startCal.setTimeInMillis(latest.getTimestamp());
            startCal.set(Calendar.DAY_OF_MONTH, 1);
        } else {
            startCal.add(Calendar.DAY_OF_MONTH, -29);
        }
        startCal.set(Calendar.HOUR_OF_DAY, 0);
        startCal.set(Calendar.MINUTE, 0);
        startCal.set(Calendar.SECOND, 0);
        startCal.set(Calendar.MILLISECOND, 0);
        rangeStartMs = startCal.getTimeInMillis();
    }

    private void updateDateLabel() {
        Calendar sc = Calendar.getInstance(); sc.setTimeInMillis(rangeStartMs);
        Calendar ec = Calendar.getInstance(); ec.setTimeInMillis(rangeEndMs);
        tvDateRange.setText(sdfDate.format(sc.getTime()) + " ~ " + sdfDate.format(ec.getTime()));
    }

    private void refreshCharts() {
        if (currentMode == 0) updateDaily(); else updateMonthly();
    }

    private void updateDaily() {
        List<Record> allRecs = dbHelper.getRecordsByMeter(meterId);
        if (allRecs.isEmpty()) return;

        SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        SimpleDateFormat labelFmt = new SimpleDateFormat("MM/dd", Locale.CHINA);

        // 按日期汇总原始数据(受时间范围限制)
        TreeMap<String, Double> rawUsage = new TreeMap<>(), rawCost = new TreeMap<>();
        for (int i = allRecs.size() - 1; i >= 0; i--) {
            Record r = allRecs.get(i);
            if (r.getTimestamp() < rangeStartMs || r.getTimestamp() > rangeEndMs) continue;
            String dateKey = dateFmt.format(r.getTimestamp());
            if (r.getUsageDiff() > 0) { Double p = rawUsage.get(dateKey); rawUsage.put(dateKey, (p != null ? p : 0) + r.getUsageDiff()); }
            if (r.getCostDiff() > 0) { Double p = rawCost.get(dateKey); rawCost.put(dateKey, (p != null ? p : 0) + r.getCostDiff()); }
        }
        if (rawUsage.isEmpty() && rawCost.isEmpty()) return;

        // 生成连续日期列表
        Calendar startCal = Calendar.getInstance(); startCal.setTimeInMillis(rangeStartMs);
        Calendar endCal = Calendar.getInstance(); endCal.setTimeInMillis(rangeEndMs);

        ArrayList<String> allDateKeys = new ArrayList<>();
        ArrayList<String> allLabels = new ArrayList<>();
        Calendar cur = (Calendar) startCal.clone();
        while (!cur.after(endCal)) {
            allDateKeys.add(dateFmt.format(cur.getTime()));
            allLabels.add(labelFmt.format(cur.getTime()));
            cur.add(Calendar.DAY_OF_MONTH, 1);
        }

        // 插值
        ArrayList<Integer> dataIndices = new ArrayList<>();
        for (int i = 0; i < allDateKeys.size(); i++) {
            if (rawUsage.containsKey(allDateKeys.get(i)) || rawCost.containsKey(allDateKeys.get(i))) {
                dataIndices.add(i);
            }
        }
        LinkedHashMap<String, Double> dayUsage = new LinkedHashMap<>(), dayCost = new LinkedHashMap<>();
        for (int i = 0; i < allDateKeys.size(); i++) {
            String dk = allDateKeys.get(i);
            String label = allLabels.get(i);
            if (rawUsage.containsKey(dk) || rawCost.containsKey(dk)) {
                dayUsage.put(label, rawUsage.containsKey(dk) ? rawUsage.get(dk) : 0.0);
                dayCost.put(label, rawCost.containsKey(dk) ? rawCost.get(dk) : 0.0);
            } else {
                dayUsage.put(label, interpolateValue(i, dataIndices, allDateKeys, rawUsage));
                dayCost.put(label, interpolateValue(i, dataIndices, allDateKeys, rawCost));
            }
        }

        buildChartsAndTable(allLabels, dayUsage, dayCost, "日用量趋势 (" + meter.getUnit() + ")", "日费用趋势 (元)");
    }

    /** 插值：首条数据之前按日均分摊，中间线性插值，末条之后保持 */
    private double interpolateValue(int missingIdx, ArrayList<Integer> dataIndices, ArrayList<String> allDateKeys, TreeMap<String, Double> rawData) {
        int prevIdx = -1, nextIdx = -1;
        for (int di : dataIndices) {
            if (di <= missingIdx) prevIdx = di;
            if (di >= missingIdx && nextIdx < 0) nextIdx = di;
        }

        // 在首个数据点之前：按日均分摊
        if (prevIdx < 0 && nextIdx >= 0) {
            double firstVal = valAt(allDateKeys, nextIdx, rawData);
            // 从范围起始到首个数据点（含）的天数
            int daysToFirst = nextIdx + 1;
            return daysToFirst > 0 ? firstVal / daysToFirst : 0.0;
        }
        // 在末尾数据点之后：保持最后值
        if (nextIdx < 0 && prevIdx >= 0) {
            // 也按日均分摊
            double lastVal = valAt(allDateKeys, prevIdx, rawData);
            int daysFromLast = allDateKeys.size() - prevIdx;
            return daysFromLast > 0 ? lastVal / daysFromLast : 0.0;
        }
        if (prevIdx < 0) return 0.0;
        if (prevIdx == nextIdx) return valAt(allDateKeys, prevIdx, rawData);

        // 中间：线性插值
        double prevVal = valAt(allDateKeys, prevIdx, rawData);
        double nextVal = valAt(allDateKeys, nextIdx, rawData);
        int gap = nextIdx - prevIdx;
        return gap == 0 ? prevVal : prevVal + (nextVal - prevVal) * (missingIdx - prevIdx) / gap;
    }

    private double valAt(ArrayList<String> keys, int idx, TreeMap<String, Double> data) {
        String k = keys.get(idx);
        return data.containsKey(k) ? data.get(k) : 0.0;
    }

    private void updateMonthly() {
        List<Record> allRecs = dbHelper.getRecordsByMeter(meterId);
        if (allRecs.isEmpty()) return;
        TreeMap<String, Double> mUsage = new TreeMap<>(), mCost = new TreeMap<>();
        SimpleDateFormat keyFmt = new SimpleDateFormat("yyyy-MM", Locale.CHINA);
        for (int i = allRecs.size() - 1; i >= 0; i--) {
            Record r = allRecs.get(i);
            if (r.getTimestamp() < rangeStartMs || r.getTimestamp() > rangeEndMs) continue;
            String key = keyFmt.format(r.getTimestamp());
            if (r.getUsageDiff() > 0) { Double p = mUsage.get(key); mUsage.put(key, (p != null ? p : 0) + r.getUsageDiff()); }
            if (r.getCostDiff() > 0) { Double p = mCost.get(key); mCost.put(key, (p != null ? p : 0) + r.getCostDiff()); }
        }
        ArrayList<String> labels = new ArrayList<>(mUsage.keySet());
        if (labels.isEmpty()) return;
        buildChartsAndTable(labels, mUsage, mCost, "月用量趋势 (" + meter.getUnit() + ")", "月费用趋势 (元)");
    }

    private void buildChartsAndTable(ArrayList<String> labels, Map<String, Double> usageMap, Map<String, Double> costMap, String usageTitle, String costTitle) {
        double totalU = 0, totalC = 0;
        ArrayList<Entry> uEntries = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            double u = usageMap.containsKey(labels.get(i)) ? usageMap.get(labels.get(i)) : 0;
            uEntries.add(new Entry(i, (float)u)); totalU += u;
        }
        LineDataSet uSet = new LineDataSet(uEntries, usageTitle);
        styleDataSet(uSet, Color.parseColor("#009688"), Color.parseColor("#B2DFDB"), Color.parseColor("#4DB6AC"));
        setupChart(chartUsage, new LineData(uSet), labels, usageTitle);

        ArrayList<Entry> cEntries = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            double c = costMap.containsKey(labels.get(i)) ? costMap.get(labels.get(i)) : 0;
            cEntries.add(new Entry(i, (float)c)); totalC += c;
        }
        LineDataSet cSet = new LineDataSet(cEntries, costTitle);
        styleDataSet(cSet, Color.parseColor("#FF7043"), Color.parseColor("#FFCCBC"), Color.parseColor("#FF8A65"));
        setupChart(chartCost, new LineData(cSet), labels, costTitle);

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

    private void buildTable(ArrayList<String> labels, Map<String,Double> uMap, Map<String,Double> cMap, double totalU, double totalC) {
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
