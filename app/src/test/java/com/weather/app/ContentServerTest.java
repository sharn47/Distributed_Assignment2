package com.weather.app;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ContentServerTest {

    @Test
    public void testSendData() throws Exception {
        // Mock socket and output stream
        Socket mockSocket = Mockito.mock(Socket.class);
        BufferedWriter mockWriter = mock(BufferedWriter.class);
        BufferedReader mockReader = mock(BufferedReader.class);
        
        // Simulate server response
        when(mockReader.readLine()).thenReturn("HTTP/1.1 200 OK", "");
        when(mockReader.ready()).thenReturn(false);
        
        // Mock the socket creation to return the mock socket
        ContentServer contentServer = Mockito.spy(ContentServer.class);
        doReturn(mockSocket).when(contentServer).createSocket(any(URL.class));

        // Mock input/output stream handling
        when(mockSocket.getOutputStream()).thenReturn(mock(OutputStream.class));
        when(mockSocket.getInputStream()).thenReturn(mock(InputStream.class));
        doReturn(mockWriter).when(contentServer).createBufferedWriter(any());
        doReturn(mockReader).when(contentServer).createBufferedReader(any());

        // Mock the file read operation
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put("id", "12345");
        dataMap.put("temperature", "25.3");

        // Test method
        contentServer.sendData("http://localhost:4567", "weatherData.txt");

        // Verify that PUT request is sent
        verify(mockWriter, times(1)).write(anyString());
    }

    @Test
    public void testReadDataFromFile() throws IOException {
        // Prepare sample content
        Path tempFile = Files.createTempFile("testWeatherData", ".txt");
        Files.write(tempFile, "id: 12345\ntemperature: 25.3\n".getBytes());

        // Test reading data from file
        Map<String, String> dataMap = ContentServer.readDataFromFile(tempFile.toString());
        assertEquals("12345", dataMap.get("id"));
        assertEquals("25.3", dataMap.get("temperature"));

        // Clean up temp file
        Files.delete(tempFile);
    }

    @Test
    public void testSendDataWithRetry() throws Exception {
        // Simulate a failure and retry logic
        ContentServer contentServer = Mockito.spy(ContentServer.class);
        doThrow(new IOException()).when(contentServer).sendData(anyString(), anyString());

        // Mock retry method
        boolean success = contentServer.sendDataWithRetry("http://localhost:4567", "weatherData.txt", 2);

        assertFalse(success);
        verify(contentServer, times(2)).sendData(anyString(), anyString());
    }
}
