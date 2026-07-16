package com.example.datarecorder;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BatchRecordActivity extends AppCompatActivity {
    private DatabaseHelper dbHelper;
    private LinearLayout container;
    private List<Meter> meters;
    private List<View> meterViews = new ArrayList<>();
    private Calendar backdateCal = Calendar.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_batch_record);
        dbHelper = new DatabaseHelper(this);
        container = findViewById(R.id.meter_input_container);
        findViewById(R.id.btn_save).setOnClickListener(v -> saveAll());

        // 补录控件
        CheckBox cbBackdate = findViewById(R.id.cb_backdate);
        LinearLayout layoutBackdate = findViewById(R.id.layout_backdate);
        EditText etBackdateDate = findViewById(R.id.et_backdate_date);
        EditText etBackdateTime = findViewById(R.id.et_backdate_time);

        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm", Locale.CHINA);
        etBackdateDate.setText(sdfDate.format(backdateCal.getTime()));
        etBackdateTime.setText(sdfTime.format(backdateCal.getTime()));

        cbBackdate.setOnCheckedChangeListener((btn, checked) -> {
            layoutBackdate.setVisibility(checked ? View.VISIBLE : View.GONE);
        });

        etBackdateDate.setOnClickListener(v -> {
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

        etBackdateTime.setOnClickListener(v -> {
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

        loadMeters();
    }

    private void loadMeters() {
        meters = dbHelper.getAllMeters();
        container.removeAllViews();
        meterViews.clear();

        if (meters.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("暂无表计，请先添加表计"); tv.setTextSize(16); tv.setTextColor(0xFF999999); tv.setPadding(0, 40, 0, 0);
            container.addView(tv); findViewById(R.id.btn_save).setEnabled(false); return;
        }

        LayoutInflater inflater = getLayoutInflater();
        for (Meter m : meters) {
            View itemView = inflater.inflate(R.layout.item_batch_meter, container, false);

            // Type label
            TextView tvType = itemView.findViewById(R.id.tv_type_label);
            tvType.setText(m.getTypeLabel());
            String mt = m.getMeterType();
            if ("electric".equals(mt)) tvType.setBackgroundColor(0xFF009688);
            else if ("water".equals(mt)) tvType.setBackgroundColor(0xFF2196F3);
            else if ("gas".equals(mt)) tvType.setBackgroundColor(0xFFFF9800);
            else tvType.setBackgroundColor(0xFF9E9E9E);

            // Name
            TextView tvName = itemView.findViewById(R.id.tv_meter_name);
            String nameText = m.getName();
            if (m.getMeterNo() != null && !m.getMeterNo().isEmpty()) nameText += " (" + m.getMeterNo() + ")";
            tvName.setText(nameText);

            // Billing mode
            TextView tvBilling = itemView.findViewById(R.id.tv_billing_mode);
            tvBilling.setText(m.getBillingModeLabel());

            // Layout sections
            LinearLayout layoutPrepaidReading = itemView.findViewById(R.id.layout_prepaid_reading);
            LinearLayout layoutPostpaidReading = itemView.findViewById(R.id.layout_postpaid_reading);
            LinearLayout layoutRecharge = itemView.findViewById(R.id.layout_recharge);
            TextView tvUnitReading = itemView.findViewById(R.id.tv_unit_reading);
            TextView tvUnitMain = itemView.findViewById(R.id.tv_unit_main);
            tvUnitReading.setText(m.getUnit());
            tvUnitMain.setText(m.getUnit());

            // Show correct input section based on mode
            if (m.isPrepaid()) layoutPrepaidReading.setVisibility(View.VISIBLE);
            else layoutPostpaidReading.setVisibility(View.VISIBLE);

            // Action spinner
            Spinner spnAction = itemView.findViewById(R.id.spn_action);
            String[] actionLabels = m.isPrepaid() ? new String[]{"抄表", "充值"} : new String[]{"抄表", "缴费"};
            ArrayAdapter<String> actionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, actionLabels);
            actionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spnAction.setAdapter(actionAdapter);
            spnAction.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    boolean isRecharge = (pos == 1);
                    if (m.isPrepaid()) {
                        layoutPrepaidReading.setVisibility(isRecharge ? View.GONE : View.VISIBLE);
                    } else {
                        layoutPostpaidReading.setVisibility(isRecharge ? View.GONE : View.VISIBLE);
                    }
                    layoutRecharge.setVisibility(isRecharge ? View.VISIBLE : View.GONE);
                }
                @Override public void onNothingSelected(AdapterView<?> p) {}
            });

            // Last record info
            TextView tvLast = itemView.findViewById(R.id.tv_last_info);
            List<Record> recs = dbHelper.getRecordsByMeter(m.getId());
            if (!recs.isEmpty()) {
                Record last = recs.get(0);
                if (m.isPrepaid()) tvLast.setText("上次余额: " + last.getFormattedBalance() + " 元 | " + last.getFormattedTime());
                else tvLast.setText("上次读数: " + last.getFormattedBalance() + " " + m.getUnit() + " | " + last.getFormattedTime());
            } else tvLast.setText("尚无记录");

            container.addView(itemView);
            meterViews.add(itemView);
        }
    }

    private void saveAll() {
        // 获取补录时间
        CheckBox cbBackdate = findViewById(R.id.cb_backdate);
        long timestamp = System.currentTimeMillis();
        if (cbBackdate.isChecked()) {
            backdateCal.set(Calendar.SECOND, 0);
            backdateCal.set(Calendar.MILLISECOND, 0);
            timestamp = backdateCal.getTimeInMillis();
            if (timestamp > System.currentTimeMillis()) {
                Toast.makeText(this, "补录时间不能晚于当前时间", Toast.LENGTH_SHORT).show(); return;
            }
        }

        int saved = 0;
        StringBuilder summary = new StringBuilder();
        String note = ((EditText) findViewById(R.id.et_note)).getText().toString().trim();

        for (int i = 0; i < meters.size(); i++) {
            Meter m = meters.get(i);
            View itemView = meterViews.get(i);
            Spinner spnAction = itemView.findViewById(R.id.spn_action);
            boolean isRecharge = spnAction.getSelectedItemPosition() == 1;

            if (isRecharge) {
                EditText etRecharge = itemView.findViewById(R.id.et_recharge);
                String amtStr = etRecharge.getText().toString().trim();
                if (amtStr.isEmpty()) continue;
                try {
                    double amt = Double.parseDouble(amtStr);
                    Record nr = dbHelper.insertRecharge(m.getId(), amt, note, timestamp); saved++;
                    String label = m.isPrepaid() ? "充值" : "缴费";
                    summary.append(m.getName()).append(" ").append(label).append(": ").append(String.format(Locale.CHINA, "%.2f", amt)).append("元");
                    if (m.isPrepaid()) summary.append(" (新余额").append(nr.getFormattedBalance()).append("元)");
                    summary.append("\n");
                } catch (NumberFormatException e) { Toast.makeText(this, m.getName() + " 金额格式错误", Toast.LENGTH_SHORT).show(); return; }
            } else if (m.isPrepaid()) {
                // 预付费抄表：余额必填，用量可选
                EditText etBal = itemView.findViewById(R.id.et_balance);
                EditText etRd = itemView.findViewById(R.id.et_reading);
                CheckBox cbCumPre = itemView.findViewById(R.id.cb_cumulative_pre);
                String bs = etBal.getText().toString().trim();
                if (bs.isEmpty()) continue;
                try {
                    double bal = Double.parseDouble(bs);
                    double rd = 0;
                    boolean cumulative = cbCumPre.isChecked();
                    String rs = etRd.getText().toString().trim();
                    if (!rs.isEmpty()) rd = Double.parseDouble(rs);
                    Record nr = dbHelper.insertRecord(m.getId(), bal, rd, cumulative, note, timestamp); saved++;
                    summary.append(m.getName()).append(" 余额: ").append(nr.getFormattedBalance()).append("元");
                    if (nr.getCostDiff() > Record.EPSILON) summary.append(" 费用:").append(nr.getFormattedCostDiff()).append("元");
                    if (nr.getUsageDiff() > Record.EPSILON) summary.append(" 用量:").append(nr.getFormattedUsageDiff()).append(m.getUnit());
                    summary.append("\n");
                } catch (NumberFormatException e) { Toast.makeText(this, m.getName() + " 数值格式错误", Toast.LENGTH_SHORT).show(); return; }
            } else {
                // 后付费抄表：读数必填
                EditText etRdMain = itemView.findViewById(R.id.et_reading_main);
                CheckBox cbCumPost = itemView.findViewById(R.id.cb_cumulative_post);
                String rs = etRdMain.getText().toString().trim();
                if (rs.isEmpty()) continue;
                try {
                    double rd = Double.parseDouble(rs);
                    boolean cumulativePost = cbCumPost.isChecked();
                    Record nr = dbHelper.insertRecord(m.getId(), rd, rd, cumulativePost, note, timestamp); saved++;
                    summary.append(m.getName()).append(" 读数: ").append(nr.getFormattedBalance()).append(m.getUnit());
                    if (nr.getUsageDiff() > Record.EPSILON) summary.append(" 用量:").append(nr.getFormattedUsageDiff()).append(m.getUnit()).append(" 费用:").append(nr.getFormattedCostDiff()).append("元");
                    summary.append("\n");
                } catch (NumberFormatException e) { Toast.makeText(this, m.getName() + " 数值格式错误", Toast.LENGTH_SHORT).show(); return; }
            }
        }

        if (saved == 0) { Toast.makeText(this, "请至少输入一个表计的数据", Toast.LENGTH_SHORT).show(); return; }
        String result = "成功录入 " + saved + " 条\n" + summary.toString().trim();
        if (cbBackdate.isChecked()) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
            result += "\n(补录时间: " + sdf.format(new Date(timestamp)) + ")";
        }
        Toast.makeText(this, result, Toast.LENGTH_LONG).show();
        finish();
    }
}
