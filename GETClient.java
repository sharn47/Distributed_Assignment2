import java.io.*;
import java.net.*;

public class GETClient {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java GETClient <server-url> [station-id]");
            return;
        }

        String serverUrl = args[0];
        String stationId = args.length > 1 ? args[1] : null;

        try {
            // Parse the server URL
            URL url = new URL(serverUrl);

            // Establish a socket connection to the server
            Socket socket = new Socket(url.getHost(), url.getPort() == -1 ? 80 : url.getPort());

            // Set up input and output streams
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Build the GET request path
            String path = url.getPath();
            if (stationId != null) {
                path += "?id=" + stationId;
            }

            // Send the GET request
            out.println("GET " + path + " HTTP/1.1");
            out.println("Host: " + url.getHost());
            out.println("Connection: close");
            out.println();

            // Read and print the status line
            String statusLine = in.readLine();
            if (statusLine == null) {
                System.out.println("No response from server.");
                return;
            }
            System.out.println(statusLine);

            // Read and print the headers
            String headerLine;
            while (!(headerLine = in.readLine()).equals("")) {
                System.out.println(headerLine);
            }
            System.out.println();

            // Read and print the response body
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println(line);
            }

            // Close the socket
            socket.close();

        } catch (Exception e) {
            System.out.println("Client exception: " + e.getMessage());
        }
    }
}
