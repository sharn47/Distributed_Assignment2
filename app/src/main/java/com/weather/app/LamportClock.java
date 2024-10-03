package com.weather.app;

public class LamportClock {
    private int clock;

    public synchronized void tick() {
        clock++;
    }

    public synchronized void update(int receivedClock) {
        clock = Math.max(clock, receivedClock) + 1;
    }

    public synchronized int getClock() {
        return clock;
    }
}