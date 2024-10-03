package com.weather.app;

import java.util.Map;

public class WeatherData {
    private Map<String, String> data;
    private int lamportTime;
    private long lastUpdated;

    public WeatherData(Map<String, String> data, int lamportTime) {
        this.data = data;
        this.lamportTime = lamportTime;
        this.lastUpdated = System.currentTimeMillis();
    }

    public Map<String, String> getData() {
        return data;
    }

    public int getLamportTime() {
        return lamportTime;
    }

    public void updateLastUpdated() {
        this.lastUpdated = System.currentTimeMillis();
    }

    public long getLastUpdated() {
        return lastUpdated;
    }
}
