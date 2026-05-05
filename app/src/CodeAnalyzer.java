import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * CodeAnalyzer skanner kildekode for å måle teknisk kvalitet og sikkerhet.
 * Den analyserer kompleksitet, navnestandarder, dokumentasjon og sårbarheter.
 */
public class CodeAnalyzer {

    private List<FileReport> fileReports = new ArrayList<>();

    // Regex for å oppdage potensielle hardkodede hemmeligheter (API-nøkler, passord)
    private static final Pattern SECRET_PATTERN = Pattern.compile(
        ".*\\b(password|secret|passwd|api_key|apikey|token|private_key)\\b\\s*=\\s*\"[^\"]+\".*", 
        Pattern.CASE_INSENSITIVE
    );

    public static void main(String[] args) {
        CodeAnalyzer analyzer = new CodeAnalyzer();
        String scanPath = (args.length > 0) ? args[0] : "./src";
        analyzer.analyze(scanPath);
        analyzer.saveReport();
    }

    public void analyze(String rootPath) {
        System.out.println("[ANALYZER] Starter teknisk- og sikkerhetsanalyse av: " + rootPath);
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

    private FileReport processFile(Path path) {
        FileReport report = new FileReport(path.getFileName().toString());
        boolean insideSqlQuery = false;
        
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

                // 2. Syklo matisk Kompleksitet
                if (trimmed.matches(".*\\b(if|for|while|case|catch|&&|\\|\\|)\\b.*")) {
                    report.complexityScore++;
                }

                // 3. Stil- og navnesjekk
                if (trimmed.contains("class ")) {
                    Pattern pattern = Pattern.compile("class\\s+([A-Z][a-zA-Z0-9]*)");
                    Matcher matcher = pattern.matcher(trimmed);
                    if (!matcher.find()) {
                        report.addIssue("Stil: Klassenavn bør være PascalCase");
                    }
                }

                // 4. SIKKERHETSSJEKKER
                
                // Sjekk A: Hardkodede hemmeligheter
                if (SECRET_PATTERN.matcher(trimmed).matches() && !trimmed.contains("null")) {
                    report.addIssue("Sikkerhet: Potensiell hardkodet hemmelighet/passord oppdaget");
                }

                // Sjekk B: Svak kryptografi eller usikker randomisering
                if (trimmed.contains("MessageDigest.getInstance(\"MD5\")") || 
                    trimmed.contains("MessageDigest.getInstance(\"SHA-1\")")) {
                    report.addIssue("Sikkerhet: Sårbar kryptoalgoritme brukt (MD5/SHA-1)");
                }
                if (trimmed.contains("new Random()")) {
                    report.addIssue("Sikkerhet: Bruk SecureRandom i stedet for Random for sensitive operasjoner");
                }

                // Sjekk C: Enkel deteksjon av SQL-injeksjon i strengbygging
                if (trimmed.toLowerCase().contains("select ") || trimmed.toLowerCase().contains("where ")) {
                    insideSqlQuery = true;
                }
                if (insideSqlQuery && trimmed.contains("+") && (trimmed.contains("\"") || trimmed.contains("'"))) {
                    report.addIssue("Sikkerhet: Potensiell SQL-injeksjon via strengsammensetning");
                    insideSqlQuery = false; // Nullstill etter varsel for å unngå duplikater på samme spørring
                }
                if (trimmed.contains(";")) {
                    insideSqlQuery = false; // Avslutt spørringskontekst ved semikolon
                }
            }
        } catch (IOException e) {
            System.err.println("[FEIL] Kunne ikke lese fil: " + path);
        }
        return report;
    }

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

        writeJsonToFile(json.toString());
    }

    private void writeJsonToFile(String content) {
        Path[] paths = { Paths.get("/app/quality_report.json"), Paths.get("quality_report.json") };
        for (Path p : paths) {
            try {
                Files.write(p, content.getBytes());
                System.out.println("[ANALYZER] Sikkerhetsrapport lagret: " + p.toAbsolutePath());
                return;
            } catch (IOException ignored) {}
        }
    }

    private static class FileReport {
        String fileName;
        int totalLines = 0;
        int javadocCount = 0;
        int todoCount = 0;
        int complexityScore = 1; 
        List<String> issues = new ArrayList<>();

        FileReport(String fileName) { this.fileName = fileName; }

        void addIssue(String issue) { 
            if (!issues.contains(issue)) { // Unngå identiske feilmeldinger per fil
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