package com.weather.app;
import org.junit.jupiter.api.BeforeAll;

import org.junit.jupiter.api.Test;

import java.io.*;

import java.net.ServerSocket;

import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;



class ContentServerTest {



    private static Thread serverThread;



    @BeforeAll

    static void startServer() {

        serverThread = new Thread(() -> {

            try {

                AggregationServer.main(new String[] { "4568" });

            } catch (IOException e) {

                e.printStackTrace();

            }

        });

        serverThread.start();



        // Give the server time to start

        try {

            Thread.sleep(2000);  // Adjust sleep time as necessary

        } catch (InterruptedException e) {

            e.printStackTrace();

        }

    }



    @Test

    void testContentServerSendData() throws IOException {

        String[] args = { "localhost:4568", "testWeatherData.txt" };

        ContentServer.main(args);



        // Now check if the data was received by the AggregationServer

        Socket socket = new Socket("localhost", 4568);

        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));



        // Send GET request

        out.println("GET /weather.json HTTP/1.1");

        out.println("Host: localhost");

        out.println("Accept: application/json");

        out.println("Lamport-Clock: 3");

        out.println();



        // Read the response

        String statusLine = in.readLine();

        assertTrue(statusLine.contains("200")); // Ensure we get OK status



        // Read JSON body

        StringBuilder responseBody = new StringBuilder();

        String line;

        while ((line = in.readLine()) != null) {

            responseBody.append(line);

        }



        assertTrue(responseBody.toString().contains("Test Station"));

        assertTrue(responseBody.toString().contains("Test State"));



        socket.close();

    }



    @Test

    void testContentServerInvalidFileFormat() throws IOException {

        // Prepare an invalid file for content server

        try (PrintWriter writer = new PrintWriter(new FileWriter("invalidWeatherData.txt"))) {

            writer.println("invalid data");

        }



        String[] args = { "localhost:4568", "invalidWeatherData.txt" };

        ContentServer.main(args);



        // Since there's no id, the entry should be rejected

        Socket socket = new Socket("localhost", 4568);

        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));



        // Send GET request to check if no invalid data was stored

        out.println("GET /weather.json HTTP/1.1");

        out.println("Host: localhost");

        out.println("Accept: application/json");

        out.println("Lamport-Clock: 3");

        out.println();



        // Read the response

        StringBuilder responseBody = new StringBuilder();

        String line;

        while ((line = in.readLine()) != null) {

            responseBody.append(line);

        }



        // Ensure the invalid data wasn't stored

        assertFalse(responseBody.toString().contains("invalid data"));



        socket.close();

    }

   

}