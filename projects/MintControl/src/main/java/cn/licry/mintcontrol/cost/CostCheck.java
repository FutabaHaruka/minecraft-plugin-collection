package cn.licry.mintcontrol.cost;

public final class CostCheck {
    private final boolean success;
    private final String messageKey;
    private final double amount;

    private CostCheck(boolean success, String messageKey, double amount) {
        this.success = success;
        this.messageKey = messageKey;
        this.amount = amount;
    }

    public static CostCheck ok() { return new CostCheck(true, "", 0.0D); }
    public static CostCheck fail(String key, double amount) { return new CostCheck(false, key, amount); }
    public boolean isSuccess() { return success; }
    public String getMessageKey() { return messageKey; }
    public double getAmount() { return amount; }
}
