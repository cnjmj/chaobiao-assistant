package com.example.datarecorder;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Meter模型单元测试
 * 验证分段计价、费用反推用量等核心计算逻辑
 */
public class MeterTest {

    private Meter createElectricMeter() {
        Meter m = new Meter();
        m.setMeterType("electric");
        m.setDefaultTiers();
        return m;
    }

    private Meter createWaterMeter() {
        Meter m = new Meter();
        m.setMeterType("water");
        m.setDefaultTiers();
        return m;
    }

    private Meter createGasMeter() {
        Meter m = new Meter();
        m.setMeterType("gas");
        m.setDefaultTiers();
        return m;
    }

    // ===== 分段计价 calculateCost =====

    @Test
    public void testElectricCost_firstTier() {
        Meter m = createElectricMeter();
        // 第一档：0-180 kWh, 0.56元/kWh
        assertEquals(180 * 0.56, m.calculateCost(180), 0.01);
    }

    @Test
    public void testElectricCost_secondTier() {
        Meter m = createElectricMeter();
        // 200 kWh = 180*0.56 + 20*0.61
        double expected = 180 * 0.56 + 20 * 0.61;
        assertEquals(expected, m.calculateCost(200), 0.01);
    }

    @Test
    public void testElectricCost_thirdTier() {
        Meter m = createElectricMeter();
        // 400 kWh = 180*0.56 + 170*0.61 + 50*0.86
        double expected = 180 * 0.56 + 170 * 0.61 + 50 * 0.86;
        assertEquals(expected, m.calculateCost(400), 0.01);
    }

    @Test
    public void testElectricCost_zeroUsage() {
        Meter m = createElectricMeter();
        assertEquals(0.0, m.calculateCost(0), 0.001);
    }

    @Test
    public void testWaterCost_tierBoundary() {
        Meter m = createWaterMeter();
        // 水表：0-15吨 2.45元, 15-22吨 3.45元, 22以上 4.45元
        double cost15 = 15 * 2.45;
        assertEquals(cost15, m.calculateCost(15), 0.01);
        double cost22 = 15 * 2.45 + 7 * 3.45;
        assertEquals(cost22, m.calculateCost(22), 0.01);
    }

    @Test
    public void testGasCost_basic() {
        Meter m = createGasMeter();
        // 气：0-300 m3 2.03元
        assertEquals(300 * 2.03, m.calculateCost(300), 0.01);
    }

    // ===== 费用反推用量 calculateUsageFromCost =====

    @Test
    public void testUsageFromCost_firstTier() {
        Meter m = createElectricMeter();
        double cost = 100 * 0.56; // 100 kWh 在第一档
        double usage = m.calculateUsageFromCost(cost);
        assertEquals(100.0, usage, 0.01);
    }

    @Test
    public void testUsageFromCost_crossTier() {
        Meter m = createElectricMeter();
        // 200 kWh: cost = 180*0.56 + 20*0.61
        double cost = 180 * 0.56 + 20 * 0.61;
        double usage = m.calculateUsageFromCost(cost);
        assertEquals(200.0, usage, 0.01);
    }

    @Test
    public void testUsageFromCost_thirdTier() {
        Meter m = createElectricMeter();
        // 400 kWh: cost = 180*0.56 + 170*0.61 + 50*0.86
        double cost = 180 * 0.56 + 170 * 0.61 + 50 * 0.86;
        double usage = m.calculateUsageFromCost(cost);
        assertEquals(400.0, usage, 0.01);
    }

    @Test
    public void testUsageFromCost_zero() {
        Meter m = createElectricMeter();
        assertEquals(0.0, m.calculateUsageFromCost(0), 0.001);
        assertEquals(0.0, m.calculateUsageFromCost(-10), 0.001);
    }

    // ===== 双向一致性：cost→usage→cost =====

    @Test
    public void testRoundTrip_electric() {
        Meter m = createElectricMeter();
        for (double usage = 50; usage <= 500; usage += 50) {
            double cost = m.calculateCost(usage);
            double usageBack = m.calculateUsageFromCost(cost);
            assertEquals(usage, usageBack, 0.1);
        }
    }

    @Test
    public void testRoundTrip_water() {
        Meter m = createWaterMeter();
        for (double usage = 5; usage <= 30; usage += 5) {
            double cost = m.calculateCost(usage);
            double usageBack = m.calculateUsageFromCost(cost);
            assertEquals(usage, usageBack, 0.1);
        }
    }

    // ===== 标签和属性 =====

    @Test
    public void testTypeLabels() {
        Meter e = new Meter(); e.setMeterType("electric");
        assertEquals("电表", e.getTypeLabel());
        Meter w = new Meter(); w.setMeterType("water");
        assertEquals("水表", w.getTypeLabel());
        Meter g = new Meter(); g.setMeterType("gas");
        assertEquals("气表", g.getTypeLabel());
        Meter o = new Meter(); o.setMeterType("other");
        assertEquals("其他", o.getTypeLabel());
    }

    @Test
    public void testBillingModeLabels() {
        Meter pre = new Meter(); pre.setBillingMode("prepaid");
        assertEquals("预付费", pre.getBillingModeLabel());
        assertTrue(pre.isPrepaid());
        Meter post = new Meter(); post.setBillingMode("postpaid");
        assertEquals("后付费", post.getBillingModeLabel());
        assertFalse(post.isPrepaid());
    }

    @Test
    public void testDefaultUnits() {
        Meter e = new Meter(); e.setMeterType("electric"); e.setDefaultTiers();
        assertEquals("kWh", e.getUnit());
        Meter w = new Meter(); w.setMeterType("water"); w.setDefaultTiers();
        assertEquals("吨", w.getUnit());
        Meter g = new Meter(); g.setMeterType("gas"); g.setDefaultTiers();
        assertEquals("m\u00B3", g.getUnit());
    }

    // ===== PriceTier =====

    @Test
    public void testPriceTierLabel() {
        Meter.PriceTier finite = new Meter.PriceTier(0, 180, 0.56);
        assertEquals("0.0-180.0", finite.getLabel());
        Meter.PriceTier infinite = new Meter.PriceTier(350, -1, 0.86);
        assertEquals("350.0以上", infinite.getLabel());
    }
}
