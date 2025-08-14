
# Java File Organizer (Gradle)

A cross-platform CLI tool to organize files in a folder by **extension**, **modified date**, or **type** (images, videos, docs, etc.).

## Quick Start

```bash
# 1) Extract project
cd java-file-organizer

# 2) Build runnable JAR (requires Java 17+ and Gradle wrapper)
./gradlew fatJar

# 3) Run (example: organize by extension)
java -jar build/libs/java-file-organizer-all.jar --source "/path/to/folder" --mode by-extension --move
```

> On Windows use `gradlew.bat` instead of `./gradlew`.

## Usage

```
Required:
  --source <path>           Source directory to scan.

Optional:
  --target <path>           Target directory (default: same as --source).
  --mode <by-extension|by-date|by-type>   How to organize (default: by-extension).
  --move                    Move files (default behavior if neither --move nor --copy is set).
  --copy                    Copy files instead of moving.
  --dry-run                 Show what would happen without changing files.
  --include-hidden          Include hidden files.
  --recursive               Recurse into subdirectories (default: true).
  --no-recursive            Do not recurse into subdirectories.
  --max-depth <n>           Maximum recursion depth (0 = only source).
  --conflict <skip|rename|overwrite>   What to do if destination file exists (default: rename).
  --pattern <glob>          Only include files matching a single glob (e.g., *.jpg).

Examples:
  # Organize by file type, moving files:
  java -jar build/libs/java-file-organizer-all.jar --source "D:/Downloads" --mode by-type --move

  # Organize by last-modified month/year, copy instead of move:
  java -jar build/libs/java-file-organizer-all.jar --source "/Users/you/Desktop" --mode by-date --copy

  # Dry run with max depth 1:
  java -jar build/libs/java-file-organizer-all.jar --source "/tmp/inbox" --mode by-extension --dry-run --max-depth 1
```

## How it Works

- **by-extension**: Puts files into `<ext>/` (e.g., `jpg/`, `pdf/`). Unknown extension → `_no_ext`.
- **by-date**: Puts files into `YYYY/MM/` based on last-modified time (e.g., `2025/08/`).
- **by-type**: Groups common extensions into folders like `Images/`, `Videos/`, `Audio/`, `Documents/`, `Archives/`, `Code/`, `Spreadsheets/`, `Presentations/`, `Others/`.

## Build from Source

- Java 17+ is recommended.
- Uses the Gradle wrapper included in the repo.

```bash
./gradlew clean build
```

## Notes

- Conflict strategies:
  - **skip**: leave the existing destination file and do nothing.
  - **rename**: append a numeric suffix to the filename (e.g., `file (1).jpg`).
  - **overwrite**: replace the existing destination file.

- The tool avoids organizing directories; it only processes regular files.
