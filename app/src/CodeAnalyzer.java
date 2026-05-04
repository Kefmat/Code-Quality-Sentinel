import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CodeAnalyzer skanner kildekode for å måle dokumentasjonsgrad og kvalitet.
 * Resultatene lagres som en JSON-fil for videre prosessering i Node.js.
 */
public class CodeAnalyzer {

    private int totalFiles = 0;
    private int totalLines = 0;
    private int todoCount = 0;
    private int javadocCount = 0;

    /**
     * Hovedmetode som starter analyseprosessen.
     * Tar imot en sti som argument, eller bruker './src' som standard.
     */
    public static void main(String[] args) {
        CodeAnalyzer analyzer = new CodeAnalyzer();
        
        // Bruker første argument som sti hvis det eksisterer, ellers standard ./src
        String scanPath = (args.length > 0) ? args[0] : "./src";
        
        analyzer.analyze(scanPath);
        analyzer.saveReport();
    }

    /**
     * Går gjennom alle filer i en mappe rekursivt og filtrerer for Java-filer.
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

    /**
     * Leser en enkelt fil linje for linje for å telle kodelengde, TODOs og Javadoc.
     * @param path Stien til filen som skal prosesseres.
     */
    private void processFile(Path path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
            String line;

            while ((line = reader.readLine()) != null) {
                totalLines++;
                String trimmed = line.trim();

                // Identifiserer TODOs og FIXMEs
                if (trimmed.contains("TODO") || trimmed.contains("FIXME")) {
                    todoCount++;
                }

                // Enkel sjekk for Javadoc-start (blokk-kommentarer)
                if (trimmed.startsWith("/**")) {
                    javadocCount++;
                }
            }
        } catch (IOException e) {
            System.err.println("[FEIL] Kunne ikke lese fil: " + path);
        }
    }

    /**
     * Lagrer resultatene til en JSON-fil. 
     * Bruker /app/quality_report.json for Docker-kompatibilitet.
     */
    public void saveReport() {
        double docRate = totalFiles > 0 ? (double) javadocCount / totalFiles * 100 : 0;
        
        String json = String.format(
            "{\n  \"total_files\": %d,\n  \"total_lines\": %d,\n  \"todo_count\": %d,\n  \"doc_rate\": %.2f\n}",
            totalFiles, totalLines, todoCount, docRate
        );

        // Vi prøver først den absolutte stien for Docker, ellers lokal mappe
        Path reportPath = Paths.get("/app/quality_report.json");
        
        try {
            Files.write(reportPath, json.getBytes());
            System.out.println("[ANALYZER] Rapport generert: " + reportPath.toAbsolutePath());
        } catch (IOException e) {
            // Fallback for lokal testing utenfor Docker
            try {
                Files.write(Paths.get("quality_report.json"), json.getBytes());
                System.out.println("[ANALYZER] Lokal rapport generert (fallback): quality_report.json");
            } catch (IOException ex) {
                System.err.println("[FEIL] Kunne ikke lagre rapport: " + ex.getMessage());
            }
        }
    }
}