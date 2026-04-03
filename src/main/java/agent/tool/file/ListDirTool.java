package agent.tool.file;

import agent.tool.Tool;
import utils.PathUtil;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static agent.tool.file.FileSystemTools.asString;
import static agent.tool.file.FileSystemTools.schemaPathOnly;

// ----------------------------
    // ListDirTool
    // ----------------------------
    public final class ListDirTool extends Tool {
        private final Path workspace;
        private final Path allowedDir;

        public ListDirTool(Path workspace, Path allowedDir) {
            this.workspace = workspace;
            this.allowedDir = allowedDir;
        }

        @Override
        public String name() {
            return "list_dir";
        }

        @Override
        public String description() {
            return "List the contents of a directory.";
        }

        @Override
        public Map<String, Object> parameters() {
            return schemaPathOnly("The directory path to list");
        }

        @Override
        public CompletionStage<String> execute(Map<String, Object> args) {
            String path = asString(args.get("path"));
            try {
                Path dirPath = PathUtil.resolvePath(path, workspace, allowedDir);
                if (!Files.exists(dirPath)) return CompletableFuture.completedFuture("Error: Directory not found: " + path);
                if (!Files.isDirectory(dirPath)) return CompletableFuture.completedFuture("Error: Not a directory: " + path);

                List<String> items = new ArrayList<>();
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(dirPath)) {
                    for (Path p : ds) items.add(p.getFileName().toString());
                }
                Collections.sort(items);

                if (items.isEmpty()) return CompletableFuture.completedFuture("Directory " + path + " is empty");

                List<String> out = new ArrayList<>();
                for (String name : items) {
                    Path p = dirPath.resolve(name);
                    String prefix = Files.isDirectory(p) ? "📁 " : "📄 ";
                    out.add(prefix + name);
                }
                return CompletableFuture.completedFuture(String.join("\n", out));
            } catch (SecurityException se) {
                return CompletableFuture.completedFuture("Error: " + se.getMessage());
            } catch (Exception e) {
                return CompletableFuture.completedFuture("Error listing directory: " + e.getMessage());
            }
        }
    }