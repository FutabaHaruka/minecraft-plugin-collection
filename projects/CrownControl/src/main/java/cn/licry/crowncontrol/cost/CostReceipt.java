package cn.licry.crowncontrol.cost;

/** Receipt for plugin-managed currencies only. Pixelmon manages the native crown item. */
public final class CostReceipt {
    private double money;
    private int points;

    public void setMoney(double money) { this.money = money; }
    public void setPoints(int points) { this.points = points; }
    public double getMoney() { return money; }
    public int getPoints() { return points; }
    public boolean hasCost() { return money > 0.0D || points > 0; }
}
