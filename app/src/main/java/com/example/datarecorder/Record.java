package com.example.datarecorder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Record {
    private long id;
    private long meterId;
    private double balance;       // 余额(元) for prepaid, 读数 for postpaid
    private double reading;       // 表读数(kWh/吨/m³), 仅预付费时可选填写
    private long timestamp;
    private double usageDiff;     // 用量(kWh/吨/m³)
    private double costDiff;      // 费用(元)
    private String note;
    private String recordType;    // reading / recharge
    private double rechargeAmount;
    private boolean readingIsCumulative; // true=累计值需算差, false=当期用量(预付费默认), 后付费默认true
    public static final double EPSILON = 0.0001;

    public Record() { this.timestamp = System.currentTimeMillis(); this.recordType = "reading"; this.rechargeAmount = 0; this.reading = 0; this.readingIsCumulative = false; }

    public long getId() { return id; } public void setId(long id) { this.id = id; }
    public long getMeterId() { return meterId; } public void setMeterId(long id) { this.meterId = id; }
    public double getBalance() { return balance; } public void setBalance(double b) { this.balance = b; }
    public double getReading() { return reading; } public void setReading(double r) { this.reading = r; }
    public long getTimestamp() { return timestamp; } public void setTimestamp(long t) { this.timestamp = t; }
    public double getUsageDiff() { return usageDiff; } public void setUsageDiff(double d) { this.usageDiff = d; }
    public double getCostDiff() { return costDiff; } public void setCostDiff(double d) { this.costDiff = d; }
    public String getNote() { return note; } public void setNote(String n) { this.note = n; }
    public String getRecordType() { return recordType; } public void setRecordType(String t) { this.recordType = t; }
    public double getRechargeAmount() { return rechargeAmount; } public void setRechargeAmount(double a) { this.rechargeAmount = a; }
    public boolean isReadingCumulative() { return readingIsCumulative; } public void setReadingCumulative(boolean c) { this.readingIsCumulative = c; }
    public boolean isRecharge() { return "recharge".equals(recordType); }
    public void markAsFirst() { this.usageDiff = 0; this.costDiff = 0; }
    public void markAsRecharge(double amount) { this.recordType = "recharge"; this.rechargeAmount = amount; this.usageDiff = 0; this.costDiff = 0; }

    // 预付费: 费用始终=上次余额-本次余额; 用量优先用录入值，无录入则从费用反推
    public void calculateDiffPrepaid(double prevBalance, double prevReading, Meter meter) {
        this.costDiff = prevBalance - this.balance;
        if (this.costDiff < 0) this.costDiff = 0; // 余额增加不算费用
        if (this.reading > 0 && prevReading > 0) {
            this.usageDiff = this.reading - prevReading;
            if (this.usageDiff < 0) this.usageDiff = 0;
        } else if (this.reading > 0) {
            // 当期用量模式（非累计），录入值即用量
            this.usageDiff = this.reading;
        } else {
            // 无读数时，从费用按分段计价反推用量
            if (this.costDiff > EPSILON && meter != null) {
                this.usageDiff = meter.calculateUsageFromCost(this.costDiff);
            } else {
                this.usageDiff = 0;
            }
        }
    }

    // 后付费: 读数增加=用了电, 用量=读数差, 费用=分段计价
    public void calculateDiffPostpaid(double prevReading, Meter meter) {
        this.usageDiff = this.balance - prevReading; // balance字段存读数
        if (this.usageDiff > EPSILON && meter != null) this.costDiff = meter.calculateCost(this.usageDiff);
        else this.costDiff = 0;
    }

    public String getFormattedTime() { return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date(timestamp)); }
    public String getFormattedDate() { return new SimpleDateFormat("MM/dd", Locale.CHINA).format(new Date(timestamp)); }
    public String getFormattedDateFull() { return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date(timestamp)); }
    public String getFormattedBalance() { return String.format(Locale.CHINA, "%.2f", balance); }
    public String getFormattedReading() { return reading > 0 ? String.format(Locale.CHINA, "%.2f", reading) : ""; }
    public String getFormattedUsageDiff() { if (Math.abs(usageDiff) < EPSILON) return "--"; return String.format(Locale.CHINA, "%.2f", usageDiff); }
    public String getFormattedCostDiff() { if (Math.abs(costDiff) < EPSILON) return "--"; return String.format(Locale.CHINA, "%.2f", costDiff); }
    public boolean isConsumed() { return costDiff > EPSILON || usageDiff > EPSILON; }
}