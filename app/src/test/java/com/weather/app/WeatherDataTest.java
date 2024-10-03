package com.weather.app;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;

public class WeatherDataTest {

    @Test
    public void testWeatherDataCreation() {
        Map<String, String> data = new HashMap<>();
        data.put("temp", "25");
        WeatherData weatherData = new WeatherData(data, 1);

        assertEquals("25", weatherData.getData().get("temp"));
        assertEquals(1, weatherData.getLamportTime());
        assertTrue(weatherData.getLastUpdated() > 0);
    }

    @Test
    public void testWeatherDataUpdateLastUpdated() throws InterruptedException {
        Map<String, String> data = new HashMap<>();
        WeatherData weatherData = new WeatherData(data, 1);
        long initialTime = weatherData.getLastUpdated();

        Thread.sleep(10);
        weatherData.updateLastUpdated();
        assertTrue(weatherData.getLastUpdated() > initialTime);
    }
}
