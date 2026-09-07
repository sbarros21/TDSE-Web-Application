package co.edu.escuelaing.networking.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class HttpServer {

    public static final int PORT = 35000;

    private static final Path PUBLIC_DIR =
            Paths.get("src/main/resources/public").toAbsolutePath().normalize();

    private static final Map<String, String> CONTENT_TYPES = new HashMap<>();
    static {
        CONTENT_TYPES.put("html", "text/html; charset=UTF-8");
        CONTENT_TYPES.put("js", "text/javascript; charset=UTF-8");
        CONTENT_TYPES.put("png", "image/png");
        CONTENT_TYPES.put("jpg", "image/jpeg");
        CONTENT_TYPES.put("jpeg", "image/jpeg");
    }

    public static void main(String[] args) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server listening on port " + PORT + "...");
            System.out.println("Serving files from: " + PUBLIC_DIR);

            while (true) {
                Socket clientSocket = null;
                try {
                    clientSocket = serverSocket.accept();
                    handleClient(clientSocket);
                } catch (IOException e) {
                    System.err.println("Error handling client: " + e.getMessage());
                } finally {
                    closeQuietly(clientSocket);
                }
            }
        }
    }

    private static void handleClient(Socket clientSocket) throws IOException {
        BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
        OutputStream out = clientSocket.getOutputStream();

        String requestLine = in.readLine();
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            // Drain remaining headers.
        }

        if (requestLine == null || requestLine.isBlank()) {
            return;
        }
        System.out.println("Request line: " + requestLine);

        String[] parts = requestLine.split(" ");
        if (parts.length < 2) {
            sendError(out, 400, "Bad Request");
            return;
        }

        String method = parts[0];
        String rawPath = parts[1];

        if (!"GET".equalsIgnoreCase(method)) {
            sendError(out, 405, "Method Not Allowed");
            return;
        }

        String pathOnly = rawPath.contains("?")
                ? rawPath.substring(0, rawPath.indexOf('?'))
                : rawPath;
        String decodedPath = URLDecoder.decode(pathOnly, StandardCharsets.UTF_8);
        Map<String, String> params = parseQuery(rawPath);

        // --- Hardcoded dynamic services ---
        switch (decodedPath) {
            case "/greeting":
                handleGreeting(out, params);
                return;
            case "/square":
                handleSquare(out, params);
                return;
            case "/server-time":
                handleServerTime(out);
                return;
            case "/health":
                handleHealth(out);
                return;
            default:
                break;
        }

        // --- Static resources ---
        if (decodedPath.equals("/")) {
            decodedPath = "/index.html";
        }

        String extension = getExtension(decodedPath);
        String contentType = CONTENT_TYPES.get(extension);
        if (contentType == null) {
            sendError(out, 404, "Not Found");
            return;
        }

        Path requested = PUBLIC_DIR.resolve(decodedPath.substring(1)).normalize();
        if (!requested.startsWith(PUBLIC_DIR)) {
            System.err.println("Rejected path traversal attempt: " + decodedPath);
            sendError(out, 404, "Not Found");
            return;
        }

        if (!Files.exists(requested) || Files.isDirectory(requested)) {
            sendError(out, 404, "Not Found");
            return;
        }

        byte[] body = Files.readAllBytes(requested);
        sendResponse(out, 200, "OK", contentType, body);
    }

    // ---------- Services ----------

    private static void handleGreeting(OutputStream out, Map<String, String> params) throws IOException {
        String name = params.get("name");
        if (name == null || name.isBlank()) {
            sendJsonError(out, 400, "Missing or empty 'name' parameter");
            return;
        }
        String json = "{\"message\":\"Hello, " + escapeJson(name) + "!\"}";
        sendResponse(out, 200, "OK", "application/json; charset=UTF-8",
                json.getBytes(StandardCharsets.UTF_8));
    }

    private static void handleSquare(OutputStream out, Map<String, String> params) throws IOException {
        String rawValue = params.get("value");
        if (rawValue == null || rawValue.isBlank()) {
            sendJsonError(out, 400, "Missing 'value' parameter");
            return;
        }
        double value;
        try {
            value = Double.parseDouble(rawValue);
        } catch (NumberFormatException e) {
            sendJsonError(out, 400, "'value' must be numeric");
            return;
        }
        double square = value * value;
        String json = "{\"input\":" + value + ",\"square\":" + square + "}";
        sendResponse(out, 200, "OK", "application/json; charset=UTF-8",
                json.getBytes(StandardCharsets.UTF_8));
    }

    private static void handleServerTime(OutputStream out) throws IOException {
        String json = "{\"serverTime\":\"" + Instant.now() + "\"}";
        sendResponse(out, 200, "OK", "application/json; charset=UTF-8",
                json.getBytes(StandardCharsets.UTF_8));
    }

    private static void handleHealth(OutputStream out) throws IOException {
        String json = "{\"status\":\"UP\"}";
        sendResponse(out, 200, "OK", "application/json; charset=UTF-8",
                json.getBytes(StandardCharsets.UTF_8));
    }

    // ---------- Helpers ----------

    private static Map<String, String> parseQuery(String rawPath) {
        Map<String, String> result = new HashMap<>();
        if (!rawPath.contains("?")) {
            return result;
        }
        String query = rawPath.substring(rawPath.indexOf('?') + 1);
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) continue;
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            result.put(key, value);
        }
        return result;
    }

    private static String getExtension(String path) {
        int dot = path.lastIndexOf('.');
        return dot == -1 ? "" : path.substring(dot + 1).toLowerCase();
    }

    private static String escapeJson(String input) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static void sendResponse(OutputStream out, int status, String statusText,
                                     String contentType, byte[] body) throws IOException {
        String headers = "HTTP/1.1 " + status + " " + statusText + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "\r\n";
        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    private static void sendError(OutputStream out, int status, String statusText) throws IOException {
        String body = "<!doctype html><html><body><h1>" + status + " " + statusText
                + "</h1></body></html>";
        sendResponse(out, status, statusText, "text/html; charset=UTF-8",
                body.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendJsonError(OutputStream out, int status, String message) throws IOException {
        String json = "{\"error\":\"" + escapeJson(message) + "\"}";
        String statusText = status == 400 ? "Bad Request" : "Error";
        sendResponse(out, status, statusText, "application/json; charset=UTF-8",
                json.getBytes(StandardCharsets.UTF_8));
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Nothing more we can do.
            }
        }
    }
}