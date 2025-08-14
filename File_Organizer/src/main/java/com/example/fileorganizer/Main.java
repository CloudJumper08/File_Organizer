
package com.example.fileorganizer;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    public static void main(String[] args) {
        try {
            Config cfg = Config.parseArgs(args);
            new Organizer(cfg).run();
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.out.println(Config.usage());
            System.exit(2);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    static class Config {
        Path source;
        Path target;
        Mode mode = Mode.BY_EXTENSION;
        boolean move = true; // default move
        boolean copy = false;
        boolean dryRun = false;
        boolean includeHidden = false;
        boolean recursive = true;
        Integer maxDepth = null;
        ConflictStrategy conflictStrategy = ConflictStrategy.RENAME;
        String pattern = null;

        enum Mode { BY_EXTENSION, BY_DATE, BY_TYPE }
        enum ConflictStrategy { SKIP, RENAME, OVERWRITE }

        static Config parseArgs(String[] args) {
            Config c = new Config();
            if (args == null) args = new String[0];
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "--source":
                        c.source = Paths.get(requireValue(args, ++i, "--source"));
                        break;
                    case "--target":
                        c.target = Paths.get(requireValue(args, ++i, "--target"));
                        break;
                    case "--mode":
                        String m = requireValue(args, ++i, "--mode").toLowerCase(Locale.ROOT);
                        switch (m) {
                            case "by-extension": c.mode = Mode.BY_EXTENSION; break;
                            case "by-date": c.mode = Mode.BY_DATE; break;
                            case "by-type": c.mode = Mode.BY_TYPE; break;
                            default: throw new IllegalArgumentException("Unknown mode: " + m);
                        }
                        break;
                    case "--move":
                        c.move = true; c.copy = false;
                        break;
                    case "--copy":
                        c.copy = true; c.move = false;
                        break;
                    case "--dry-run":
                        c.dryRun = true;
                        break;
                    case "--include-hidden":
                        c.includeHidden = true;
                        break;
                    case "--recursive":
                        c.recursive = true;
                        break;
                    case "--no-recursive":
                        c.recursive = false;
                        break;
                    case "--max-depth":
                        c.maxDepth = Integer.parseInt(requireValue(args, ++i, "--max-depth"));
                        if (c.maxDepth < 0) throw new IllegalArgumentException("max-depth must be >= 0");
                        break;
                    case "--conflict":
                        String cs = requireValue(args, ++i, "--conflict").toLowerCase(Locale.ROOT);
                        switch (cs) {
                            case "skip": c.conflictStrategy = ConflictStrategy.SKIP; break;
                            case "rename": c.conflictStrategy = ConflictStrategy.RENAME; break;
                            case "overwrite": c.conflictStrategy = ConflictStrategy.OVERWRITE; break;
                            default: throw new IllegalArgumentException("Unknown conflict: " + cs);
                        }
                        break;
                    case "--pattern":
                        c.pattern = requireValue(args, ++i, "--pattern");
                        break;
                    case "-h":
                    case "--help":
                        System.out.println(usage());
                        System.exit(0);
                    default:
                        throw new IllegalArgumentException("Unknown argument: " + a);
                }
            }

            if (c.source == null) throw new IllegalArgumentException("--source is required");
            if (c.target == null) c.target = c.source;

            return c;
        }

        static String requireValue(String[] args, int idx, String key) {
            if (idx >= args.length) throw new IllegalArgumentException("Missing value for " + key);
            return args[idx];
        }

        static String usage() {
            return String.join("\n",
                "Java File Organizer",
                "Required:",
                "  --source <path>",
                "Optional:",
                "  --target <path> (default: --source)",
                "  --mode <by-extension|by-date|by-type> (default: by-extension)",
                "  --move | --copy (default: move)",
                "  --dry-run",
                "  --include-hidden",
                "  --recursive | --no-recursive (default: recursive)",
                "  --max-depth <n>",
                "  --conflict <skip|rename|overwrite> (default: rename)",
                "  --pattern <glob> (e.g., *.jpg)",
                "  -h | --help"
            );
        }
    }

    static class Organizer {
        private final Config cfg;
        private final DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("yyyy/MM");
        private final Map<String, String> typeMap = buildTypeMap();
        private final AtomicInteger moved = new AtomicInteger(0);
        private final AtomicInteger copied = new AtomicInteger(0);
        private final AtomicInteger skipped = new AtomicInteger(0);

        Organizer(Config cfg) {
            this.cfg = cfg;
        }

        void run() throws IOException {
            if (!Files.isDirectory(cfg.source)) {
                throw new IllegalArgumentException("Source is not a directory: " + cfg.source);
            }
            if (!Files.exists(cfg.target)) {
                if (!cfg.dryRun) Files.createDirectories(cfg.target);
            }
            final int maxDepth = cfg.recursive ? (cfg.maxDepth == null ? Integer.MAX_VALUE : cfg.maxDepth) : 0;

            FileVisitor<Path> visitor = new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!cfg.includeHidden && isHidden(dir)) return FileVisitResult.SKIP_SUBTREE;
                    if (dir.equals(cfg.target)) return FileVisitResult.CONTINUE; // allow target inside source
                    // Respect max depth relative to source
                    if (cfg.source.relativize(dir).getNameCount() > maxDepth) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isRegularFile()) return FileVisitResult.CONTINUE;
                    if (!cfg.includeHidden && isHidden(file)) return FileVisitResult.CONTINUE;
                    if (cfg.pattern != null && !file.getFileName().toString().toLowerCase(Locale.ROOT).matches(globToRegex(cfg.pattern))) {
                        return FileVisitResult.CONTINUE;
                    }

                    try {
                        Path dest = destinationFor(file, attrs);
                        if (dest == null) {
                            skipped.incrementAndGet();
                            return FileVisitResult.CONTINUE;
                        }

                        if (cfg.dryRun) {
                            System.out.printf("Would %s %s -> %s%n", actionWord(), file, dest);
                        } else {
                            Files.createDirectories(dest.getParent());
                            Path finalDest = resolveConflict(dest);
                            if (finalDest == null) {
                                skipped.incrementAndGet();
                                return FileVisitResult.CONTINUE;
                            }
                            if (cfg.copy) {
                                Files.copy(file, finalDest, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                                copied.incrementAndGet();
                            } else {
                                // default move
                                Files.move(file, finalDest, StandardCopyOption.REPLACE_EXISTING);
                                moved.incrementAndGet();
                            }
                            System.out.printf("%s %s -> %s%n", capitalize(actionWord()), file, finalDest);
                        }
                    } catch (Exception ex) {
                        System.err.println("Error processing " + file + ": " + ex.getMessage());
                        skipped.incrementAndGet();
                    }
                    return FileVisitResult.CONTINUE;
                }
            };

            Files.walkFileTree(cfg.source, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, visitor);

            System.out.printf("Done. Moved: %d, Copied: %d, Skipped: %d%n", moved.get(), copied.get(), skipped.get());
        }

        private String actionWord() { return cfg.copy ? "copy" : "move"; }
        private String capitalize(String s) { return s.substring(0,1).toUpperCase() + s.substring(1); }

        private Path resolveConflict(Path dest) throws IOException {
            if (!Files.exists(dest)) return dest;
            switch (cfg.conflictStrategy) {
                case SKIP:
                    System.out.println("Exists, skipping: " + dest);
                    return null;
                case OVERWRITE:
                    return dest;
                case RENAME:
                default:
                    String fileName = dest.getFileName().toString();
                    String base = fileName;
                    String ext = "";
                    int dot = fileName.lastIndexOf('.');
                    if (dot > 0) { base = fileName.substring(0, dot); ext = fileName.substring(dot); }
                    int n = 1;
                    Path parent = dest.getParent();
                    Path candidate = dest;
                    while (Files.exists(candidate)) {
                        candidate = parent.resolve(base + " (" + (n++) + ")" + ext);
                    }
                    return candidate;
            }
        }

        private Path destinationFor(Path file, BasicFileAttributes attrs) throws IOException {
            String folderName;
            switch (cfg.mode) {
                case BY_DATE:
                    Instant lm = attrs.lastModifiedTime().toInstant();
                    String ym = monthFmt.format(lm.atZone(ZoneId.systemDefault()));
                    folderName = ym;
                    break;
                case BY_TYPE:
                    String ext = getExtension(file.getFileName().toString());
                    folderName = typeMap.getOrDefault(ext, "Others");
                    break;
                case BY_EXTENSION:
                default:
                    String e = getExtension(file.getFileName().toString());
                    folderName = e.isEmpty() ? "_no_ext" : e;
                    break;
            }
            Path rel = cfg.source.relativize(file.getParent());
            // Put inside target/<folderName>/ and ignore original substructure
            return cfg.target.resolve(Paths.get(folderName, file.getFileName().toString()));
        }

        private boolean isHidden(Path p) {
            try {
                return Files.isHidden(p) || p.getFileName().toString().startsWith(".");
            } catch (IOException e) {
                return p.getFileName().toString().startsWith(".");
            }
        }

        private static String getExtension(String name) {
            int dot = name.lastIndexOf('.');
            if (dot < 0 || dot == name.length()-1) return "";
            return name.substring(dot+1).toLowerCase(Locale.ROOT);
        }

        private static String globToRegex(String glob) {
            // Simple glob to regex (supports * and ?)
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < glob.length(); i++) {
                char c = glob.charAt(i);
                switch (c) {
                    case '*': sb.append(".*"); break;
                    case '?': sb.append('.'); break;
                    case '.': sb.append("\\."); break;
                    default:
                        if ("()[]{}+$^|".indexOf(c) >= 0) sb.append('\\');
                        sb.append(c);
                }
            }
            return "^" + sb + "$";
        }

        private static Map<String, String> buildTypeMap() {
            Map<String, String> m = new HashMap<>();
            // Images
            for (String ext : new String[]{"jpg","jpeg","png","gif","bmp","tiff","webp","heic","raw","svg"}) m.put(ext, "Images");
            // Videos
            for (String ext : new String[]{"mp4","mov","avi","mkv","wmv","flv","webm","m4v"}) m.put(ext, "Videos");
            // Audio
            for (String ext : new String[]{"mp3","wav","flac","aac","ogg","m4a","wma"}) m.put(ext, "Audio");
            // Documents
            for (String ext : new String[]{"pdf","doc","docx","rtf","txt","odt","md"}) m.put(ext, "Documents");
            // Spreadsheets
            for (String ext : new String[]{"xls","xlsx","csv","ods"}) m.put(ext, "Spreadsheets");
            // Presentations
            for (String ext : new String[]{"ppt","pptx","odp","key"}) m.put(ext, "Presentations");
            // Archives
            for (String ext : new String[]{"zip","rar","7z","tar","gz","bz2"}) m.put(ext, "Archives");
            // Code
            for (String ext : new String[]{"java","kt","kts","py","js","ts","html","css","c","h","cpp","hpp","cc","cs","go","rb","php","rs","swift","m","mm","sh","bat","ps1","sql","json","xml","yml","yaml","ini","toml","gradle"}) m.put(ext, "Code");
            return m;
        }
    }
}
