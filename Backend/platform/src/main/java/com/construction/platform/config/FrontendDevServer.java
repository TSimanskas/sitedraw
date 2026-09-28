package com.construction.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.frontend.dev-server.enabled", havingValue = "true")
@ConditionalOnMissingClass("org.junit.jupiter.api.Test")
public class FrontendDevServer {

    private static final Logger log = LoggerFactory.getLogger(FrontendDevServer.class);

    private final FrontendDevServerProperties properties;
    private Process process;

    public FrontendDevServer(FrontendDevServerProperties properties) {
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startFrontend() {
        Path frontendDir = resolveFrontendDirectory();
        if (frontendDir == null) {
            log.warn(
                    "Frontend directory was not found (looked for package.json in '{}', user.dir={}). Start Vite manually with: npm run dev",
                    properties.directory(),
                    System.getProperty("user.dir")
            );
            return;
        }

        if (isPortOpen(properties.port())) {
            log.info("Frontend already running on http://localhost:{}", properties.port());
            return;
        }

        try {
            ensureDependencies(frontendDir);
            process = startVite(frontendDir);
            pipeOutput(process);
            log.info("Frontend dev server starting from {} — http://localhost:{}", frontendDir, properties.port());
        } catch (IOException exception) {
            log.warn("Could not start the frontend automatically: {}", exception.getMessage());
        }
    }

    @PreDestroy
    public void stopFrontend() {
        if (process == null || !process.isAlive()) {
            return;
        }

        log.info("Stopping frontend dev server");
        ProcessHandle handle = process.toHandle();
        handle.descendants().forEach(ProcessHandle::destroy);
        handle.destroy();

        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                handle.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private Path resolveFrontendDirectory() {
        List<Path> candidates = List.of(
                Path.of(properties.directory()).toAbsolutePath().normalize(),
                Path.of(System.getProperty("user.dir"), properties.directory()).toAbsolutePath().normalize()
        );

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate.resolve("package.json"))) {
                return candidate;
            }
        }
        return null;
    }

    private void ensureDependencies(Path frontendDir) throws IOException {
        if (Files.isDirectory(frontendDir.resolve("node_modules"))) {
            return;
        }

        log.info("Installing frontend dependencies in {}", frontendDir);
        Process install = new ProcessBuilder(npmCommand(), "install")
                .directory(frontendDir.toFile())
                .redirectErrorStream(true)
                .start();
        pipeOutput(install);

        try {
            int exitCode = install.waitFor();
            if (exitCode != 0) {
                throw new IOException("npm install failed with exit code " + exitCode);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            install.destroyForcibly();
            throw new IOException("npm install was interrupted");
        }
    }

    private Process startVite(Path frontendDir) throws IOException {
        return new ProcessBuilder(npmCommand(), "run", "dev")
                .directory(frontendDir.toFile())
                .redirectErrorStream(true)
                .start();
    }

    private String npmCommand() {
        if (properties.npmCommand() != null && !properties.npmCommand().isBlank()) {
            return properties.npmCommand();
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win") ? "npm.cmd" : "npm";
    }

    private boolean isPortOpen(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 250);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private void pipeOutput(Process child) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[frontend] {}", line);
                }
            } catch (IOException ignored) {
                // Process closed.
            }
        }, "frontend-dev-server");
        thread.setDaemon(true);
        thread.start();
    }
}
