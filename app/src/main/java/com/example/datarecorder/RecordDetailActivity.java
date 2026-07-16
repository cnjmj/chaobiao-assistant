package com.example.datarecorder;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Calendar;
import java.util.Locale;

public class RecordDetailActivity extends AppCompatActivity {
    private long recordId; private Record record; private Meter meter; private DatabaseHelper dbHelper;
    private TextView tvType, tvTime, tvBalance, tvBalanceLabel, tvReading, tvReadingLabel;
    private TextView tvUsage, tvCost, tvNote, tvNoteLabel;
    private LinearLayout layoutDiff;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_record_detail);
        recordId = getIntent().getLongExtra("record_id", -1); if (recordId <= 0) { finish(); return; }
        dbHelper = new DatabaseHelper(this);
        tvType = findViewById(R.id.tv_type); tvTime = findViewById(R.id.tv_time);
        tvBalance = findViewById(R.id.tv_balance); tvBalanceLabel = findViewById(R.id.tv_balance_label);
        tvReading = findViewById(R.id.tv_reading); tvReadingLabel = findViewById(R.id.tv_reading_label);
        tvUsage = findViewById(R.id.tv_usage); tvCost = findViewById(R.id.tv_cost);
        tvNote = findViewById(R.id.tv_note); tvNoteLabel = findViewById(R.id.tv_note_label);
        layoutDiff = findViewById(R.id.layout_diff);

        findViewById(R.id.btn_edit).setOnClickListener(v -> showEditDialog());
        findViewById(R.id.btn_delete).setOnClickListener(v -> confirmDelete());
        loadRecord();
    }

    @Override protected void onResume() { super.onResume(); loadRecord(); }

    private void loadRecord() {
        record = dbHelper.getRecordById(recordId);
        if (record == null) { Toast.makeText(this, "记录不存在", Toast.LENGTH_SHORT).show(); finish(); return; }
        meter = dbHelper.getMeter(record.getMeterId());
        if (meter == null) { finish(); return; }

        // Type badge
        if (record.isRecharge()) {
            tvType.setText("充值"); tvType.setBackgroundColor(0xFFFF9800);
        } else {
            tvType.setText(meter.getTypeLabel()); tvType.setBackgroundColor(0xFF009688);
        }

        tvTime.setText(record.getFormattedTime());

        if (meter.isPrepaid()) {
            tvBalanceLabel.setText("余额（元）");
            tvBalance.setText(String.format(Locale.CHINA, "%.2f 元", record.getBalance()));
            if (record.getReading() > 0) {
                tvReadingLabel.setVisibility(View.VISIBLE); tvReading.setVisibility(View.VISIBLE);
                tvReadingLabel.setText("表读数（" + meter.getUnit() + "）");
                tvReading.setText(String.format(Locale.CHINA, "%.2f %s", record.getReading(), meter.getUnit()));
            } else { tvReadingLabel.setVisibility(View.GONE); tvReading.setVisibility(View.GONE); }
        } else {
            tvBalanceLabel.setText("当前读数（" + meter.getUnit() + "）");
            tvBalance.setText(String.format(Locale.CHINA, "%.2f %s", record.getBalance(), meter.getUnit()));
            tvReadingLabel.setVisibility(View.GONE); tvReading.setVisibility(View.GONE);
        }

        if (record.getUsageDiff() > Record.EPSILON || record.getCostDiff() > Record.EPSILON) {
            layoutDiff.setVisibility(View.VISIBLE);
            tvUsage.setText(record.getUsageDiff() > Record.EPSILON ? String.format(Locale.CHINA, "%.2f %s", record.getUsageDiff(), meter.getUnit()) : "--");
            tvCost.setText(record.getCostDiff() > Record.EPSILON ? String.format(Locale.CHINA, "%.2f 元", record.getCostDiff()) : "--");
        } else layoutDiff.setVisibility(View.GONE);

        if (record.getNote() != null && !record.getNote().isEmpty()) {
            tvNoteLabel.setVisibility(View.VISIBLE); tvNote.setVisibility(View.VISIBLE);
            tvNoteLabel.setText("备注"); tvNote.setText(record.getNote());
        } else { tvNoteLabel.setVisibility(View.GONE); tvNote.setVisibility(View.GONE); }
    }

    private void showEditDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(50, 40, 50, 10);

        final EditText etBalance = new EditText(this);
        etBalance.setHint(meter.isPrepaid() ? "余额（元）" : "读数（" + meter.getUnit() + "）");
        etBalance.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etBalance.setText(String.format(Locale.CHINA, "%.2f", record.getBalance()));
        layout.addView(etBalance);

        // 表读数编辑（预付费模式且为抄表记录时显示）
        final EditText etReading = new EditText(this);
        final CheckBox cbCumulative = new CheckBox(this);
        if (meter.isPrepaid() && !record.isRecharge()) {
            etReading.setHint("用量（" + meter.getUnit() + "）");
            etReading.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            etReading.setText(record.getReading() > 0 ? String.format(Locale.CHINA, "%.2f", record.getReading()) : "");
            layout.addView(etReading);
            cbCumulative.setText("作为累计值");
            cbCumulative.setTextColor(0xFFFF7043);
            cbCumulative.setChecked(record.isReadingCumulative());
            layout.addView(cbCumulative);
        }

        // 时间戳编辑
        final Calendar editCal = Calendar.getInstance();
        editCal.setTimeInMillis(record.getTimestamp());
        final EditText etDate = new EditText(this);
        etDate.setHint("日期");
        etDate.setFocusable(false); etDate.setClickable(true);
        java.text.SimpleDateFormat sdfDate = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        java.text.SimpleDateFormat sdfTime = new java.text.SimpleDateFormat("HH:mm", Locale.CHINA);
        etDate.setText(sdfDate.format(editCal.getTime()));
        etDate.setOnClickListener(v -> {
            DatePickerDialog dpd = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    editCal.set(Calendar.YEAR, year);
                    editCal.set(Calendar.MONTH, month);
                    editCal.set(Calendar.DAY_OF_MONTH, day);
                    etDate.setText(sdfDate.format(editCal.getTime()));
                }, editCal.get(Calendar.YEAR), editCal.get(Calendar.MONTH), editCal.get(Calendar.DAY_OF_MONTH));
            dpd.show();
        });
        layout.addView(etDate);

        final EditText etTime = new EditText(this);
        etTime.setHint("时间");
        etTime.setFocusable(false); etTime.setClickable(true);
        etTime.setText(sdfTime.format(editCal.getTime()));
        etTime.setOnClickListener(v -> {
            TimePickerDialog tpd = new TimePickerDialog(this,
                (view, hour, minute) -> {
                    editCal.set(Calendar.HOUR_OF_DAY, hour);
                    editCal.set(Calendar.MINUTE, minute);
                    etTime.setText(sdfTime.format(editCal.getTime()));
                }, editCal.get(Calendar.HOUR_OF_DAY), editCal.get(Calendar.MINUTE), true);
            tpd.show();
        });
        layout.addView(etTime);

        final EditText etNote = new EditText(this);
        etNote.setHint("备注"); etNote.setText(record.getNote() != null ? record.getNote() : "");
        layout.addView(etNote);

        new AlertDialog.Builder(this).setTitle("编辑记录").setView(layout)
            .setPositiveButton("保存", (d, w) -> {
                String bs = etBalance.getText().toString().trim();
                if (bs.isEmpty()) { Toast.makeText(this, "请输入数值", Toast.LENGTH_SHORT).show(); return; }
                try {
                    double bal = Double.parseDouble(bs);
                    double rd = 0;
                    boolean cumulative = false;
                    if (meter.isPrepaid() && !record.isRecharge()) {
                        String rs = etReading.getText().toString().trim();
                        if (!rs.isEmpty()) rd = Double.parseDouble(rs);
                        cumulative = cbCumulative.isChecked();
                    } else if (!meter.isPrepaid()) {
                        rd = bal; // 后付费：读数=余额字段
                        cumulative = true; // 后付费默认累计
                    }
                    editCal.set(Calendar.SECOND, 0);
                    editCal.set(Calendar.MILLISECOND, 0);
                    long timestamp = editCal.getTimeInMillis();
                    String note = etNote.getText().toString().trim();
                    dbHelper.updateRecord(recordId, bal, rd, cumulative, timestamp, note);
                    // 编辑后重算diff
                    dbHelper.recalculateDiffs(record.getMeterId());
                    Toast.makeText(this, "已更新", Toast.LENGTH_SHORT).show();
                    loadRecord(); setResult(RESULT_OK);
                } catch (NumberFormatException e) { Toast.makeText(this, "数值格式错误", Toast.LENGTH_SHORT).show(); }
            }).setNegativeButton("取消", null).show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this).setTitle("删除记录").setMessage("确定删除这条记录？删除后不可恢复。")
            .setPositiveButton("删除", (d, w) -> {
                dbHelper.deleteRecord(recordId);
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK); finish();
            }).setNegativeButton("取消", null).show();
    }
}