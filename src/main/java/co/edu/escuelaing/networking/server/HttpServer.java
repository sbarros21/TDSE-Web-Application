package co.edu.escuelaing.networking.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Minimal HTTP server (starting point).
 *
 * Accepts exactly one TCP connection, reads one HTTP request, returns a
 * small fixed HTML response, and stops. This is the baseline described in
 * section 4.4 of the networking guide, before extending it to accept
 * multiple sequential requests and serve real static resources.
 */
public class HttpServer {

    public static final int PORT = 35000;

    public static void main(String[] args) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Ready to receive on port " + PORT + "...");

            Socket clientSocket = serverSocket.accept();

            try (
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(clientSocket.getInputStream()))
            ) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    System.out.println("Received: " + inputLine);
                    if (!in.ready()) {
                        break;
                    }
                }

                String body = "<!doctype html><html><head>"
                        + "<meta charset=\"UTF-8\">"
                        + "<title>My Web Site</title></head>"
                        + "<body>My Web Site</body></html>";

                String response = "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: text/html; charset=UTF-8\r\n"
                        + "Content-Length: " + body.getBytes().length + "\r\n"
                        + "\r\n"
                        + body;

                out.print(response);
                out.flush();
            } finally {
                clientSocket.close();
            }
        }
    }
}