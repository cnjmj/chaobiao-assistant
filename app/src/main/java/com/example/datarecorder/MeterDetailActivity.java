package com.example.datarecorder;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MeterDetailActivity extends AppCompatActivity {
    private static final int REQ_DETAIL = 1001;
    private long meterId; private Meter meter; private DatabaseHelper dbHelper;
    private RecyclerView recyclerView; private RecordAdapter adapter;
    private TextView tvSummary, tvInfo, tvEmpty; private FloatingActionButton fabAdd;
    private List<Record> recordList = new ArrayList<>();
    private List<Meter> allMeters = new ArrayList<>();
    private int currentMeterIndex = 0;
    private GestureDetector gestureDetector;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_meter_detail);
        meterId = getIntent().getLongExtra("meter_id", -1); if (meterId <= 0) { finish(); return; }
        dbHelper = new DatabaseHelper(this);
        recyclerView = findViewById(R.id.recycler_view); tvSummary = findViewById(R.id.tv_summary);
        tvInfo = findViewById(R.id.tv_meter_info); tvEmpty = findViewById(R.id.tv_empty); fabAdd = findViewById(R.id.fab_add);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecordAdapter(recordList, "kWh", true);
        // 点击查看详情
        adapter.setOnItemClick((r, pos) -> {
            Intent intent = new Intent(this, RecordDetailActivity.class);
            intent.putExtra("record_id", r.getId());
            startActivityForResult(intent, REQ_DETAIL);
        });
        // 长按弹出菜单
        adapter.setOnItemLongClick((r, pos) -> {
            String[] items = {"查看详情", "编辑", "删除"};
            new AlertDialog.Builder(this).setTitle("操作").setItems(items, (d, which) -> {
                if (which == 0) {
                    Intent intent = new Intent(this, RecordDetailActivity.class);
                    intent.putExtra("record_id", r.getId());
                    startActivityForResult(intent, REQ_DETAIL);
                } else if (which == 1) {
                    showEditDialog(r);
                } else if (which == 2) {
                    confirmDelete(r);
                }
            }).show();
        });
        recyclerView.setAdapter(adapter);
        fabAdd.setOnClickListener(v -> showAddDialog());
        findViewById(R.id.btn_stats).setOnClickListener(v -> startActivity(new Intent(this, StatsActivity.class).putExtra("meter_id", meterId)));
        findViewById(R.id.btn_edit).setOnClickListener(v -> startActivity(new Intent(this, AddMeterActivity.class).putExtra("edit_id", meterId)));
        findViewById(R.id.btn_export).setOnClickListener(v -> exportRecords());

        // 手势检测：左右滑动切换表计
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY = 100;
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > SWIPE_THRESHOLD && Math.abs(vx) > SWIPE_VELOCITY) {
                    if (dx > 0) {
                        // 向右滑 → 上一个表计
                        switchMeter(-1);
                    } else {
                        // 向左滑 → 下一个表计
                        switchMeter(1);
                    }
                    return true;
                }
                return false;
            }
        });
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        gestureDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    /** 切换到上一个/下一个表计 */
    private void switchMeter(int direction) {
        if (allMeters.size() <= 1) {
            Toast.makeText(this, "没有其他表计", Toast.LENGTH_SHORT).show();
            return;
        }
        int newIndex = currentMeterIndex + direction;
        if (newIndex < 0 || newIndex >= allMeters.size()) {
            Toast.makeText(this, direction < 0 ? "已是第一个表计" : "已是最后一个表计", Toast.LENGTH_SHORT).show();
            return;
        }
        currentMeterIndex = newIndex;
        meterId = allMeters.get(newIndex).getId();
        meter = dbHelper.getMeter(meterId);
        if (meter == null) { finish(); return; }
        updateMeterInfo();
        // 重新创建adapter以更新单位
        adapter = new RecordAdapter(recordList, meter.getUnit(), meter.isPrepaid());
        adapter.setOnItemClick((r, pos) -> {
            Intent intent = new Intent(this, RecordDetailActivity.class);
            intent.putExtra("record_id", r.getId());
            startActivityForResult(intent, REQ_DETAIL);
        });
        adapter.setOnItemLongClick((r, pos) -> {
            String[] items = {"查看详情", "编辑", "删除"};
            new AlertDialog.Builder(this).setTitle("操作").setItems(items, (d, which) -> {
                if (which == 0) { Intent intent = new Intent(this, RecordDetailActivity.class); intent.putExtra("record_id", r.getId()); startActivityForResult(intent, REQ_DETAIL); }
                else if (which == 1) showEditDialog(r);
                else if (which == 2) confirmDelete(recordList.get(pos));
            }).show();
        });
        recyclerView.setAdapter(adapter);
        loadRecords();
        Toast.makeText(this, meter.getName() + " (" + (newIndex + 1) + "/" + allMeters.size() + ")", Toast.LENGTH_SHORT).show();
    }

    private void updateMeterInfo() {
        if (meter == null) return;
        String info = meter.getName() + " | " + meter.getTypeLabel() + " | " + meter.getBillingModeLabel();
        if (meter.getMeterNo() != null && !meter.getMeterNo().isEmpty()) info += " | 编号 " + meter.getMeterNo();
        info += " | 单位 " + meter.getUnit();
        if (allMeters.size() > 1) info += " | " + (currentMeterIndex + 1) + "/" + allMeters.size();
        tvInfo.setText(info);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(meter.getName());
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DETAIL && resultCode == RESULT_OK) loadRecords();
    }

    @Override protected void onResume() {
        super.onResume();
        meter = dbHelper.getMeter(meterId); if (meter == null) { finish(); return; }
        // 加载所有表计并定位当前索引（用于滑动切换）
        allMeters = dbHelper.getAllMeters();
        currentMeterIndex = 0;
        for (int i = 0; i < allMeters.size(); i++) {
            if (allMeters.get(i).getId() == meterId) { currentMeterIndex = i; break; }
        }
        updateMeterInfo();
        adapter = new RecordAdapter(recordList, meter.getUnit(), meter.isPrepaid());
        adapter.setOnItemClick((r, pos) -> {
            Intent intent = new Intent(this, RecordDetailActivity.class);
            intent.putExtra("record_id", r.getId());
            startActivityForResult(intent, REQ_DETAIL);
        });
        adapter.setOnItemLongClick((r, pos) -> {
            String[] items = {"查看详情", "编辑", "删除"};
            new AlertDialog.Builder(this).setTitle("操作").setItems(items, (d, which) -> {
                if (which == 0) { Intent intent = new Intent(this, RecordDetailActivity.class); intent.putExtra("record_id", r.getId()); startActivityForResult(intent, REQ_DETAIL); }
                else if (which == 1) showEditDialog(r);
                else if (which == 2) confirmDelete(recordList.get(pos));
            }).show();
        });
        recyclerView.setAdapter(adapter); loadRecords();
    }

    private void loadRecords() {
        // 仅在数据变更时重算diff（脏标记优化）
        if (dbHelper.isDirty(meterId)) {
            dbHelper.recalculateDiffs(meterId);
        }
        recordList = dbHelper.getRecordsByMeter(meterId); adapter.updateData(recordList);
        tvEmpty.setVisibility(recordList.isEmpty() ? View.VISIBLE : View.GONE);
        tvSummary.setVisibility(recordList.isEmpty() ? View.GONE : View.VISIBLE);
        if (!recordList.isEmpty()) updateSummary();
    }

    private void updateSummary() {
        Calendar cal = Calendar.getInstance(); cal.set(Calendar.DAY_OF_MONTH,1); cal.set(Calendar.HOUR_OF_DAY,0); cal.set(Calendar.MINUTE,0); cal.set(Calendar.SECOND,0); cal.set(Calendar.MILLISECOND,0);
        List<Record> mr = dbHelper.getRecordsByRange(meterId, cal.getTimeInMillis(), System.currentTimeMillis());
        double mu=0, mc=0; for (Record r : mr) { if(r.getUsageDiff()>0) mu+=r.getUsageDiff(); if(r.getCostDiff()>0) mc+=r.getCostDiff(); }
        Record latest = recordList.get(0);
        if (meter.isPrepaid()) {
            tvSummary.setText(String.format(Locale.CHINA, "余额: %.2f元 | 本月费用: %.2f元 | 用量: %.2f%s | 共%d条", latest.getBalance(), mc, mu, meter.getUnit(), recordList.size()));
        } else {
            tvSummary.setText(String.format(Locale.CHINA, "读数: %.2f%s | 本月: %.2f%s / %.2f元 | 共%d条", latest.getBalance(), meter.getUnit(), mu, meter.getUnit(), mc, recordList.size()));
        }
    }

    // 编辑记录
    private void showEditDialog(Record r) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(50, 40, 50, 10);
        final EditText etBalance = new EditText(this);
        etBalance.setHint(meter.isPrepaid() ? "余额（元）" : "读数（" + meter.getUnit() + "）");
        etBalance.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etBalance.setText(String.format(Locale.CHINA, "%.2f", r.getBalance()));
        layout.addView(etBalance);

        // 表读数编辑
        final EditText etReading = new EditText(this);
        final CheckBox cbCumulative = new CheckBox(this);
        if (meter.isPrepaid() && !r.isRecharge()) {
            etReading.setHint("用量（" + meter.getUnit() + "）");
            etReading.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            etReading.setText(r.getReading() > 0 ? String.format(Locale.CHINA, "%.2f", r.getReading()) : "");
            layout.addView(etReading);
            cbCumulative.setText("作为累计值");
            cbCumulative.setTextColor(0xFFFF7043);
            cbCumulative.setChecked(r.isReadingCumulative());
            layout.addView(cbCumulative);
        }

        // 时间戳编辑
        final Calendar editCal = Calendar.getInstance();
        editCal.setTimeInMillis(r.getTimestamp());
        final EditText etDate = new EditText(this);
        etDate.setHint("日期"); etDate.setFocusable(false); etDate.setClickable(true);
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm", Locale.CHINA);
        etDate.setText(sdfDate.format(editCal.getTime()));
        etDate.setOnClickListener(v -> {
            DatePickerDialog dpd = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    editCal.set(Calendar.YEAR, year); editCal.set(Calendar.MONTH, month);
                    editCal.set(Calendar.DAY_OF_MONTH, day);
                    etDate.setText(sdfDate.format(editCal.getTime()));
                }, editCal.get(Calendar.YEAR), editCal.get(Calendar.MONTH), editCal.get(Calendar.DAY_OF_MONTH));
            dpd.show();
        });
        layout.addView(etDate);

        final EditText etTime = new EditText(this);
        etTime.setHint("时间"); etTime.setFocusable(false); etTime.setClickable(true);
        etTime.setText(sdfTime.format(editCal.getTime()));
        etTime.setOnClickListener(v -> {
            TimePickerDialog tpd = new TimePickerDialog(this,
                (view, hour, minute) -> {
                    editCal.set(Calendar.HOUR_OF_DAY, hour); editCal.set(Calendar.MINUTE, minute);
                    etTime.setText(sdfTime.format(editCal.getTime()));
                }, editCal.get(Calendar.HOUR_OF_DAY), editCal.get(Calendar.MINUTE), true);
            tpd.show();
        });
        layout.addView(etTime);

        final EditText etNote = new EditText(this);
        etNote.setHint("备注"); etNote.setText(r.getNote() != null ? r.getNote() : "");
        layout.addView(etNote);

        new AlertDialog.Builder(this).setTitle("编辑记录").setView(layout)
            .setPositiveButton("保存", (d, w) -> {
                String bs = etBalance.getText().toString().trim();
                if (bs.isEmpty()) { Toast.makeText(this, "请输入数值", Toast.LENGTH_SHORT).show(); return; }
                try {
                    double bal = Double.parseDouble(bs);
                    double rd = 0;
                    boolean cumulative = false;
                    if (meter.isPrepaid() && !r.isRecharge()) {
                        String rs = etReading.getText().toString().trim();
                        if (!rs.isEmpty()) rd = Double.parseDouble(rs);
                        cumulative = cbCumulative.isChecked();
                    } else if (!meter.isPrepaid()) {
                        rd = bal;
                        cumulative = true;
                    }
                    editCal.set(Calendar.SECOND, 0); editCal.set(Calendar.MILLISECOND, 0);
                    long timestamp = editCal.getTimeInMillis();
                    dbHelper.updateRecord(r.getId(), bal, rd, cumulative, timestamp, etNote.getText().toString().trim());
                    dbHelper.recalculateDiffs(meterId);
                    Toast.makeText(this, "已更新", Toast.LENGTH_SHORT).show(); loadRecords();
                } catch (NumberFormatException e) { Toast.makeText(this, "数值格式错误", Toast.LENGTH_SHORT).show(); }
            }).setNegativeButton("取消", null).show();
    }

    private void confirmDelete(Record r) {
        new AlertDialog.Builder(this).setTitle("删除记录").setMessage("确定删除这条记录？删除后不可恢复。")
            .setPositiveButton("删除", (d, w) -> {
                dbHelper.deleteRecord(r.getId());
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show(); loadRecords();
            }).setNegativeButton("取消", null).show();
    }

    // 导出记录为CSV
    private void exportRecords() {
        if (recordList.isEmpty()) { Toast.makeText(this, "暂无记录可导出", Toast.LENGTH_SHORT).show(); return; }
        try {
            File exportDir = new File(getExternalFilesDir(null), "export");
            if (!exportDir.exists()) exportDir.mkdirs();
            String dateStr = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
            String fileName = meter.getName() + "_" + dateStr + ".csv";
            File file = new File(exportDir, fileName);

            FileWriter fw = new FileWriter(file);
            // Header
            String balCol = meter.isPrepaid() ? "余额(元)" : "读数(" + meter.getUnit() + ")";
            fw.write("时间,类型," + balCol + ",用量(" + meter.getUnit() + "),费用(元),备注\n");
            // Data (正序：从旧到新)
            List<Record> sorted = new ArrayList<>(recordList);
            Collections.reverse(sorted);
            for (Record r : sorted) {
                String type = r.isRecharge() ? "充值" : "抄表";
                String bal = String.format(Locale.CHINA, "%.2f", r.getBalance());
                String usage = r.getUsageDiff() > Record.EPSILON ? String.format(Locale.CHINA, "%.2f", r.getUsageDiff()) : "";
                String cost = r.getCostDiff() > Record.EPSILON ? String.format(Locale.CHINA, "%.2f", r.getCostDiff()) : "";
                if (r.isRecharge()) cost = String.format(Locale.CHINA, "%.2f", r.getRechargeAmount());
                String note = r.getNote() != null ? r.getNote().replace(",", "，") : "";
                fw.write(r.getFormattedTime() + "," + type + "," + bal + "," + usage + "," + cost + "," + note + "\n");
            }
            fw.close();

            // Share
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/csv");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "导出记录到"));
            Toast.makeText(this, "已生成 " + fileName, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showAddDialog() {
        View dv = getLayoutInflater().inflate(R.layout.dialog_add_record, null);
        Spinner spnType = dv.findViewById(R.id.spn_record_type);
        EditText etBalance = dv.findViewById(R.id.et_balance);
        EditText etReading = dv.findViewById(R.id.et_reading);
        EditText etReadingMain = dv.findViewById(R.id.et_reading_main);
        EditText etRecharge = dv.findViewById(R.id.et_recharge);
        EditText etNote = dv.findViewById(R.id.et_note);
        TextView tvHint = dv.findViewById(R.id.tv_hint);
        TextView tvReadingToggle = dv.findViewById(R.id.tv_reading_toggle);
        TextInputLayout tilReadingOptional = dv.findViewById(R.id.til_reading_optional);
        TextInputLayout tilRecharge = dv.findViewById(R.id.til_recharge);
        LinearLayout layoutPrepaid = dv.findViewById(R.id.layout_prepaid);
        LinearLayout layoutPostpaid = dv.findViewById(R.id.layout_postpaid);
        CheckBox cbBackdate = dv.findViewById(R.id.cb_backdate);
        CheckBox cbReadingCumulative = dv.findViewById(R.id.cb_reading_cumulative);
        CheckBox cbReadingCumulativePost = dv.findViewById(R.id.cb_reading_cumulative_post);
        LinearLayout layoutBackdate = dv.findViewById(R.id.layout_backdate);
        EditText etBackdateDate = dv.findViewById(R.id.et_backdate_date);
        EditText etBackdateTime = dv.findViewById(R.id.et_backdate_time);

        // 补录时间默认为当前时间
        Calendar backdateCal = Calendar.getInstance();
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm", Locale.CHINA);
        etBackdateDate.setText(sdfDate.format(backdateCal.getTime()));
        etBackdateTime.setText(sdfTime.format(backdateCal.getTime()));

        // 补录开关
        cbBackdate.setOnCheckedChangeListener((btn, checked) -> {
            layoutBackdate.setVisibility(checked ? View.VISIBLE : View.GONE);
        });

        // 日期选择器
        etBackdateDate.setOnClickListener(v2 -> {
            DatePickerDialog dpd = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    backdateCal.set(Calendar.YEAR, year);
                    backdateCal.set(Calendar.MONTH, month);
                    backdateCal.set(Calendar.DAY_OF_MONTH, day);
                    etBackdateDate.setText(sdfDate.format(backdateCal.getTime()));
                },
                backdateCal.get(Calendar.YEAR),
                backdateCal.get(Calendar.MONTH),
                backdateCal.get(Calendar.DAY_OF_MONTH));
            dpd.show();
        });

        // 时间选择器
        etBackdateTime.setOnClickListener(v2 -> {
            TimePickerDialog tpd = new TimePickerDialog(this,
                (view, hour, minute) -> {
                    backdateCal.set(Calendar.HOUR_OF_DAY, hour);
                    backdateCal.set(Calendar.MINUTE, minute);
                    etBackdateTime.setText(sdfTime.format(backdateCal.getTime()));
                },
                backdateCal.get(Calendar.HOUR_OF_DAY),
                backdateCal.get(Calendar.MINUTE), true);
            tpd.show();
        });

        if (meter.isPrepaid()) {
            layoutPrepaid.setVisibility(View.VISIBLE); layoutPostpaid.setVisibility(View.GONE);
            tvReadingToggle.setVisibility(View.VISIBLE); tilReadingOptional.setVisibility(View.GONE);
            cbReadingCumulative.setVisibility(View.GONE);
            tvReadingToggle.setOnClickListener(v2 -> {
                if (tilReadingOptional.getVisibility() == View.GONE) {
                    tilReadingOptional.setVisibility(View.VISIBLE);
                    cbReadingCumulative.setVisibility(View.VISIBLE);
                    tvReadingToggle.setText("收起用量 ^");
                } else {
                    tilReadingOptional.setVisibility(View.GONE);
                    cbReadingCumulative.setVisibility(View.GONE);
                    cbReadingCumulative.setChecked(false);
                    tvReadingToggle.setText("同时录入用量 >");
                }
            });
        } else { layoutPrepaid.setVisibility(View.GONE); layoutPostpaid.setVisibility(View.VISIBLE); }

        String[] typeLabels = meter.isPrepaid() ? new String[]{"抄表", "充值"} : new String[]{"抄表", "缴费"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, typeLabels);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnType.setAdapter(typeAdapter);
        final boolean[] isRecharge = {false};
        spnType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                isRecharge[0] = (pos == 1);
                layoutPrepaid.setVisibility(!isRecharge[0] && meter.isPrepaid() ? View.VISIBLE : View.GONE);
                layoutPostpaid.setVisibility(!isRecharge[0] && !meter.isPrepaid() ? View.VISIBLE : View.GONE);
                tilRecharge.setVisibility(isRecharge[0] ? View.VISIBLE : View.GONE);
                if (!isRecharge[0] && meter.isPrepaid()) tvReadingToggle.setVisibility(View.VISIBLE);
                else tvReadingToggle.setVisibility(View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        if (!recordList.isEmpty()) {
            Record last = recordList.get(0);
            if (meter.isPrepaid()) tvHint.setText("上次余额: " + last.getFormattedBalance() + " 元 (" + last.getFormattedTime() + ")");
            else tvHint.setText("上次读数: " + last.getFormattedBalance() + " " + meter.getUnit() + " (" + last.getFormattedTime() + ")");
        } else tvHint.setText("这是第一条记录");
        tvHint.setVisibility(View.VISIBLE);

        new AlertDialog.Builder(this).setTitle("录入数据").setView(dv)
            .setPositiveButton("保存", (d, w) -> {
                String noteStr = etNote.getText().toString().trim();
                long timestamp = System.currentTimeMillis();
                if (cbBackdate.isChecked()) {
                    backdateCal.set(Calendar.SECOND, 0);
                    backdateCal.set(Calendar.MILLISECOND, 0);
                    timestamp = backdateCal.getTimeInMillis();
                    if (timestamp > System.currentTimeMillis()) {
                        Toast.makeText(this, "补录时间不能晚于当前时间", Toast.LENGTH_SHORT).show(); return;
                    }
                }

                if (isRecharge[0]) {
                    String amtStr = etRecharge.getText().toString().trim();
                    if (amtStr.isEmpty()) { Toast.makeText(this, "请输入金额", Toast.LENGTH_SHORT).show(); return; }
                    try {
                        double amt = Double.parseDouble(amtStr);
                        Record nr = dbHelper.insertRecharge(meterId, amt, noteStr, timestamp);
                        String msg = meter.isPrepaid() ? "充值成功: " + String.format(Locale.CHINA, "%.2f", amt) + "元\n新余额: " + nr.getFormattedBalance() + " 元" : "缴费成功: " + String.format(Locale.CHINA, "%.2f", amt) + "元";
                        if (cbBackdate.isChecked()) msg += "\n(补录时间: " + sdfDate.format(new Date(timestamp)) + " " + sdfTime.format(new Date(timestamp)) + ")";
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); loadRecords();
                    } catch (NumberFormatException e) { Toast.makeText(this, "数值格式错误", Toast.LENGTH_SHORT).show(); }
                } else {
                    if (meter.isPrepaid()) {
                        String bs = etBalance.getText().toString().trim();
                        if (bs.isEmpty()) { Toast.makeText(this, "请输入余额", Toast.LENGTH_SHORT).show(); return; }
                        try {
                            double bal = Double.parseDouble(bs); double rd = 0;
                            boolean cumulative = cbReadingCumulative.isChecked();
                            String rs = etReading.getText().toString().trim();
                            if (!rs.isEmpty()) rd = Double.parseDouble(rs);
                            Record nr = dbHelper.insertRecord(meterId, bal, rd, cumulative, noteStr, timestamp);
                            String msg = "余额: " + nr.getFormattedBalance() + " 元";
                            if (nr.getCostDiff() > Record.EPSILON) msg += "\n费用: " + nr.getFormattedCostDiff() + " 元";
                            if (nr.getUsageDiff() > Record.EPSILON) msg += "\n用量: " + nr.getFormattedUsageDiff() + " " + meter.getUnit();
                            if (cbBackdate.isChecked()) msg += "\n(补录时间: " + sdfDate.format(new Date(timestamp)) + " " + sdfTime.format(new Date(timestamp)) + ")";
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); loadRecords();
                        } catch (NumberFormatException e) { Toast.makeText(this, "数值格式错误", Toast.LENGTH_SHORT).show(); }
                    } else {
                        String rs = etReadingMain.getText().toString().trim();
                        if (rs.isEmpty()) { Toast.makeText(this, "请输入读数", Toast.LENGTH_SHORT).show(); return; }
                        try {
                            double rd = Double.parseDouble(rs);
                            boolean cumulativePost = cbReadingCumulativePost.isChecked();
                            Record nr = dbHelper.insertRecord(meterId, rd, rd, cumulativePost, noteStr, timestamp);
                            String msg = "读数: " + nr.getFormattedBalance() + " " + meter.getUnit();
                            if (nr.getUsageDiff() > Record.EPSILON) msg += "\n用量: " + nr.getFormattedUsageDiff() + " " + meter.getUnit() + "\n费用: " + nr.getFormattedCostDiff() + " 元";
                            if (cbBackdate.isChecked()) msg += "\n(补录时间: " + sdfDate.format(new Date(timestamp)) + " " + sdfTime.format(new Date(timestamp)) + ")";
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); loadRecords();
                        } catch (NumberFormatException e) { Toast.makeText(this, "数值格式错误", Toast.LENGTH_SHORT).show(); }
                    }
                }
            }).setNegativeButton("取消", null).show();
    }
}