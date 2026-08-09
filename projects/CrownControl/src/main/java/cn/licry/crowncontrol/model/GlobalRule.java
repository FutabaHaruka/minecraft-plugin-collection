package cn.licry.crowncontrol.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** One global policy shared by every native Pixelmon crown. */
public final class GlobalRule {
    private final boolean enabled;
    private final int cooldownSeconds;
    private final CooldownOn cooldownOn;
    private final Set<PokemonCategory> allowedCategories;
    private final double money;
    private final int points;
    private final double successRate;
    private final double failureRate;
    private final ConsumeOn consumeOn;
    private final String validationError;

    public GlobalRule(boolean enabled, int cooldownSeconds, CooldownOn cooldownOn,
                      Set<PokemonCategory> allowedCategories, double money, int points,
                      double successRate, double failureRate, ConsumeOn consumeOn,
                      String validationError) {
        this.enabled = enabled;
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
        this.cooldownOn = cooldownOn == null ? CooldownOn.SUCCESS : cooldownOn;
        this.allowedCategories = allowedCategories == null || allowedCategories.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.allOf(PokemonCategory.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(allowedCategories));
        this.money = Math.max(0.0D, money);
        this.points = Math.max(0, points);
        this.successRate = successRate;
        this.failureRate = failureRate;
        this.consumeOn = consumeOn == null ? ConsumeOn.ATTEMPT : consumeOn;
        this.validationError = validationError;
    }

    public boolean isValid() { return validationError == null || validationError.isEmpty(); }
    public boolean isEnabled() { return enabled; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public CooldownOn getCooldownOn() { return cooldownOn; }
    public Set<PokemonCategory> getAllowedCategories() { return allowedCategories; }
    public double getMoney() { return money; }
    public int getPoints() { return points; }
    public double getSuccessRate() { return successRate; }
    public double getFailureRate() { return failureRate; }
    public ConsumeOn getConsumeOn() { return consumeOn; }
    public String getValidationError() { return validationError; }
}
