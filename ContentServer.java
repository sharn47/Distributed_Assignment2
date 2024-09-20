import java.io.*;
import java.net.*;
import java.util.*;

public class ContentServer {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java ContentServer <server-url> <file-path>");
            return;
        }

        String serverUrl = args[0];
        String filePath = args[1];

        try {
            // Read data from file
            Map<String, String> weatherData = readDataFromFile(filePath);
            if (weatherData == null) {
                System.out.println("Failed to read data from file.");
                return;
            }

            String jsonData = convertToJson(weatherData);

            // Send PUT request
            sendPutRequest(serverUrl, jsonData);

        } catch (Exception e) {
            System.out.println("ContentServer exception: " + e.getMessage());
        }
    }

    private static Map<String, String> readDataFromFile(String filePath) {
        Map<String, String> dataMap = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] kv = line.split(":", 2);
                if (kv.length == 2) {
                    dataMap.put(kv[0].trim(), kv[1].trim());
                }
            }
            return dataMap;
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
            return null;
        }
    }
    // code to covert the data read from  file to Json format
    private static String convertToJson(Map<String, String> dataMap) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        Iterator<Map.Entry<String, String>> iterator = dataMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            if (iterator.hasNext()) {
                json.append(",");
            }
        }
        json.append("}");
        return json.toString();
    }

    private static void sendPutRequest(String serverUrl, String jsonData) {
        try {
            URL url = new URL(serverUrl);
            Socket socket = new Socket(url.getHost(), url.getPort() == -1 ? 80 : url.getPort());

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Send PUT request
            out.println("PUT " + url.getPath() + " HTTP/1.1");
            out.println("Host: " + url.getHost());
            out.println("Content-Type: application/json");
            out.println("Content-Length: " + jsonData.getBytes().length);
            out.println("Connection: close");
            out.println();
            out.println(jsonData);

            // Read response
            String statusLine = in.readLine();
            if (statusLine == null) {
                System.out.println("No response from server.");
                return;
            }

            // Read headers
            Map<String, String> headers = new HashMap<>();
            String headerLine;
            while (!(headerLine = in.readLine()).equals("")) {
                String[] headerParts = headerLine.split(": ");
                if (headerParts.length == 2) {
                    headers.put(headerParts[0], headerParts[1]);
                }
            }

            // Read response body
            StringBuilder responseBody = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                responseBody.append(line);
            }

            System.out.println("Server response: " + responseBody.toString());

            socket.close();

        } catch (Exception e) {
            System.out.println("Error in sendPutRequest: " + e.getMessage());
        }
    }
}
