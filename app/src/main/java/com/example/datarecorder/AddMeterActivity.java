package com.example.datarecorder;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class AddMeterActivity extends AppCompatActivity {
    private EditText etName, etNo; private Spinner spnType, spnBilling; private LinearLayout tiersContainer;
    private List<EditText> etStarts=new ArrayList<>(), etEnds=new ArrayList<>(), etPrices=new ArrayList<>();
    private String selectedType = "electric", selectedBilling = "prepaid";
    private long editId = -1;
    private List<Meter.PriceTier> editTiers = null; // 编辑模式下的原始分段值
    private static final String[] TYPES = {"electric","water","gas","other"};
    private static final String[] LABELS = {"电表","水表","气表","其他"};
    private static final String[] BILLINGS = {"prepaid","postpaid"};
    private static final String[] BILLING_LABELS = {"预付费（按余额）","后付费（按读数）"};

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_add_meter);
        etName = findViewById(R.id.et_name); etNo = findViewById(R.id.et_meter_no);
        spnType = findViewById(R.id.spn_type); spnBilling = findViewById(R.id.spn_billing);
        tiersContainer = findViewById(R.id.tiers_container);

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, LABELS);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnType.setAdapter(typeAdapter);

        ArrayAdapter<String> billingAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, BILLING_LABELS);
        billingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnBilling.setAdapter(billingAdapter);

        // 先设置编辑模式的初始值（不注册监听器，避免回调覆盖）
        editId = getIntent().getLongExtra("edit_id", -1);
        if (editId > 0) {
            DatabaseHelper db = new DatabaseHelper(this); Meter m = db.getMeter(editId);
            if (m != null) {
                etName.setText(m.getName()); etNo.setText(m.getMeterNo() != null ? m.getMeterNo() : "");
                for (int i = 0; i < TYPES.length; i++) if (TYPES[i].equals(m.getMeterType())) { spnType.setSelection(i); break; }
                selectedType = m.getMeterType();
                for (int i = 0; i < BILLINGS.length; i++) if (BILLINGS[i].equals(m.getBillingMode())) { spnBilling.setSelection(i); break; }
                selectedBilling = m.getBillingMode();
                editTiers = m.getPriceTiers();
            }
        }

        // 初始值设完后，再注册监听器
        spnType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                String newType = TYPES[pos];
                if (!newType.equals(selectedType)) {
                    // 类型真正改变时才重建默认分段
                    selectedType = newType;
                    editTiers = null;
                    buildTierRows(null);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        spnBilling.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { selectedBilling = BILLINGS[pos]; }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // 最后构建分段行（编辑模式用原始值，新建用默认值）
        buildTierRows(editTiers);
        findViewById(R.id.btn_add_tier).setOnClickListener(v -> addTierRow(0, -1, 1.0));
        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        findViewById(R.id.btn_cancel).setOnClickListener(v -> finish());
    }

    private void buildTierRows(List<Meter.PriceTier> existing) {
        tiersContainer.removeAllViews(); etStarts.clear(); etEnds.clear(); etPrices.clear();
        Meter temp = new Meter(); temp.setMeterType(selectedType); temp.setDefaultTiers();
        List<Meter.PriceTier> tiers = existing != null ? existing : temp.getPriceTiers();
        for (Meter.PriceTier t : tiers) addTierRow(t.startKwh, t.endKwh, t.price);
    }

    private void addTierRow(double start, double end, double price) {
        View row = getLayoutInflater().inflate(R.layout.item_tier, null);
        EditText etS = row.findViewById(R.id.et_start); etS.setText(start==(long)start ? String.valueOf((long)start) : String.valueOf(start));
        EditText etE = row.findViewById(R.id.et_end); etE.setText(end<0 ? "无穷" : (end==(long)end ? String.valueOf((long)end) : String.valueOf(end)));
        EditText etP = row.findViewById(R.id.et_price); etP.setText(String.format(java.util.Locale.CHINA, "%.4f", price));
        tiersContainer.addView(row); etStarts.add(etS); etEnds.add(etE); etPrices.add(etP);
    }

    private void save() {
        String name = etName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) { Toast.makeText(this, "请输入名称", Toast.LENGTH_SHORT).show(); return; }
        String meterNo = etNo.getText().toString().trim();
        Meter temp = new Meter(); temp.setMeterType(selectedType); temp.setDefaultTiers(); String unit = temp.getUnit();
        List<Meter.PriceTier> tiers = new ArrayList<>();
        for (int i = 0; i < etStarts.size(); i++) {
            double s = safeParse(etStarts.get(i).getText().toString(), 0);
            String eStr = etEnds.get(i).getText().toString();
            double e = eStr.equals("无穷") || eStr.isEmpty() ? -1 : safeParse(eStr, -1);
            double p = safeParse(etPrices.get(i).getText().toString(), 0);
            tiers.add(new Meter.PriceTier(s, e, p));
        }
        DatabaseHelper db = new DatabaseHelper(this);
        long id; if (editId > 0) { db.updateMeter(editId, name, meterNo, selectedType, selectedBilling, unit); id = editId; }
        else id = db.insertMeter(name, meterNo, selectedType, selectedBilling, unit);
        db.saveTiers(id, tiers); setResult(RESULT_OK); finish();
    }
    private double safeParse(String s, double def) { try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; } }
}