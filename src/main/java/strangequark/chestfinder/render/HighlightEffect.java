package strangequark.chestfinder.render;


public final class HighlightEffect {
    public static final long TIME_ON = 750;
    public static final long TIME_OFF = 250;
    public static final int CYCLES = 5;
    public static final long CYCLE_DURATION = TIME_ON + TIME_OFF;
    public static final long MAX_DURATION = CYCLES * CYCLE_DURATION;
    
    public static boolean shouldRender(long elapsed) {
        return elapsed % CYCLE_DURATION < TIME_ON;
    }

    public static boolean isExpired(long elapsed) {
        return elapsed > MAX_DURATION;
    }
}

