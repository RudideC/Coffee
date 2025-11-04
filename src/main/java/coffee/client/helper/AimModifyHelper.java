package coffee.client.helper;

public class AimModifyHelper {
    private static float currentMultiplierX = 1f;
    private static float currentMultiplierY = 1f;
    private static boolean enabled = false;
    
    public static void setSlowdownMultiplierX(float multiplier) {
        currentMultiplierX = multiplier;
    }
    
    public static void setSlowdownMultiplierY(float multiplier) {
        currentMultiplierY = multiplier;
    }

    public static float getSlowdownMultiplierX() {
        return enabled ? currentMultiplierX : 1f;
    }
    
    public static float getSlowdownMultiplierY() {
        return enabled ? currentMultiplierY : 1f;
    }

    public static void setEnabled(boolean enable) {
        enabled = enable;
    }
    
    public static boolean isEnabled() {
        return enabled;
    }
}