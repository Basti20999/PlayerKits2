package pk.ajneb97.model;

import java.util.List;

public class PlayerDataKit {

    private String name;
    private long cooldown; //Cooldown calculated
    private boolean oneTime;
    private boolean bought;
    private List<Integer> customSlots;

    public PlayerDataKit(String name) {
        this.name = name;
        this.cooldown = 0;
        this.oneTime = false;
        this.bought = false;
        this.customSlots = null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getCooldown() {
        return cooldown;
    }

    public void setCooldown(long cooldown) {
        this.cooldown = cooldown;
    }

    public boolean isOneTime() {
        return oneTime;
    }

    public void setOneTime(boolean oneTime) {
        this.oneTime = oneTime;
    }

    public boolean isBought() {
        return bought;
    }

    public void setBought(boolean bought) {
        this.bought = bought;
    }

    public List<Integer> getCustomSlots() {
        return customSlots;
    }

    public void setCustomSlots(List<Integer> customSlots) {
        this.customSlots = customSlots;
    }
}
