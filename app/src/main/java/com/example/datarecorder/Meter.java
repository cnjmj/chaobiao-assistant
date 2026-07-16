package com.example.datarecorder;

import java.util.ArrayList;
import java.util.List;

public class Meter {
    private long id;
    private String name;
    private String meterNo;
    private String meterType;      // electric/water/gas/other
    private String billingMode;    // prepaid/postpaid
    private String unit;           // kWh/吨/m3
    private List<PriceTier> priceTiers = new ArrayList<>();
    private long createdAt;
    private int sortOrder = 0;

    public Meter() { this.meterType = "electric"; this.billingMode = "prepaid"; this.unit = "kWh"; this.createdAt = System.currentTimeMillis(); }

    public long getId() { return id; } public void setId(long id) { this.id = id; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getMeterNo() { return meterNo; } public void setMeterNo(String meterNo) { this.meterNo = meterNo; }
    public String getMeterType() { return meterType; } public void setMeterType(String t) { this.meterType = t; }
    public String getBillingMode() { return billingMode; } public void setBillingMode(String m) { this.billingMode = m; }
    public String getUnit() { return unit; } public void setUnit(String u) { this.unit = u; }
    public List<PriceTier> getPriceTiers() { return priceTiers; } public void setPriceTiers(List<PriceTier> t) { this.priceTiers = t; }
    public long getCreatedAt() { return createdAt; } public void setCreatedAt(long t) { this.createdAt = t; }
    public int getSortOrder() { return sortOrder; } public void setSortOrder(int s) { this.sortOrder = s; }

    public String getTypeLabel() {
        if ("electric".equals(meterType)) return "电表";
        if ("water".equals(meterType)) return "水表";
        if ("gas".equals(meterType)) return "气表";
        return "其他";
    }

    public String getBillingModeLabel() {
        if ("prepaid".equals(billingMode)) return "预付费";
        return "后付费";
    }

    public boolean isPrepaid() { return "prepaid".equals(billingMode); }

    public void setDefaultTiers() {
        priceTiers.clear();
        if ("electric".equals(meterType)) {
            priceTiers.add(new PriceTier(0, 180, 0.56));
            priceTiers.add(new PriceTier(180, 350, 0.61));
            priceTiers.add(new PriceTier(350, -1, 0.86));
            unit = "kWh";
        } else if ("water".equals(meterType)) {
            priceTiers.add(new PriceTier(0, 15, 2.45));
            priceTiers.add(new PriceTier(15, 22, 3.45));
            priceTiers.add(new PriceTier(22, -1, 4.45));
            unit = "吨";
        } else if ("gas".equals(meterType)) {
            priceTiers.add(new PriceTier(0, 300, 2.03));
            priceTiers.add(new PriceTier(300, 500, 2.43));
            priceTiers.add(new PriceTier(500, -1, 3.03));
            unit = "m\u00B3";
        } else {
            priceTiers.add(new PriceTier(0, -1, 1.0));
        }
    }

    public double calculateCost(double usage) {
        double cost = 0, remaining = usage;
        for (PriceTier tier : priceTiers) {
            if (remaining <= 0) break;
            double tierRange = tier.endKwh < 0 ? remaining : Math.min(remaining, tier.endKwh - tier.startKwh);
            cost += tierRange * tier.price;
            remaining -= tierRange;
        }
        return cost;
    }

    // 从费用反推用量（逐档扣除费用，算出各档用量再求和）
    public double calculateUsageFromCost(double cost) {
        if (cost <= 0 || priceTiers.isEmpty()) return 0;
        double remaining = cost, usage = 0;
        for (PriceTier tier : priceTiers) {
            if (remaining <= 0) break;
            double tierRange = tier.endKwh < 0 ? remaining / tier.price : Math.min(remaining / tier.price, tier.endKwh - tier.startKwh);
            usage += tierRange;
            remaining -= tierRange * tier.price;
        }
        // 保留2位小数避免浮点误差
        return Math.round(usage * 100.0) / 100.0;
    }

    public static class PriceTier {
        public double startKwh;
        public double endKwh;
        public double price;
        public PriceTier(double s, double e, double p) { startKwh = s; endKwh = e; price = p; }
        public String getLabel() { return endKwh < 0 ? startKwh + "以上" : startKwh + "-" + endKwh; }
    }
}