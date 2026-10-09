package com.WynnRunica;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class DialogueRevealController {
    public enum Control { UNKNOWN, TYPING, READY }
    public enum Action { NONE, PRESS, RELEASE }
    private enum Phase { IDLE, TRACKING, PRESSED, COMPLETE, CHOICES }

    private final Consumer<String> logger;
    private Phase phase = Phase.IDLE;
    private String body = "";
    private String speaker = "";
    private Control control = Control.UNKNOWN;
    private long pageStartedAt;
    private long lastObservedAt;
    private long lastGrowthAt;
    private long lastGrowthTick = -1;
    private long batchTick = -1;
    private long pressedTick;
    private long pressedAt;
    private int growthInBatch;
    private int growthTicks;
    private boolean growing;
    private boolean attemptedPress;
    private long pageStartTick;
    private int expectedLength = -1;
    private int latencyTicks = 2;

    public DialogueRevealController(Consumer<String> logger) {
        this.logger = logger;
    }

    public Action observe(String nextBody, String nextSpeaker, Control nextControl,
                          boolean choices, boolean dialogue, long tick, long now) {
        if (!dialogue) return reset("OVERLAY_ENDED");
        if (nextBody.isEmpty()) {
            boolean release = phase == Phase.PRESSED;
            if (phase != Phase.IDLE) phase = Phase.COMPLETE;
            growing = false;
            growthTicks = 0;
            return release ? Action.RELEASE : Action.NONE;
        }
        lastObservedAt = now;
        boolean wasPressed = phase == Phase.PRESSED;
        if (choices) {
            if (phase != Phase.CHOICES) event("CHOICES", nextBody, nextControl, tick);
            phase = Phase.CHOICES;
            body = nextBody;
            speaker = nextSpeaker;
            control = nextControl;
            growing = false;
            growthTicks = 0;
            return wasPressed ? Action.RELEASE : Action.NONE;
        }

        boolean newPage = phase == Phase.IDLE
                || !speaker.equals(nextSpeaker)
                || (control == Control.READY && nextControl == Control.TYPING)
                || (phase == Phase.CHOICES && !body.equals(nextBody))
                || (!nextBody.startsWith(body) && !body.startsWith(nextBody));
        if (newPage) {
            phase = Phase.TRACKING;
            body = nextBody;
            speaker = nextSpeaker;
            control = nextControl;
            pageStartedAt = now;
            lastGrowthAt = now;
            lastGrowthTick = -1;
            batchTick = -1;
            growthInBatch = 0;
            growthTicks = 0;
            growing = false;
            attemptedPress = false;
            pageStartTick = tick;
            expectedLength = -1;
            event("PAGE_START", body, control, tick);
        } else if (nextBody.length() < body.length()) {
            control = nextControl;
            growing = false;
            growthTicks = 0;
            attemptedPress = false;
            if (wasPressed || nextControl == Control.READY) phase = Phase.COMPLETE;
            return wasPressed ? Action.RELEASE : Action.NONE;
        } else {
            int delta = nextBody.codePointCount(0, nextBody.length())
                    - body.codePointCount(0, body.length());
            body = nextBody;
            control = nextControl;
            growing = delta > 0;
            if (delta > 0) {
                if (batchTick != tick) {
                    batchTick = tick;
                    growthInBatch = 0;
                }
                growthInBatch += delta;
                boolean healthy = delta <= 4 && growthInBatch <= 4
                        && tick - lastGrowthTick <= 4 && nextControl == Control.TYPING;
                if (lastGrowthTick < 0) healthy = delta <= 4 && growthInBatch <= 4
                        && nextControl == Control.TYPING;
                if (!healthy) {
                    growthTicks = 0;
                    if (phase == Phase.TRACKING) event("GROWTH_BURST", body, control, tick);
                } else if (lastGrowthTick != tick) {
                    growthTicks++;
                }
                lastGrowthTick = tick;
                lastGrowthAt = now;
                if (wasPressed && delta >= 6) {
                    phase = Phase.COMPLETE;
                    event("REVEALED_RELEASE", body, control, tick);
                    return Action.RELEASE;
                }
            }
        }

        if (nextControl == Control.READY) {
            if (phase != Phase.COMPLETE) event("READY", body, control, tick);
            phase = Phase.COMPLETE;
            growing = false;
            growthTicks = 0;
        } else if (nextControl == Control.UNKNOWN) {
            growthTicks = 0;
            growing = false;
            if (wasPressed) phase = Phase.COMPLETE;
        }
        if (wasPressed && (newPage || nextControl != Control.TYPING)) return Action.RELEASE;
        return Action.NONE;
    }

    public void expect(int length) {
        expectedLength = length;
    }

    public void latency(int ticks) {
        latencyTicks = Math.max(0, ticks);
    }

    private boolean enoughLeft(long tick, int bodyLen) {
        if (expectedLength <= 0) return false;
        double speed = Math.max(1.0, bodyLen / (double) Math.max(1, tick - pageStartTick));
        return (expectedLength - bodyLen) / speed > latencyTicks + 3;
    }

    public Action tick(long tick, long now, boolean enabled, boolean physicalSneak,
                       boolean screenOpen) {
        if (!enabled) return reset("DISABLED");
        if (phase == Phase.PRESSED && (tick - pressedTick >= 4
                || now - pressedAt >= 200_000_000L || physicalSneak || screenOpen)) {
            phase = Phase.COMPLETE;
            event("RELEASE", body, control, tick);
            return Action.RELEASE;
        }
        if (phase != Phase.TRACKING) return Action.NONE;
        if (physicalSneak || screenOpen) {
            phase = Phase.COMPLETE;
            event("MANUAL_INPUT", body, control, tick);
            return Action.NONE;
        }

        long pageAge = now - pageStartedAt;
        long timeSinceObserved = now - lastObservedAt;
        long timeSinceGrowth = now - lastGrowthAt;
        long ticksSinceGrowth = tick - lastGrowthTick;
        int bodyLen = body.codePointCount(0, body.length());

        if (pageAge > 2_000_000_000L) {
            phase = Phase.COMPLETE;
            event("PAGE_TIMEOUT", body, control, tick);
            return Action.NONE;
        }

        if (attemptedPress) return Action.NONE;

        boolean hasEvidence = control == Control.TYPING && growing && growthTicks >= 2;
        boolean timingOk = timeSinceObserved <= 150_000_000L && timeSinceGrowth <= 150_000_000L;
        boolean recentGrowth = ticksSinceGrowth <= 2;
        boolean minLength = bodyLen >= 4;

        boolean growthFallback = control == Control.TYPING && growthTicks >= 1
                && pageAge >= 350_000_000L && pageAge <= 1_500_000_000L && bodyLen >= 10;

        boolean burstFallback = control == Control.TYPING && growthTicks == 0 && bodyLen >= 20
                && pageAge >= 100_000_000L && pageAge <= 500_000_000L;

        if (((hasEvidence && timingOk && recentGrowth && minLength) || growthFallback || burstFallback)
                && enoughLeft(tick, bodyLen)) {
            phase = Phase.PRESSED;
            pressedTick = tick;
            pressedAt = now;
            attemptedPress = true;
            String eventName = burstFallback ? "PRESS_BURST" : (growthFallback ? "PRESS_FALLBACK" : "PRESS");
            event(eventName, body, control, tick);
            return Action.PRESS;
        }

        return Action.NONE;
    }

    public Action reset(String reason) {
        boolean release = phase == Phase.PRESSED;
        if (phase != Phase.IDLE) logger.accept(reason + " phase=" + phase);
        phase = Phase.IDLE;
        body = "";
        speaker = "";
        control = Control.UNKNOWN;
        growing = false;
        growthTicks = 0;
        attemptedPress = false;
        return release ? Action.RELEASE : Action.NONE;
    }

    private void event(String name, String value, Control signal, long tick) {
        logger.accept(name + " tick=" + tick + " control=" + signal + " phase=" + phase
                + " growthTicks=" + growthTicks + " batchGrowth=" + growthInBatch
                + " len=" + value.codePointCount(0, value.length())
                + " speaker='" + speaker + "' body='"
                + value.substring(0, Math.min(180, value.length())) + "'");
    }

    static final int LINE_PREFIX = 10;
    private static final Map<String, Integer> LINE_LENGTHS = new HashMap<>();

    public static synchronized void clearLines() {
        LINE_LENGTHS.clear();
    }

    public static synchronized void rememberLine(String line) {
        String compactLine = compactLine(line);
        if (compactLine.length() < LINE_PREFIX) return;
        LINE_LENGTHS.merge(compactLine.substring(0, LINE_PREFIX), compactLine.codePointCount(0, compactLine.length()), Math::min);
    }

    public static synchronized int lineLength(String typed) {
        String compactLine = compactLine(typed);
        if (compactLine.length() < LINE_PREFIX) return -1;
        return LINE_LENGTHS.getOrDefault(compactLine.substring(0, LINE_PREFIX), -1);
    }

    static String compactLine(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
