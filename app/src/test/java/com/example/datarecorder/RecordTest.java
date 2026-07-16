package com.example.datarecorder;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Record模型单元测试
 * 验证预付费/后付费diff计算逻辑
 */
public class RecordTest {

    private Meter createElectricPrepaid() {
        Meter m = new Meter();
        m.setMeterType("electric");
        m.setBillingMode("prepaid");
        m.setDefaultTiers();
        return m;
    }

    private Meter createElectricPostpaid() {
        Meter m = new Meter();
        m.setMeterType("electric");
        m.setBillingMode("postpaid");
        m.setDefaultTiers();
        return m;
    }

    // ===== 预付费 diff 计算 =====

    @Test
    public void testPrepaidDiff_basicBalanceDrop() {
        Meter m = createElectricPrepaid();
        Record r = new Record();
        r.setBalance(80); // 余额从100降到80
        r.setReading(0);
        r.calculateDiffPrepaid(100, 0, m);
        // 费用 = 100 - 80 = 20元
        assertEquals(20.0, r.getCostDiff(), 0.01);
    }

    @Test
    public void testPrepaidDiff_withReading() {
        Meter m = createElectricPrepaid();
        Record r = new Record();
        r.setBalance(80);
        r.setReading(150); // 读数从100到150
        r.calculateDiffPrepaid(100, 100, m);
        // 用量 = 150 - 100 = 50 kWh
        assertEquals(50.0, r.getUsageDiff(), 0.01);
        // 费用 = 100 - 80 = 20元（余额差，不是用量*单价）
        assertEquals(20.0, r.getCostDiff(), 0.01);
    }

    @Test
    public void testPrepaidDiff_currentPeriodUsage() {
        Meter m = createElectricPrepaid();
        Record r = new Record();
        r.setBalance(80);
        r.setReading(50); // 当期用量=50kWh（非累计，录入值即用量）
        r.calculateDiffPrepaid(100, 0, m); // prevReading=0表示非累计模式
        // 用量 = 50 kWh（录入值）
        assertEquals(50.0, r.getUsageDiff(), 0.01);
        // 费用 = 100 - 80 = 20元（余额差，不是50*0.56=28元）
        assertEquals(20.0, r.getCostDiff(), 0.01);
    }

    @Test
    public void testPrepaidDiff_noReading_reverseUsageFromCost() {
        Meter m = createElectricPrepaid();
        Record r = new Record();
        r.setBalance(80); // 费用=20元
        r.setReading(0);
        r.calculateDiffPrepaid(100, 0, m);
        // 20元全在第一档：用量 = 20/0.56 ≈ 35.71 kWh
        double expectedUsage = 20.0 / 0.56;
        assertEquals(Math.round(expectedUsage * 100.0) / 100.0, r.getUsageDiff(), 0.01);
    }

    @Test
    public void testPrepaidDiff_balanceIncrease_noCost() {
        Meter m = createElectricPrepaid();
        Record r = new Record();
        r.setBalance(120); // 余额增加（充值后）
        r.setReading(0);
        r.calculateDiffPrepaid(100, 0, m);
        // 余额增加不算费用
        assertEquals(0.0, r.getCostDiff(), 0.01);
        assertEquals(0.0, r.getUsageDiff(), 0.01);
    }

    // ===== 后付费 diff 计算 =====

    @Test
    public void testPostpaidDiff_basicReadingIncrease() {
        Meter m = createElectricPostpaid();
        Record r = new Record();
        r.setBalance(200); // 读数从100到200
        r.calculateDiffPostpaid(100, m);
        // 用量 = 200 - 100 = 100 kWh
        assertEquals(100.0, r.getUsageDiff(), 0.01);
        // 费用 = 100 * 0.56 = 56元（第一档）
        assertEquals(56.0, r.getCostDiff(), 0.01);
    }

    @Test
    public void testPostpaidDiff_crossTier() {
        Meter m = createElectricPostpaid();
        Record r = new Record();
        r.setBalance(200); // 读数从0到200
        r.calculateDiffPostpaid(0, m);
        // 用量 = 200 kWh
        assertEquals(200.0, r.getUsageDiff(), 0.01);
        // 费用 = 180*0.56 + 20*0.61
        double expected = 180 * 0.56 + 20 * 0.61;
        assertEquals(expected, r.getCostDiff(), 0.01);
    }

    // ===== 充值记录 =====

    @Test
    public void testRechargeMark() {
        Record r = new Record();
        r.markAsRecharge(50.0);
        assertTrue(r.isRecharge());
        assertEquals(50.0, r.getRechargeAmount(), 0.01);
        assertEquals(0.0, r.getUsageDiff(), 0.01);
        assertEquals(0.0, r.getCostDiff(), 0.01);
    }

    @Test
    public void testFirstRecordMark() {
        Record r = new Record();
        r.markAsFirst();
        assertEquals(0.0, r.getUsageDiff(), 0.001);
        assertEquals(0.0, r.getCostDiff(), 0.001);
    }

    // ===== 格式化 =====

    @Test
    public void testFormattedBalance() {
        Record r = new Record();
        r.setBalance(123.456);
        assertEquals("123.46", r.getFormattedBalance());
    }

    @Test
    public void testFormattedUsageDiff() {
        Record r = new Record();
        r.setUsageDiff(50.5);
        assertEquals("50.50", r.getFormattedUsageDiff());
        r.setUsageDiff(0);
        assertEquals("--", r.getFormattedUsageDiff());
    }

    @Test
    public void testIsConsumed() {
        Record r = new Record();
        r.setUsageDiff(10); r.setCostDiff(5);
        assertTrue(r.isConsumed());
        r.setUsageDiff(0); r.setCostDiff(0);
        assertFalse(r.isConsumed());
    }
}
