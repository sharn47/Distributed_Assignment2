package com.weather.app;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

import static org.mockito.Mockito.*;

public class AggregationServerTest {

    @Test
    public void testHandleClient() throws Exception {
        // Mock client socket and IO streams
        Socket mockClientSocket = Mockito.mock(Socket.class);
        BufferedReader mockReader = mock(BufferedReader.class);
        PrintWriter mockWriter = mock(PrintWriter.class);

        // Simulate client request
        when(mockReader.readLine()).thenReturn("PUT / HTTP/1.1", "", "{\"id\":\"12345\", \"temperature\":\"25.3\"}");
        when(mockReader.read()).thenReturn(-1);

        // Mock socket creation and IO streams
        AggregationServer server = Mockito.spy(AggregationServer.class);
        doReturn(mockClientSocket).when(server).createSocket(any());

        // Test the handleClient method
        server.handleClient(mockClientSocket);

        // Verify interactions
        verify(mockWriter, times(1)).println(anyString());
    }

    @Test
    public void testLoadFromFile() throws Exception {
        // Create a temporary file with weather data
        File tempFile = File.createTempFile("weatherData", ".json");
        FileWriter writer = new FileWriter(tempFile);
        writer.write("[{\"id\": \"12345\", \"temperature\": \"25.3\"}]");
        writer.close();

        // Mock file operations
        AggregationServer server = Mockito.spy(AggregationServer.class);
        doReturn(tempFile).when(server).getDataFile();

        // Test loading data from the file
        server.loadFromFile();

        // Check that the data was loaded correctly
        assertTrue(AggregationServer.weatherData.containsKey("12345"));

        // Clean up temp file
        tempFile.delete();
    }

    @Test
    public void testCleanExpiredData() throws Exception {
        // Insert mock weather data with an old timestamp
        AggregationServer.weatherData.put("12345", createMockWeatherData(60_000)); // 60 seconds old
        AggregationServer.cleanExpiredData();

        // Verify that the old data was removed
        assertFalse(AggregationServer.weatherData.containsKey("12345"));
    }

    private JsonObject createMockWeatherData(long ageMillis) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("id", "12345");
        jsonObject.addProperty("temperature", "25.3");
        jsonObject.addProperty("timestamp", System.currentTimeMillis() - ageMillis);
        return jsonObject;
    }
}
