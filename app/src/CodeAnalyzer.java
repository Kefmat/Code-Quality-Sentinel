import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
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
    private List<FileReport> fileReports = new ArrayList<>();

    // Mønster for å oppdage potensielle hardkodede hemmeligheter
    private static final Pattern SECRET_PATTERN = Pattern.compile(
        ".*\\b(password|secret|passwd|api_key|apikey|token|private_key)\\b\\s*=\\s*\"[^\"]+\".*", 
        Pattern.CASE_INSENSITIVE
    );

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
        FileReport report = new FileReport(path.getFileName().toString());
        boolean insideSqlQuery = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
            String line;

            while ((line = reader.readLine()) != null) {
                totalLines++;
                report.lines++;
                String trimmed = line.trim();

                // Identifiserer TODOs og FIXMEs
                if (trimmed.contains("TODO") || trimmed.contains("FIXME")) {
                    todoCount++;
                    report.todos++;
                }

                // Enkel sjekk for Javadoc-start (blokk-kommentarer)
                if (trimmed.startsWith("/**")) {
                    javadocCount++;
                    report.javadoc++;
                }

                // Måling av syklomatisk kompleksitet
                if (trimmed.matches(".*\\b(if|for|while|case|catch|&&|\\|\\|)\\b.*")) {
                    report.complexity++;
                }

                // Sjekk av navnestandard (Klasser bør starte med stor bokstav)
                if (trimmed.contains("class ")) {
                    Pattern pattern = Pattern.compile("class\\s+([A-Z][a-zA-Z0-9]*)");
                    Matcher matcher = pattern.matcher(trimmed);
                    if (!matcher.find()) {
                        report.addIssue("Stil: Klassenavn bør være PascalCase");
                    }
                }

                // Sikkerhetssjekk A: Hardkodede hemmeligheter og passord
                if (SECRET_PATTERN.matcher(trimmed).matches() && !trimmed.contains("null")) {
                    report.addIssue("Sikkerhet: Potensiell hardkodet hemmelighet oppdaget");
                }

                // Sikkerhetssjekk B: Svake kryptoalgoritmer eller usikker randomisering
                if (trimmed.contains("MessageDigest.getInstance(\"MD5\")") || 
                    trimmed.contains("MessageDigest.getInstance(\"SHA-1\")")) {
                    report.addIssue("Sikkerhet: Sårbar kryptoalgoritme brukt (MD5/SHA-1)");
                }
                if (trimmed.contains("new Random()")) {
                    report.addIssue("Sikkerhet: Bruk SecureRandom i stedet for Random for sensitive operasjoner");
                }

                // Sikkerhetssjekk C: Enkel SQL-injeksjonsdeteksjon i strenger
                if (trimmed.toLowerCase().contains("select ") || trimmed.toLowerCase().contains("where ")) {
                    insideSqlQuery = true;
                }
                if (insideSqlQuery && trimmed.contains("+") && (trimmed.contains("\"") || trimmed.contains("'"))) {
                    report.addIssue("Sikkerhet: Potensiell SQL-injeksjon via strengsammensetning");
                    insideSqlQuery = false;
                }
                if (trimmed.contains(";")) {
                    insideSqlQuery = false;
                }
            }
        } catch (IOException e) {
            System.err.println("[FEIL] Kunne ikke lese fil: " + path);
        }

        fileReports.add(report);
    }

    /**
     * Lagrer resultatene til en JSON-fil. 
     * Bruker /app/quality_report.json for Docker-kompatibilitet.
     */
    public void saveReport() {
        StringBuilder json = new StringBuilder("{\n  \"files\": [\n");
        
        for (int i = 0; i < fileReports.size(); i++) {
            FileReport fr = fileReports.get(i);
            json.append(String.format(
                "    {\n      \"name\": \"%s\",\n      \"lines\": %d,\n      \"complexity\": %d,\n      \"javadoc\": %d,\n      \"todos\": %d,\n      \"issues\": %s\n    }%s\n",
                fr.fileName, fr.lines, fr.complexity, fr.javadoc, fr.todos, fr.getIssuesJson(),
                (i < fileReports.size() - 1 ? "," : "")
            ));
        }
        json.append("  ]\n}");

        // Vi prøver først den absolutte stien for Docker, ellers lokal mappe
        Path reportPath = Paths.get("/app/quality_report.json");
        
        try {
            Files.write(reportPath, json.toString().getBytes());
            System.out.println("[ANALYZER] Rapport generert: " + reportPath.toAbsolutePath());
        } catch (IOException e) {
            // Fallback for lokal testing utenfor Docker
            try {
                Files.write(Paths.get("quality_report.json"), json.toString().getBytes());
                System.out.println("[ANALYZER] Lokal rapport generert (fallback): quality_report.json");
            } catch (IOException ex) {
                System.err.println("[FEIL] Kunne ikke lagre rapport: " + ex.getMessage());
            }
        }
    }

    /**
     * Hjelpeklasse for å holde på analysedata for den enkelte filen.
     */
    private static class FileReport {
        String fileName;
        int lines = 0;
        int javadoc = 0;
        int todos = 0;
        int complexity = 1; // Starter på 1 (hovedstien i koden)
        List<String> issues = new ArrayList<>();

        FileReport(String fileName) {
            this.fileName = fileName;
        }

        void addIssue(String issue) {
            if (!issues.contains(issue)) {
                issues.add(issue);
            }
        }

        String getIssuesJson() {
            return issues.stream()
                    .map(s -> "\"" + s + "\"")
                    .collect(Collectors.joining(", ", "[", "]"));
        }
    }
}