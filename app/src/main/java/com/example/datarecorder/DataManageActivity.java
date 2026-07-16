package com.example.datarecorder;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DataManageActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private TextView tvSummary;

    private ActivityResultLauncher<Intent> restoreDbLauncher;
    private ActivityResultLauncher<Intent> importCsvLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    private static final String BACKUP_DIR = "backup";
    private static final String EXPORT_DIR = "export";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_manage);

        dbHelper = new DatabaseHelper(this);
        tvSummary = findViewById(R.id.tv_data_summary);

        // 权限请求
        requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (!granted) {
                showToast("存储权限被拒绝，部分功能可能无法使用");
                }
            });

        // 恢复数据库
        restoreDbLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) restoreDatabase(uri);
                }
            });

        // 导入CSV
        importCsvLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) importCsv(uri);
                }
            });

        findViewById(R.id.btn_backup_db).setOnClickListener(v -> confirmBackup());
        findViewById(R.id.btn_restore_db).setOnClickListener(v -> confirmRestore());
        findViewById(R.id.btn_export_csv).setOnClickListener(v -> exportCsv());
        findViewById(R.id.btn_import_csv).setOnClickListener(v -> pickCsvFile());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("数据管理");
        }

        loadSummary();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void ensureStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
    }

    // ===== 数据概览 =====
    private void loadSummary() {
        int meterCount = dbHelper.getAllMeters().size();
        int recordCount = 0;
        for (Meter m : dbHelper.getAllMeters()) {
            recordCount += dbHelper.getRecordCount(m.getId());
        }
        File dbFile = getDatabasePath("meter_recorder.db");
        long dbSize = dbFile.exists() ? dbFile.length() : 0;
        String sizeStr = dbSize > 1024 * 1024
            ? String.format(Locale.getDefault(), "%.1f MB", dbSize / 1024.0 / 1024.0)
            : String.format(Locale.getDefault(), "%.1f KB", dbSize / 1024.0);

        tvSummary.setText(String.format(Locale.getDefault(),
            "表计数量：%d 个\n记录总数：%d 条\n数据库大小：%s\n数据库版本：v%d",
            meterCount, recordCount, sizeStr, DatabaseHelper.DB_VERSION));
    }

    // ===== 备份数据库 =====
    private void confirmBackup() {
        new AlertDialog.Builder(this)
            .setTitle("备份数据库")
            .setMessage("将数据库备份到应用专有目录，并通过分享发送。\n备份包含所有表计和记录数据。")
            .setPositiveButton("备份并分享", (d, w) -> backupDatabase())
            .setNegativeButton("取消", null)
            .show();
    }

    private void backupDatabase() {
        try {
            File dbFile = getDatabasePath("meter_recorder.db");
            if (!dbFile.exists()) {
                showToast("数据库文件不存在");
                return;
            }

            // 备份到 app 专有目录（无需存储权限）
            File backupDir = getBackupDir();
            if (backupDir == null) {
                showToast("无法创建备份目录，请检查存储权限");
                return;
            }
            if (!backupDir.exists()) backupDir.mkdirs();
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File backupFile = new File(backupDir, "抄表助手_备份_" + timeStamp + ".db");

            copyFile(dbFile, backupFile);

            // 通过分享发送
            Uri uri = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", backupFile);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/octet-stream");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "分享备份文件"));

            showToast("备份成功！请通过分享保存到安全位置\n文件：" + backupFile.getName());
        } catch (Exception e) {
            String msg = getChineseErrorMsg(e);
            showToast("备份失败：" + msg);
        }
    }

    // ===== 恢复数据库 =====
    private void confirmRestore() {
        new AlertDialog.Builder(this)
            .setTitle("恢复数据库")
            .setMessage("恢复将替换当前所有数据，此操作不可撤销！\n建议先备份当前数据。")
            .setPositiveButton("选择文件", (d, w) -> pickDbFile())
            .setNegativeButton("取消", null)
            .show();
    }

    private void pickDbFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        restoreDbLauncher.launch(Intent.createChooser(intent, "选择备份文件"));
    }

    private void restoreDatabase(Uri uri) {
        try {
            File dbFile = getDatabasePath("meter_recorder.db");
            File walFile = new File(dbFile.getParent(), "meter_recorder.db-wal");
            File shmFile = new File(dbFile.getParent(), "meter_recorder.db-shm");

            dbHelper.close();

            if (walFile.exists()) walFile.delete();
            if (shmFile.exists()) shmFile.delete();

            InputStream is = getContentResolver().openInputStream(uri);
            FileOutputStream fos = new FileOutputStream(dbFile);
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
            fos.flush(); fos.close(); is.close();

            dbHelper = new DatabaseHelper(this);
            loadSummary();

            showToast("恢复成功！所有数据已更新");
        } catch (Exception e) {
            String msg = getChineseErrorMsg(e);
            showToast("恢复失败：" + msg);
            dbHelper = new DatabaseHelper(this);
        }
    }

    // ===== 导出CSV =====
    private void exportCsv() {
        try {
            File exportDir = getExportDir();
            if (exportDir == null) {
                showToast("无法创建导出目录，请检查存储权限");
                return;
            }
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File csvFile = new File(exportDir, "抄表助手_" + timeStamp + ".csv");

            FileWriter fw = new FileWriter(csvFile);
            fw.write("\uFEFF"); // UTF-8 BOM for Excel

            // 表计信息段
            fw.write("===表计信息===\n");
            fw.write("ID,名称,表号,类型,计费模式,单位,创建时间\n");

            java.util.List<Meter> meters = dbHelper.getAllMeters();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            for (Meter m : meters) {
                fw.write(String.format(Locale.getDefault(), "%d,%s,%s,%s,%s,%s,%s\n",
                    m.getId(),
                    escapeCsv(m.getName()),
                    escapeCsv(m.getMeterNo() != null ? m.getMeterNo() : ""),
                    m.getMeterType(),
                    m.isPrepaid() ? "预付费" : "后付费",
                    m.getUnit(),
                    sdf.format(new Date(m.getCreatedAt()))));
            }

            // 分段计价段
            fw.write("\n===分段计价===\n");
            fw.write("表计ID,序号,起始值,结束值,单价\n");
            for (Meter m : meters) {
                java.util.List<Meter.PriceTier> tiers = m.getPriceTiers();
                for (int i = 0; i < tiers.size(); i++) {
                    Meter.PriceTier t = tiers.get(i);
                    fw.write(String.format(Locale.getDefault(), "%d,%d,%.2f,%s,%.4f\n",
                        m.getId(), i, t.startKwh,
                        t.endKwh < 0 ? "无穷" : String.format(Locale.getDefault(), "%.2f", t.endKwh),
                        t.price));
                }
            }

            // 记录数据段
            fw.write("\n===记录数据===\n");
            fw.write("表计ID,表计名称,时间,类型,余额,表读数,用量,费用,充值金额,备注\n");

            for (Meter m : meters) {
                java.util.List<Record> records = dbHelper.getRecordsByMeter(m.getId());
                for (Record r : records) {
                    fw.write(String.format(Locale.getDefault(), "%d,%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%s\n",
                        m.getId(),
                        escapeCsv(m.getName()),
                        sdf.format(new Date(r.getTimestamp())),
                        "recharge".equals(r.getRecordType()) ? "充值" : "抄表",
                        r.getBalance(),
                        r.getReading(),
                        r.getUsageDiff(),
                        r.getCostDiff(),
                        r.getRechargeAmount(),
                        escapeCsv(r.getNote() != null ? r.getNote() : "")));
                }
            }

            fw.close();

            // 分享
            Uri uri = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", csvFile);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "分享CSV文件"));

            Toast.makeText(this, "导出成功！请通过分享保存\n文件：" + csvFile.getName(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            String msg = getChineseErrorMsg(e);
            showToast("导出失败：" + msg);
        }
    }

    // ===== 导入CSV =====
    private void pickCsvFile() {
        new AlertDialog.Builder(this)
            .setTitle("导入CSV")
            .setMessage("导入数据将追加到现有数据中，不会删除已有记录。\n\nCSV文件需包含[表计信息]和[记录数据]两个段落。\n确定继续？")
            .setPositiveButton("选择文件", (d, w) -> {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                importCsvLauncher.launch(Intent.createChooser(intent, "选择CSV文件"));
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void importCsv(Uri uri) {
        new AlertDialog.Builder(this)
            .setTitle("确认导入")
            .setMessage("导入将追加数据到当前数据库，确定继续？")
            .setPositiveButton("导入", (d, w) -> doImportCsv(uri))
            .setNegativeButton("取消", null)
            .show();
    }

    private void doImportCsv(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            reader.close(); is.close();

            String content = sb.toString();
            if (content.startsWith("\uFEFF")) content = content.substring(1);

            // 解析三个段落：表计信息、分段计价、记录数据
            java.util.Map<Long, Long> meterIdMap = new java.util.HashMap<>(); // oldId → currentDbId
            java.util.Map<Long, Meter> newMeters = new java.util.HashMap<>(); // oldId → meter对象
            java.util.Map<Long, java.util.List<Meter.PriceTier>> meterTiers = new java.util.HashMap<>(); // oldId → 分段列表

            // 切分段落
            String meterSection = "";
            String tierSection = "";
            String recordSection = "";

            int idxMeter = content.indexOf("===表计信息===");
            int idxTier = content.indexOf("===分段计价===");
            int idxRecord = content.indexOf("===记录数据===");

            if (idxMeter >= 0 && idxRecord >= 0) {
                // 表计信息段：从 ===表计信息=== 到下一个 === 段或 ===记录数据===
                int meterEnd = idxRecord;
                if (idxTier >= 0 && idxTier < idxRecord) meterEnd = idxTier;
                meterSection = content.substring(idxMeter, meterEnd);

                // 分段计价段：从 ===分段计价=== 到 ===记录数据===
                if (idxTier >= 0) {
                    tierSection = content.substring(idxTier, idxRecord);
                }

                // 记录数据段
                recordSection = content.substring(idxRecord);
            } else {
                showToast("CSV格式不正确，缺少必要段落");
                return;
            }

            // 解析表计信息段落
            String meterContent = meterSection.replaceFirst("===表计信息===", "").trim();
            if (!meterContent.isEmpty()) {
                String[] meterLines = meterContent.split("\n");
                for (int i = 1; i < meterLines.length; i++) { // 跳过表头
                    String ml = meterLines[i].trim();
                    if (ml.isEmpty()) continue;
                    String[] mc = parseCsvLine(ml);
                    if (mc.length < 7) continue;
                    try {
                        long oldId = Long.parseLong(mc[0].trim());
                        String mName = mc[1].trim();
                        String mNo = mc[2].trim();
                        String mType = typeToEnglish(mc[3].trim());
                        String billingMode = "后付费".equals(mc[4].trim()) ? "postpaid" : "prepaid";
                        String mUnit = mc[5].trim();
                        newMeters.put(oldId, createTempMeter(mName, mNo, mType, billingMode, mUnit));
                    } catch (Exception e) { /* skip bad line */ }
                }
            }

            // 解析分段计价段落
            String tierContent = tierSection.replaceFirst("===分段计价===", "").trim();
            if (!tierContent.isEmpty()) {
                String[] tierLines = tierContent.split("\n");
                for (int i = 1; i < tierLines.length; i++) { // 跳过表头
                    String tl = tierLines[i].trim();
                    if (tl.isEmpty()) continue;
                    String[] tc = parseCsvLine(tl);
                    if (tc.length < 5) continue;
                    try {
                        long oldId = Long.parseLong(tc[0].trim());
                        double startVal = Double.parseDouble(tc[2].trim());
                        double endVal = "无穷".equals(tc[3].trim()) ? -1 : Double.parseDouble(tc[3].trim());
                        double price = Double.parseDouble(tc[4].trim());
                        java.util.List<Meter.PriceTier> tiers = meterTiers.get(oldId);
                        if (tiers == null) { tiers = new java.util.ArrayList<>(); meterTiers.put(oldId, tiers); }
                        tiers.add(new Meter.PriceTier(startVal, endVal, price));
                    } catch (Exception e) { /* skip bad line */ }
                }
            }

            // 确保所有引用的表计在数据库中存在
            int createdMeters = 0;
            for (java.util.Map.Entry<Long, Meter> entry : newMeters.entrySet()) {
                long oldId = entry.getKey();
                Meter m = entry.getValue();
                // 先按名称查找是否已存在
                Meter existing = findMeterByName(m.getName());
                if (existing != null) {
                    meterIdMap.put(oldId, existing.getId());
                    // 如果已存在表计没有自定义分段，且导入有分段，则更新
                    if (existing.getPriceTiers().isEmpty() && meterTiers.containsKey(oldId)) {
                        dbHelper.saveTiers(existing.getId(), meterTiers.get(oldId));
                    }
                } else {
                    // 自动创建表计
                    long newId = dbHelper.insertMeter(m.getName(), m.getMeterNo(), m.getMeterType(), m.getBillingMode(), m.getUnit());
                    meterIdMap.put(oldId, newId);
                    createdMeters++;
                    // 写入分段计价（覆盖默认分段）
                    if (meterTiers.containsKey(oldId)) {
                        dbHelper.saveTiers(newId, meterTiers.get(oldId));
                    }
                }
            }

            // 对记录数据段中出现的表计ID，如果没有在表计信息段中定义，也按名称查找
            String[] lines = recordSection.replaceFirst("===记录数据===", "").trim().split("\n");
            int imported = 0;
            int skipped = 0;

            for (int i = 1; i < lines.length; i++) {
                String ln = lines[i].trim();
                if (ln.isEmpty()) continue;
                String[] cols = parseCsvLine(ln);
                if (cols.length < 10) { skipped++; continue; }

                try {
                    long oldMeterId = Long.parseLong(cols[0].trim());
                    double balance = Double.parseDouble(cols[4].trim());
                    double reading = cols.length > 5 ? Double.parseDouble(cols[5].trim()) : 0;
                    String typeStr = cols[3].trim();
                    double rechargeAmt = cols.length > 8 ? Double.parseDouble(cols[8].trim()) : 0;
                    String note = cols.length > 9 ? cols[9].trim() : "";
                    // 解析时间戳
                    long timestamp = System.currentTimeMillis();
                    if (cols.length > 2 && !cols[2].trim().isEmpty()) {
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                            Date d = sdf.parse(cols[2].trim());
                            if (d != null) timestamp = d.getTime();
                        } catch (Exception pe) { /* use default */ }
                    }

                    // 解析目标meterId
                    Long targetMeterId = meterIdMap.get(oldMeterId);
                    if (targetMeterId == null) {
                        // 尝试按名称查找（cols[1]是表计名称）
                        String meterName = cols.length > 1 ? cols[1].trim() : "";
                        Meter existing = findMeterByName(meterName);
                        if (existing != null) {
                            targetMeterId = existing.getId();
                            meterIdMap.put(oldMeterId, targetMeterId);
                        } else if (!meterName.isEmpty()) {
                            // 自动创建（默认电表/预付费）
                            long newId = dbHelper.insertMeter(meterName, "", "electric", "prepaid", "kWh");
                            meterIdMap.put(oldMeterId, newId);
                            targetMeterId = newId;
                            createdMeters++;
                        } else {
                            skipped++; continue;
                        }
                    }

                    Meter meter = dbHelper.getMeter(targetMeterId);
                    if (meter == null) { skipped++; continue; }

                    if ("充值".equals(typeStr)) {
                        dbHelper.insertRecharge(targetMeterId, rechargeAmt, note, timestamp);
                    } else {
                        dbHelper.insertRecord(targetMeterId, balance, reading, note, timestamp);
                    }
                    imported++;
                } catch (Exception e) {
                    skipped++;
                }
            }

            loadSummary();
            String msg = String.format(Locale.getDefault(), "导入完成！成功 %d 条，跳过 %d 条", imported, skipped);
            if (createdMeters > 0) msg += String.format(Locale.getDefault(), "\n自动创建 %d 个表计", createdMeters);
            showToast(msg);

        } catch (Exception e) {
            String msg = getChineseErrorMsg(e);
            showToast("导入失败：" + msg);
        }
    }

    // 中文类型转英文
    private String typeToEnglish(String label) {
        if ("电表".equals(label)) return "electric";
        if ("水表".equals(label)) return "water";
        if ("气表".equals(label)) return "gas";
        if ("electric".equals(label)) return "electric";
        if ("water".equals(label)) return "water";
        if ("gas".equals(label)) return "gas";
        return "other";
    }

    // 创建临时Meter对象（不入库）
    private Meter createTempMeter(String name, String meterNo, String type, String billingMode, String unit) {
        Meter m = new Meter();
        m.setName(name); m.setMeterNo(meterNo);
        m.setMeterType(type); m.setBillingMode(billingMode); m.setUnit(unit);
        return m;
    }

    // 按名称查找已有表计
    private Meter findMeterByName(String name) {
        if (name == null || name.isEmpty()) return null;
        for (Meter m : dbHelper.getAllMeters()) {
            if (name.equals(m.getName())) return m;
        }
        return null;
    }

    // ===== 中文错误信息 =====
    private String getChineseErrorMsg(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return "未知错误";

        // FileProvider 相关错误
        if (msg.contains("Failed to find configured root")) {
            return "无法访问存储目录，请检查存储权限";
        }
        // 权限相关
        if (msg.contains("Permission") || msg.contains("EACCES")) {
            return "没有存储权限，请在设置中开启";
        }
        // 文件不存在
        if (msg.contains("ENOENT") || msg.contains("No such file")) {
            return "文件不存在或已被移动";
        }
        // 文件读写
        if (msg.contains("openInputStream") || msg.contains("FileNotFoundException")) {
            return "无法读取文件，请重新选择";
        }
        // 数据库相关
        if (msg.contains("SQLITE") || msg.contains("database")) {
            return "数据库操作异常，请重试";
        }
        // CSV解析
        if (msg.contains("NumberFormatException") || msg.contains("parse")) {
            return "文件格式不正确，请检查CSV内容";
        }

        // 截断过长的技术信息
        if (msg.length() > 50) {
            return msg.substring(0, 50) + "...";
        }
        return msg;
    }

    // ===== 目录获取（兼容 getExternalFilesDir 返回 null） =====
    private File getBackupDir() {
        File extDir = getExternalFilesDir(null);
        if (extDir != null) return new File(extDir, BACKUP_DIR);
        // fallback 到内部存储
        return new File(getFilesDir(), BACKUP_DIR);
    }

    private File getExportDir() {
        File extDir = getExternalFilesDir(null);
        if (extDir != null) {
            File dir = new File(extDir, EXPORT_DIR);
            if (!dir.exists()) dir.mkdirs();
            return dir;
        }
        File dir = new File(getFilesDir(), EXPORT_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    // ===== 工具方法 =====
    private String escapeCsv(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private String[] parseCsvLine(String line) {
        java.util.List<String> result = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result.toArray(new String[0]);
    }

    private void copyFile(File src, File dst) throws Exception {
        FileInputStream fis = new FileInputStream(src);
        FileOutputStream fos = new FileOutputStream(dst);
        byte[] buf = new byte[4096];
        int len;
        while ((len = fis.read(buf)) > 0) fos.write(buf, 0, len);
        fos.flush(); fos.close(); fis.close();
    }
}
