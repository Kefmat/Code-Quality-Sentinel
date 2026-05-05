import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * CodeAnalyzer skanner kildekode for å måle teknisk kvalitet.
 * Den analyserer kompleksitet, navnestandarder og dokumentasjonsgrad per fil.
 */
public class CodeAnalyzer {

    private List<FileReport> fileReports = new ArrayList<>();

    public static void main(String[] args) {
        CodeAnalyzer analyzer = new CodeAnalyzer();
        
        // Bruker første argument som sti hvis det eksisterer, ellers standard ./src
        String scanPath = (args.length > 0) ? args[0] : "./src";
        
        analyzer.analyze(scanPath);
        analyzer.saveReport();
    }

    /**
     * Går gjennom alle filer i en mappe rekursivt og starter analyse.
     */
    public void analyze(String rootPath) {
        System.out.println("[ANALYZER] Starter teknisk analyse av: " + rootPath);
        try {
            List<Path> files = Files.walk(Paths.get(rootPath))
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path file : files) {
                fileReports.add(processFile(file));
            }
        } catch (IOException e) {
            System.err.println("[FEIL] Kunne ikke lese mappen: " + e.getMessage());
        }
    }

    /**
     * Analyserer en enkelt fil for kompleksitet, stil og dokumentasjon.
     */
    private FileReport processFile(Path path) {
        FileReport report = new FileReport(path.getFileName().toString());
        
        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                report.totalLines++;
                String trimmed = line.trim();

                // 1. Dokumentasjon og TODOs
                if (trimmed.startsWith("/**")) {
                    report.javadocCount++;
                }
                if (trimmed.contains("TODO") || trimmed.contains("FIXME")) {
                    report.todoCount++;
                }

                // 2. Mål Syklo matisk Kompleksitet
                // Vi teller kontrollstrukturer (forgreninger i koden)
                if (trimmed.matches(".*\\b(if|for|while|case|catch|&&|\\|\\|)\\b.*")) {
                    report.complexityScore++;
                }

                // 3. Sjekk Navnestandard (Klasser bør være PascalCase)
                if (trimmed.contains("class ")) {
                    Pattern pattern = Pattern.compile("class\\s+([A-Z][a-zA-Z0-9]*)");
                    Matcher matcher = pattern.matcher(trimmed);
                    if (!matcher.find()) {
                        report.addIssue("Klassenavn følger ikke PascalCase");
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[FEIL] Kunne ikke lese fil: " + path);
        }
        return report;
    }

    /**
     * Lagrer resultatene som en detaljert JSON-fil.
     */
    public void saveReport() {
        StringBuilder json = new StringBuilder("{\n  \"files\": [\n");
        
        for (int i = 0; i < fileReports.size(); i++) {
            FileReport fr = fileReports.get(i);
            json.append(String.format(
                "    {\n      \"name\": \"%s\",\n      \"lines\": %d,\n      \"complexity\": %d,\n      \"javadoc\": %d,\n      \"todos\": %d,\n      \"issues\": %s\n    }%s\n",
                fr.fileName, fr.totalLines, fr.complexityScore, fr.javadocCount, fr.todoCount, fr.getIssuesJson(),
                (i < fileReports.size() - 1 ? "," : "")
            ));
        }
        json.append("  ]\n}");

        // Lagrer primært til /app for Docker, med fallback til lokal mappe
        writeJsonToFile(json.toString());
    }

    private void writeJsonToFile(String content) {
        Path[] paths = { Paths.get("/app/quality_report.json"), Paths.get("quality_report.json") };
        for (Path p : paths) {
            try {
                Files.write(p, content.getBytes());
                System.out.println("[ANALYZER] Rapport lagret: " + p.toAbsolutePath());
                return;
            } catch (IOException ignored) {}
        }
    }

    /**
     * Hjelpeklasse for å kapsle inn data per analysert fil.
     */
    private static class FileReport {
        String fileName;
        int totalLines = 0;
        int javadocCount = 0;
        int todoCount = 0;
        int complexityScore = 1; 
        List<String> issues = new ArrayList<>();

        FileReport(String fileName) { this.fileName = fileName; }

        void addIssue(String issue) { issues.add(issue); }

        String getIssuesJson() {
            return issues.stream()
                    .map(s -> "\"" + s + "\"")
                    .collect(Collectors.joining(", ", "[", "]"));
        }
    }
}