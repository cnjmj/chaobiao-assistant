package com.example.datarecorder;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "meter_recorder.db";
    static final int DB_VERSION = 6;
    private static final String PREFS_NAME = "meter_recorder_prefs";
    private static final String KEY_INITIAL_RECALC_DONE = "initial_recalc_done_v5";

    private static final String T_METERS = "meters";
    private static final String T_RECORDS = "records";
    private static final String T_TIERS = "price_tiers";

    // 脏标记：记录变更后标记，recalculateDiffs执行后清除
    private final Set<Long> dirtyMeters = new HashSet<>();
    private final Context context;

    public DatabaseHelper(Context context) { super(context, DB_NAME, null, DB_VERSION); this.context = context; ensureInitialRecalc(); }

    /** 首次启动时全量重算一次（兼容旧版本数据） */
    private void ensureInitialRecalc() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_INITIAL_RECALC_DONE, false)) {
            markAllDirty();
            prefs.edit().putBoolean(KEY_INITIAL_RECALC_DONE, true).apply();
        }
    }

    /** 标记某表计数据已变更，需要重算diff */
    public void markDirty(long meterId) { dirtyMeters.add(meterId); }

    /** 检查某表计是否需要重算diff */
    public boolean isDirty(long meterId) { return dirtyMeters.contains(meterId); }

    /** 标记所有表计为脏（用于数据库恢复等场景） */
    public void markAllDirty() { for (Meter m : getAllMeters()) dirtyMeters.add(m.getId()); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_METERS + " (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, meter_no TEXT, meter_type TEXT DEFAULT 'electric', billing_mode TEXT DEFAULT 'prepaid', unit TEXT DEFAULT 'kWh', created_at INTEGER NOT NULL, sort_order INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE " + T_TIERS + " (id INTEGER PRIMARY KEY AUTOINCREMENT, meter_id INTEGER NOT NULL, tier_order INTEGER NOT NULL, start_val REAL NOT NULL, end_val REAL NOT NULL, price REAL NOT NULL, FOREIGN KEY(meter_id) REFERENCES meters(id))");
        db.execSQL("CREATE TABLE " + T_RECORDS + " (id INTEGER PRIMARY KEY AUTOINCREMENT, meter_id INTEGER NOT NULL, balance REAL NOT NULL, reading REAL DEFAULT 0, reading_is_cumulative INTEGER DEFAULT 0, timestamp INTEGER NOT NULL, usage_diff REAL DEFAULT 0, cost_diff REAL DEFAULT 0, note TEXT, record_type TEXT DEFAULT 'reading', recharge_amount REAL DEFAULT 0, FOREIGN KEY(meter_id) REFERENCES meters(id))");
        db.execSQL("CREATE INDEX idx_records_meter_time ON records(meter_id, timestamp)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
        if (o < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + T_TIERS + " (id INTEGER PRIMARY KEY AUTOINCREMENT, meter_id INTEGER NOT NULL, tier_order INTEGER NOT NULL, start_val REAL NOT NULL, end_val REAL NOT NULL, price REAL NOT NULL, FOREIGN KEY(meter_id) REFERENCES meters(id))");
        }
        if (o < 3) {
            try { db.execSQL("ALTER TABLE " + T_METERS + " ADD COLUMN billing_mode TEXT DEFAULT 'prepaid'"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE " + T_RECORDS + " ADD COLUMN record_type TEXT DEFAULT 'reading'"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE " + T_RECORDS + " ADD COLUMN recharge_amount REAL DEFAULT 0"); } catch (Exception e) {}
        }
        if (o < 4) {
            try { db.execSQL("ALTER TABLE " + T_RECORDS + " ADD COLUMN reading REAL DEFAULT 0"); } catch (Exception e) {}
        }
        if (o < 5) {
            try { db.execSQL("ALTER TABLE " + T_RECORDS + " ADD COLUMN reading_is_cumulative INTEGER DEFAULT 0"); } catch (Exception e) {}
        }
        if (o < 6) {
            try { db.execSQL("ALTER TABLE " + T_METERS + " ADD COLUMN sort_order INTEGER DEFAULT 0"); } catch (Exception e) {}
        }
    }

    // ===== Meter =====
    public long insertMeter(String name, String meterNo, String type, String billingMode, String unit) {
        SQLiteDatabase db = getWritableDatabase();
        // 获取当前最大 sort_order
        int maxSort = 0;
        Cursor c = db.rawQuery("SELECT MAX(sort_order) FROM " + T_METERS, null);
        if (c.moveToFirst()) maxSort = c.getInt(0);
        c.close();
        ContentValues cv = new ContentValues();
        cv.put("name", name); cv.put("meter_no", meterNo);
        cv.put("meter_type", type); cv.put("billing_mode", billingMode); cv.put("unit", unit);
        cv.put("created_at", System.currentTimeMillis());
        cv.put("sort_order", maxSort + 1);
        long id = db.insert(T_METERS, null, cv);
        db.close(); return id;
    }

    public boolean updateMeter(long id, String name, String meterNo, String type, String billingMode, String unit) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name); cv.put("meter_no", meterNo);
        cv.put("meter_type", type); cv.put("billing_mode", billingMode); cv.put("unit", unit);
        int r = db.update(T_METERS, cv, "id=?", new String[]{String.valueOf(id)});
        db.close(); return r > 0;
    }

    public boolean deleteMeter(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(T_RECORDS, "meter_id=?", new String[]{String.valueOf(id)});
        db.delete(T_TIERS, "meter_id=?", new String[]{String.valueOf(id)});
        int r = db.delete(T_METERS, "id=?", new String[]{String.valueOf(id)});
        db.close(); return r > 0;
    }

    /** 批量更新表计排序顺序 */
    public void updateSortOrders(List<Long> meterIdsInOrder) {
        SQLiteDatabase db = getWritableDatabase();
        for (int i = 0; i < meterIdsInOrder.size(); i++) {
            ContentValues cv = new ContentValues();
            cv.put("sort_order", i);
            db.update(T_METERS, cv, "id=?", new String[]{String.valueOf(meterIdsInOrder.get(i))});
        }
        db.close();
    }

    public List<Meter> getAllMeters() {
        List<Meter> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_METERS, null, null, null, null, null, "sort_order ASC, created_at ASC");
        if (c.moveToFirst()) { do { list.add(readMeter(c)); } while (c.moveToNext()); }
        c.close(); db.close();
        for (Meter m : list) m.setPriceTiers(getTiers(m.getId()));
        return list;
    }

    public Meter getMeter(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_METERS, null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        Meter m = null;
        if (c.moveToFirst()) m = readMeter(c);
        c.close(); db.close();
        if (m != null) m.setPriceTiers(getTiers(id));
        return m;
    }

    private Meter readMeter(Cursor c) {
        Meter m = new Meter();
        m.setId(c.getLong(c.getColumnIndexOrThrow("id")));
        m.setName(c.getString(c.getColumnIndexOrThrow("name")));
        m.setMeterNo(c.getString(c.getColumnIndexOrThrow("meter_no")));
        m.setMeterType(c.getString(c.getColumnIndexOrThrow("meter_type")));
        int bi = c.getColumnIndex("billing_mode");
        m.setBillingMode(bi >= 0 ? c.getString(bi) : "prepaid");
        m.setUnit(c.getString(c.getColumnIndexOrThrow("unit")));
        m.setCreatedAt(c.getLong(c.getColumnIndexOrThrow("created_at")));
        int si = c.getColumnIndex("sort_order");
        m.setSortOrder(si >= 0 ? c.getInt(si) : 0);
        return m;
    }

    // ===== PriceTiers =====
    public void saveTiers(long meterId, List<Meter.PriceTier> tiers) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(T_TIERS, "meter_id=?", new String[]{String.valueOf(meterId)});
        for (int i = 0; i < tiers.size(); i++) {
            Meter.PriceTier t = tiers.get(i);
            ContentValues cv = new ContentValues();
            cv.put("meter_id", meterId); cv.put("tier_order", i);
            cv.put("start_val", t.startKwh); cv.put("end_val", t.endKwh);
            cv.put("price", t.price);
            db.insert(T_TIERS, null, cv);
        }
        db.close();
    }

    public List<Meter.PriceTier> getTiers(long meterId) {
        List<Meter.PriceTier> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_TIERS, null, "meter_id=?", new String[]{String.valueOf(meterId)}, null, null, "tier_order ASC");
        if (c.moveToFirst()) { do {
            list.add(new Meter.PriceTier(
                c.getDouble(c.getColumnIndexOrThrow("start_val")),
                c.getDouble(c.getColumnIndexOrThrow("end_val")),
                c.getDouble(c.getColumnIndexOrThrow("price"))));
        } while (c.moveToNext()); }
        c.close(); db.close(); return list;
    }

    // ===== Records =====
    // 插入抄表记录（自定义时间戳，用于补录）
    public Record insertRecord(long meterId, double balance, double reading, boolean readingIsCumulative, String note, long timestamp) {
        Meter meter = getMeter(meterId);

        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("meter_id", meterId); cv.put("balance", balance);
        cv.put("reading", reading);
        cv.put("reading_is_cumulative", readingIsCumulative ? 1 : 0);
        cv.put("timestamp", timestamp);
        cv.put("note", note); cv.put("usage_diff", 0); cv.put("cost_diff", 0);
        cv.put("record_type", "reading"); cv.put("recharge_amount", 0);
        long id = db.insert(T_RECORDS, null, cv);
        db.close();

        // 补录后重算该表计所有diff
        recalculateDiffs(meterId);

        Record r = getRecordById(id);
        return r != null ? r : new Record();
    }

    // 插入抄表记录（当前时间）
    public Record insertRecord(long meterId, double balance, double reading, boolean readingIsCumulative, String note) {
        return insertRecord(meterId, balance, reading, readingIsCumulative, note, System.currentTimeMillis());
    }

    // 旧方法兼容（默认非累计）
    public Record insertRecord(long meterId, double balance, double reading, String note, long timestamp) {
        return insertRecord(meterId, balance, reading, false, note, timestamp);
    }
    public Record insertRecord(long meterId, double balance, double reading, String note) {
        return insertRecord(meterId, balance, reading, false, note);
    }

    // 插入充值记录（自定义时间戳，用于补录）
    public Record insertRecharge(long meterId, double amount, String note, long timestamp) {
        Meter meter = getMeter(meterId);

        // 找到该时间点之前最近一条记录的余额
        SQLiteDatabase db2 = getReadableDatabase();
        Cursor c = db2.query(T_RECORDS, null, "meter_id=? AND timestamp<?",
            new String[]{String.valueOf(meterId), String.valueOf(timestamp)},
            null, null, "timestamp DESC", "1");
        double prevBalance = 0;
        if (c.moveToFirst()) { int bi = c.getColumnIndex("balance"); if (bi >= 0) prevBalance = c.getDouble(bi); }
        c.close(); db2.close();

        double newBalance = meter.isPrepaid() ? prevBalance + amount : amount;

        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("meter_id", meterId); cv.put("balance", newBalance);
        cv.put("reading", 0);
        cv.put("timestamp", timestamp);
        cv.put("note", note); cv.put("usage_diff", 0); cv.put("cost_diff", 0);
        cv.put("record_type", "recharge"); cv.put("recharge_amount", amount);
        long id = db.insert(T_RECORDS, null, cv);
        db.close();

        // 补录后重算该表计所有diff
        recalculateDiffs(meterId);

        Record r = getRecordById(id);
        return r != null ? r : new Record();
    }

    // 插入充值记录（当前时间）
    public Record insertRecharge(long meterId, double amount, String note) {
        return insertRecharge(meterId, amount, note, System.currentTimeMillis());
    }

    // 重算某表计所有记录的diff（补录后、打开详情时调用）
    public void recalculateDiffs(long meterId) {
        Meter meter = getMeter(meterId);
        if (meter == null) return;

        // 一次性读取所有记录（时间正序）和ID
        List<Long> ids = new ArrayList<>();
        List<Record> allRecs = new ArrayList<>();
        SQLiteDatabase rdb = getReadableDatabase();
        Cursor c = rdb.query(T_RECORDS, null, "meter_id=?",
            new String[]{String.valueOf(meterId)}, null, null, "timestamp ASC");
        if (c.moveToFirst()) { do {
            ids.add(c.getLong(c.getColumnIndexOrThrow("id")));
            allRecs.add(readRecord(c));
        } while (c.moveToNext()); }
        c.close(); rdb.close();

        if (allRecs.isEmpty()) return;

        // 计算每条记录的 diff
        double[] usageDiffs = new double[allRecs.size()];
        double[] costDiffs = new double[allRecs.size()];
        double[] newBalances = new double[allRecs.size()]; // 充值记录需要更新余额

        double prevBalance = 0;
        double prevReadingValue = 0;
        long prevReadingTimestamp = 0;

        for (int i = 0; i < allRecs.size(); i++) {
            Record r = allRecs.get(i);
            if (i == 0) {
                // 首条记录，无diff，只记录参考值
                prevBalance = r.getBalance();
                prevReadingValue = r.getReading() > Record.EPSILON ? r.getReading() : r.getBalance();
                prevReadingTimestamp = r.getTimestamp();
                continue;
            }
            if (r.isRecharge()) {
                // 充值记录：新余额 = 上条余额 + 充值金额
                double newBal = prevBalance + r.getRechargeAmount();
                newBalances[i] = newBal;
                prevBalance = newBal;
                continue;
            }
            // 抄表记录
            double newUsage = 0, newCost = 0;
            if (meter.isPrepaid()) {
                // 预付费：费用 = 上次余额 - 本次余额
                // prevBalance 已在充值记录中更新（含充值），无需再加 rechargeBetween
                newCost = prevBalance - r.getBalance();
                if (newCost < 0) newCost = 0;
                // 用量：优先用录入值，无录入则从费用反推
                double currentReadingVal = r.getReading() > Record.EPSILON ? r.getReading() : 0;
                if (currentReadingVal > Record.EPSILON) {
                    if (r.isReadingCumulative()) {
                        // 累计值模式：用量=当前累计值-上次累计值
                        if (prevReadingValue > Record.EPSILON) {
                            newUsage = currentReadingVal - prevReadingValue;
                            if (newUsage < 0) newUsage = 0;
                        }
                    } else {
                        // 当期用量模式：录入值即当期用量
                        newUsage = currentReadingVal;
                    }
                } else {
                    // 无录入用量：从费用按分段计价反推用量
                    if (newCost > Record.EPSILON && !meter.getPriceTiers().isEmpty()) {
                        newUsage = meter.calculateUsageFromCost(newCost);
                    }
                }
            } else {
                // 后付费：用量 = 当前读数 - 上次读数
                double currentReading = r.getReading() > Record.EPSILON ? r.getReading() : r.getBalance();
                double lastReading = prevReadingValue;
                if (currentReading > Record.EPSILON && lastReading > Record.EPSILON) {
                    newUsage = currentReading - lastReading;
                    if (newUsage < 0) newUsage = 0;
                }
                newCost = meter.calculateCost(newUsage);
            }
            usageDiffs[i] = newUsage;
            costDiffs[i] = newCost;
            prevReadingTimestamp = r.getTimestamp();
            prevReadingValue = r.getReading() > Record.EPSILON ? r.getReading() : r.getBalance();
            prevBalance = r.getBalance();
        }

        // 一次性写入数据库
        SQLiteDatabase wdb = getWritableDatabase();
        for (int i = 0; i < allRecs.size(); i++) {
            Record r = allRecs.get(i);
            ContentValues cv = new ContentValues();
            if (r.isRecharge() && i > 0) {
                cv.put("balance", newBalances[i]);
                cv.put("usage_diff", 0);
                cv.put("cost_diff", 0);
            } else {
                cv.put("usage_diff", usageDiffs[i]);
                cv.put("cost_diff", costDiffs[i]);
            }
            wdb.update(T_RECORDS, cv, "id=?", new String[]{String.valueOf(ids.get(i))});
        }
        wdb.close();

        // 清除脏标记
        dirtyMeters.remove(meterId);
    }

    // 计算两次抄表之间的充值总额
    private double sumRechargeBetween(long meterId, long startTime, long endTime) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
            "SELECT SUM(recharge_amount) FROM " + T_RECORDS +
            " WHERE meter_id=? AND record_type='recharge' AND timestamp>? AND timestamp<=?",
            new String[]{String.valueOf(meterId), String.valueOf(startTime), String.valueOf(endTime)});
        double total = 0;
        if (c.moveToFirst()) total = c.getDouble(0);
        c.close(); db.close();
        return total;
    }

    public List<Record> getRecordsByMeter(long meterId) {
        List<Record> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_RECORDS, null, "meter_id=?", new String[]{String.valueOf(meterId)}, null, null, "timestamp DESC");
        if (c.moveToFirst()) { do { list.add(readRecord(c)); } while (c.moveToNext()); }
        c.close(); db.close(); return list;
    }

    public List<Record> getRecordsByRange(long meterId, long start, long end) {
        List<Record> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_RECORDS, null, "meter_id=? AND timestamp>=? AND timestamp<=?",
            new String[]{String.valueOf(meterId), String.valueOf(start), String.valueOf(end)}, null, null, "timestamp ASC");
        if (c.moveToFirst()) { do { list.add(readRecord(c)); } while (c.moveToNext()); }
        c.close(); db.close(); return list;
    }

    private Record readRecord(Cursor c) {
        Record r = new Record();
        r.setId(c.getLong(c.getColumnIndexOrThrow("id")));
        r.setMeterId(c.getLong(c.getColumnIndexOrThrow("meter_id")));
        r.setBalance(c.getDouble(c.getColumnIndexOrThrow("balance")));
        int ri = c.getColumnIndex("reading");
        r.setReading(ri >= 0 ? c.getDouble(ri) : 0);
        int ci = c.getColumnIndex("reading_is_cumulative");
        r.setReadingCumulative(ci >= 0 && c.getInt(ci) == 1);
        r.setTimestamp(c.getLong(c.getColumnIndexOrThrow("timestamp")));
        r.setUsageDiff(c.getDouble(c.getColumnIndexOrThrow("usage_diff")));
        r.setCostDiff(c.getDouble(c.getColumnIndexOrThrow("cost_diff")));
        r.setNote(c.getString(c.getColumnIndexOrThrow("note")));
        int rti = c.getColumnIndex("record_type");
        r.setRecordType(rti >= 0 ? c.getString(rti) : "reading");
        int rai = c.getColumnIndex("recharge_amount");
        r.setRechargeAmount(rai >= 0 ? c.getDouble(rai) : 0);
        return r;
    }

    private Record getLastRecord(long meterId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_RECORDS, null, "meter_id=?", new String[]{String.valueOf(meterId)}, null, null, "timestamp DESC", "1");
        Record r = null;
        if (c.moveToFirst()) r = readRecord(c);
        c.close(); db.close(); return r;
    }

    // 获取上次录的表读数（跳过充值记录）
    private double getLastReading(long meterId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_RECORDS, null, "meter_id=? AND record_type='reading' AND reading>0", new String[]{String.valueOf(meterId)}, null, null, "timestamp DESC", "1");
        double rd = 0;
        if (c.moveToFirst()) { int ri = c.getColumnIndex("reading"); if (ri >= 0) rd = c.getDouble(ri); }
        c.close(); db.close(); return rd;
    }

    public boolean deleteRecord(long id) {
        Record r = getRecordById(id);
        SQLiteDatabase db = getWritableDatabase();
        int result = db.delete(T_RECORDS, "id=?", new String[]{String.valueOf(id)});
        db.close();
        if (result > 0 && r != null) markDirty(r.getMeterId());
        return result > 0;
    }

    public Record getRecordById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_RECORDS, null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        Record r = null;
        if (c.moveToFirst()) r = readRecord(c);
        c.close(); db.close(); return r;
    }

    public boolean updateRecord(long id, double balance, double reading, boolean readingIsCumulative, long timestamp, String note) {
        // 先获取meterId用于脏标记
        Record r = getRecordById(id);
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("balance", balance); cv.put("reading", reading);
        cv.put("reading_is_cumulative", readingIsCumulative ? 1 : 0);
        cv.put("timestamp", timestamp); cv.put("note", note);
        int result = db.update(T_RECORDS, cv, "id=?", new String[]{String.valueOf(id)});
        db.close();
        if (result > 0 && r != null) markDirty(r.getMeterId());
        return result > 0;
    }

    public boolean updateRecord(long id, double balance, double reading, long timestamp, String note) {
        return updateRecord(id, balance, reading, false, timestamp, note);
    }

    public boolean updateRecord(long id, double balance, String note) {
        // 旧方法兼容：不修改reading和timestamp
        Record r = getRecordById(id);
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("balance", balance); cv.put("note", note);
        int result = db.update(T_RECORDS, cv, "id=?", new String[]{String.valueOf(id)});
        db.close();
        if (result > 0 && r != null) markDirty(r.getMeterId());
        return result > 0;
    }

    public int getRecordCount(long meterId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + T_RECORDS + " WHERE meter_id=?", new String[]{String.valueOf(meterId)});
        int n = 0; if (c.moveToFirst()) n = c.getInt(0);
        c.close(); db.close(); return n;
    }
}