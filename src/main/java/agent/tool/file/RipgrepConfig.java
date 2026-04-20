package agent.tool.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Ripgrep configuration - provides ripgrep binary path with fallback logic.
 * Port of Open Claude Code's ripgrep.ts getRipgrepConfig().
 *
 * Modes:
 * - system: Use system ripgrep (rg) if available
 * - builtin: Use vendored ripgrep binary bundled with the application
 */
public class RipgrepConfig {

    public enum Mode {
        SYSTEM,
        BUILTIN
    }

    private final Mode mode;
    private final String command;
    private final Path binaryPath;

    public RipgrepConfig(Mode mode, String command, Path binaryPath) {
        this.mode = mode;
        this.command = command;
        this.binaryPath = binaryPath;
    }

    public Mode getMode() {
        return mode;
    }

    public String getCommand() {
        return command;
    }

    public Path getBinaryPath() {
        return binaryPath;
    }

    /**
     * Get the ripgrep configuration with fallback logic.
     * Port of ripgrep.ts getRipgrepConfig().
     *
     * Strategy:
     * 1. Try to find system ripgrep (rg) in PATH
     * 2. If not found, use vendored ripgrep binary
     */
    public static RipgrepConfig getRipgrepConfig() {
        // Try to find system ripgrep
        String systemRg = findSystemExecutable("rg");
        if (systemRg != null) {
            // SECURITY: Use command name 'rg' instead of systemPath to prevent PATH hijacking
            // If we used systemPath, a malicious ./rg.exe in current directory could be executed
            // Using just 'rg' lets the OS resolve it safely
            return new RipgrepConfig(Mode.SYSTEM, "rg", null);
        }

        // Fall back to vendored ripgrep
        Path vendorPath = getVendoredRipgrepPath();
        if (vendorPath != null && Files.exists(vendorPath)) {
            return new RipgrepConfig(Mode.BUILTIN, vendorPath.toString(), vendorPath);
        }

        // Last resort: try system rg anyway (will fail with proper error message)
        return new RipgrepConfig(Mode.SYSTEM, "rg", null);
    }

    /**
     * Find system executable in PATH.
     */
    private static String findSystemExecutable(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }

        String[] dirs = path.split(System.getProperty("path.separator"));
        String exeName = System.getProperty("os.name").startsWith("Windows") ? name + ".exe" : name;

        for (String dir : dirs) {
            Path fullPath = Paths.get(dir, exeName);
            if (Files.exists(fullPath) && Files.isExecutable(fullPath)) {
                return fullPath.toString();
            }
        }
        return null;
    }

    /**
     * Get the path to vendored ripgrep binary.
     * Returns null if no vendored ripgrep is available.
     */
    private static Path getVendoredRipgrepPath() {
        // Determine platform and architecture
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        String platform;
        if (os.contains("mac") || os.contains("darwin")) {
            platform = "darwin";
        } else if (os.contains("windows")) {
            platform = "win32";
        } else if (os.contains("linux")) {
            platform = "linux";
        } else {
            return null;
        }

        String archDir;
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            archDir = "arm64-" + platform;
        } else {
            archDir = "x64-" + platform;
        }

        // Check filesystem directly first (development mode: src/main/resources/vendor/ripgrep/)
        Path devPath = Path.of("src/main/resources/vendor/ripgrep/" + archDir + "/rg");
        if (Files.exists(devPath)) {
            return devPath;
        }

        // Check compiled classes/resources (target/classes/vendor/ripgrep/)
        Path classesPath = Path.of("target/classes/vendor/ripgrep/" + archDir + "/rg");
        if (Files.exists(classesPath)) {
            return classesPath;
        }

        return null;
    }

    /**
     * Get the resolved ripgrep binary path for execution.
     * For BUILTIN mode, this extracts the resource to a temp file if running from jar.
     */
    public Path getExecutablePath() throws IOException {
        if (mode == Mode.SYSTEM) {
            return Path.of(command);
        }

        if (binaryPath == null) {
            return Path.of(command);
        }

        // If binaryPath is an absolute path and exists, use it directly
        if (binaryPath.isAbsolute() && Files.exists(binaryPath)) {
            return binaryPath;
        }

        // If binaryPath exists and is executable, use it
        if (Files.exists(binaryPath) && Files.isExecutable(binaryPath)) {
            return binaryPath;
        }

        // Need to extract from classpath to temp file
        return extractToTempFile();
    }

    /**
     * Extract vendored ripgrep binary to a temp file.
     */
    private Path extractToTempFile() throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        String platform;
        if (os.contains("mac") || os.contains("darwin")) {
            platform = "darwin";
        } else if (os.contains("windows")) {
            platform = "win32";
        } else if (os.contains("linux")) {
            platform = "linux";
        } else {
            throw new IOException("Unsupported platform: " + os);
        }

        String archDir;
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            archDir = "arm64-" + platform;
        } else {
            archDir = "x64-" + platform;
        }

        String resourcePath = "/vendor/ripgrep/" + archDir + "/rg";
        String exeName = platform.equals("win32") ? "rg.exe" : "rg";

        // Create temp directory for the binary
        Path tempDir = Files.createTempDirectory("javaclawbot-rg");
        Path tempBin = tempDir.resolve(exeName);

        try (InputStream is = RipgrepConfig.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Vendored ripgrep not found in classpath: " + resourcePath);
            }
            try (OutputStream os2 = Files.newOutputStream(tempBin)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os2.write(buffer, 0, bytesRead);
                }
            }
        }

        // Make executable
        tempBin.toFile().setExecutable(true, false);

        return tempBin;
    }
}