package com.WynnRunica;

import java.util.Arrays;

public final class ChoiceMarqueeTest {
    public static void main(String[] args) {
        ChoiceTicker.Marquee animation = new ChoiceTicker.Marquee(0);
        expect(animation.startAt(ms(999), 10) == 0, "Initial pause");
        expect(animation.startAt(ms(1000), 10) == 1, "First character");
        expect(animation.startAt(ms(1110), 10) == 2, "Forward cadence");
        expect(animation.startAt(ms(1990), 10) == 10, "Last window");
        expect(animation.startAt(ms(3489), 10) == 10, "Tail remains for 1.5 seconds");
        expect(animation.startAt(ms(3665), 10) < 5, "Fast eased return");
        expect(animation.startAt(ms(3840), 10) == 0, "Return to the beginning");
        expect(animation.startAt(ms(4800), 10) == 0, "Pause after returning");
        expect(animation.startAt(ms(10000), 0) == 0, "Fitting text does not move");
        expect(animation.startAt(-1, 10) == 0, "Clock before initialization");

        int previous = 10;
        for (int time = 3490; time < 3840; time++) {
            int start = animation.startAt(ms(time), 10);
            expect(start <= previous && start >= 0, "Return never reverses direction");
            previous = start;
        }
        for (int fps : new int[]{30, 60, 144, 240}) {
            for (int frame = 0; frame < fps * 9; frame++) {
                long now = frame * 1_000_000_000L / fps;
                expect(animation.startAt(now, 10) == new ChoiceTicker.Marquee(0).startAt(now, 10),
                        "Position depends on time, not previous frames");
            }
        }

        int[] widths = {6, 3, 8, 5, 2, 7, 4};
        expect(ChoiceTicker.Marquee.maxStart(widths, 40) == 0, "Whole text fits");
        expect(ChoiceTicker.Marquee.maxStart(widths, 13) == 4, "Earliest complete tail");
        expect(ChoiceTicker.Marquee.endAt(widths, 4, 13) == widths.length, "Tail is not truncated");
        expect(ChoiceTicker.Marquee.endAt(widths, 0, 13) == 2, "No overflowing glyph");
        expect(ChoiceTicker.Marquee.maxStart(new int[0], 13) == 0, "Empty text");
        int[] uniform = new int[100];
        Arrays.fill(uniform, 6);
        int last = ChoiceTicker.Marquee.maxStart(uniform, 60);
        for (int start = 0; start <= last; start++) {
            expect(ChoiceTicker.Marquee.endAt(uniform, start, 60) - start == 10,
                    "Window does not delete trailing characters on its own");
        }
        System.out.println("Choice marquee checks passed");
    }

    private static long ms(long value) {
        return value * 1_000_000;
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
