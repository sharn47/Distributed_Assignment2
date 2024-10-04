package com.weather.app;

import org.junit.jupiter.api.Test;



import java.io.BufferedReader;

import java.io.BufferedWriter;

import java.io.ByteArrayOutputStream;

import java.io.IOException;

import java.io.InputStreamReader;

import java.io.OutputStreamWriter;

import java.io.PrintStream;

import java.net.ServerSocket;

import java.net.Socket;



import static org.junit.jupiter.api.Assertions.*;



class GETClientTest {



    @Test

    void testGetClientReceivesData() throws IOException {

        // Test case for retrieving data from AggregationServer

        String[] args = { "localhost:4568" };

        GETClient.main(args);



    }

    

    @Test

    public void testGETClientHandlesValidServerResponse() throws Exception {

        // Start a mock server that will respond with JSON data

        Thread mockServerThread = new Thread(() -> {

            try (ServerSocket serverSocket = new ServerSocket(8081)) {

                Socket socket = serverSocket.accept();

                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));



                // Read the request (you can add more processing if needed)

                while (!in.readLine().isEmpty()) {

                    // Just read the request

                }



                // Mock a 200 OK response with a JSON body

                String jsonResponse = "[{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"state\":\"SA\",\"air_temp\":\"13.3\"}]";

                out.write("HTTP/1.1 200 OK\r\n");

                out.write("Content-Type: application/json\r\n");

                out.write("Content-Length: " + jsonResponse.length() + "\r\n");

                out.write("\r\n");

                out.write(jsonResponse);

                out.flush();



                socket.close();

            } catch (IOException e) {

                e.printStackTrace();

            }

        });



        // Start the mock server

        mockServerThread.start();

        Thread.sleep(500); // Wait for the mock server to start



        // Capture the output of the GETClient

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        PrintStream originalOut = System.out;

        System.setOut(new PrintStream(outputStream));



        // Run the GETClient and connect to the mock server

        String[] args = { "localhost:8081" };

        GETClient.main(args);



        // Restore the original output

        System.setOut(originalOut);



        // Verify the client output

        String output = outputStream.toString();

        assertTrue(output.contains("id: IDS60901"));

        assertTrue(output.contains("name: Adelaide"));

        assertTrue(output.contains("state: SA"));

        assertTrue(output.contains("air_temp: 13.3"));



        // Stop the mock server thread

        mockServerThread.interrupt();

    }

}