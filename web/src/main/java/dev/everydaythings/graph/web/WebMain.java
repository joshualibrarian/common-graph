package dev.everydaythings.graph.web;

import dev.everydaythings.graph.runtime.LibrarianOld;

import java.net.InetSocketAddress;

/**
 * Standalone entry point for the web gateway.
 *
 * <p>Starts an in-memory Librarian and a WebGateway on the specified port
 * (default 8080). Prints the connection URL and auth token to stdout.
 *
 * <p>Usage:
 * <pre>
 *   ./gradlew :web:run                    # Start on default port 8080
 *   ./gradlew :web:run --args='9090'      # Start on port 9090
 * </pre>
 *
 * <p>Then open the printed URL in a browser and enter the displayed token.
 */
public class WebMain {

    public static void main(String[] args) throws Exception {
        int port = 8080;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Usage: WebMain [port]");
                System.exit(1);
            }
        }

        // Boot librarian
        System.out.println("Starting in-memory Librarian...");
        LibrarianOld librarian = LibrarianOld.createInMemory();

        // Start session server (needed for auth token generation)
        librarian.startSessionServer();
        String token = librarian.sessionLocalToken();

        // Start web gateway
        WebGateway gateway = new WebGateway(librarian);
        InetSocketAddress address = gateway.start("0.0.0.0", port);

        System.out.println();
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  Common Graph Web Gateway");
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println();
        System.out.println("  URL:   http://localhost:" + address.getPort());
        System.out.println("  Token: " + token);
        System.out.println();
        System.out.println("  Open the URL in a browser and enter the token.");
        System.out.println("  Press Ctrl+C to stop.");
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println();

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down...");
            gateway.close();
            librarian.close();
        }, "web-shutdown"));

        // Block until killed
        Thread.currentThread().join();
    }
}
