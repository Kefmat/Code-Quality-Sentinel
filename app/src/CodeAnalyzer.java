import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CodeAnalyzer skanner kildekode for å måle dokumentasjonsgrad og kvalitet.
 * Resultatene lagres som en JSON-fil for videre prosessering.
 */
public class CodeAnalyzer {

    private int totalFiles = 0;
    private int totalLines = 0;
    private int todoCount = 0;
    private int javadocCount = 0;

    public static void main(String[] args) {
        CodeAnalyzer analyzer = new CodeAnalyzer();
        // Vi skanner 'src'-mappen som standard
        analyzer.analyze("./src");
        analyzer.saveReport();
    }

    /**
     * Går gjennom alle filer i en mappe rekursivt.
     * @param rootPath Stien som skal skannes.
     */
    public void analyze(String rootPath) {
        System.out.println("[ANALYZER] Starter skanning av: " + rootPath);
        try {
            List<Path> files = Files.walk(Paths.get(rootPath))
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            totalFiles = files.size();

            for (Path file : files) {
                processFile(file);
            }
        } catch (IOException e) {
            System.err.println("[FEIL] Kunne ikke lese mappen: " + e.getMessage());
            System.exit(1);
        }
    }

    private void processFile(Path path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
            String line;
            boolean inJavadoc = false;

            while ((line = reader.readLine()) != null) {
                totalLines++;
                String trimmed = line.trim();

                // Tell TODOs
                if (trimmed.contains("TODO") || trimmed.contains("FIXME")) {
                    todoCount++;
                }

                // Enkel sjekk for Javadoc-start
                if (trimmed.startsWith("/**")) {
                    inJavadoc = true;
                    javadocCount++;
                }
            }
        } catch (IOException e) {
            System.err.println("[FEIL] Kunne ikke lese fil: " + path);
        }
    }

    /**
     * Lagrer resultatene til en JSON-fil.
     */
    public void saveReport() {
        double docRate = totalFiles > 0 ? (double) javadocCount / totalFiles * 100 : 0;
        
        String json = String.format(
            "{\n  \"total_files\": %d,\n  \"total_lines\": %d,\n  \"todo_count\": %d,\n  \"doc_rate\": %.2f\n}",
            totalFiles, totalLines, todoCount, docRate
        );

        try {
            Files.write(Paths.get("quality_report.json"), json.getBytes());
            System.out.println("[ANALYZER] Rapport generert: quality_report.json");
        } catch (IOException e) {
            System.err.println("[FEIL] Kunne ikke lagre rapport.");
        }
    }
}