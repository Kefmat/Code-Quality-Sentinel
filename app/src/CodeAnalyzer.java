import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * CodeAnalyzer skanner kildekode for å måle dokumentasjonsgrad og kvalitet.
 * Resultatene lagres som en JSON-fil for videre prosessering i Node.js.
 * * @author Kefmat
 * @version 1.2.1
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
     * Sjekker automatisk for vanlige prosjektstrukturer.
     * * @param args Kommandolinjeargumenter (valgfri sti til kildekode).
     */
    public static void main(String[] args) {
        CodeAnalyzer analyzer = new CodeAnalyzer();
        
        // Prioritering av sti: 
        // 1. Argument fra terminal
        // 2. ./app/src (Standard for din VS Code-struktur)
        // 3. ./src (Standard fallback)
        String scanPath = "./src";
        
        if (args.length > 0) {
            scanPath = args[0];
        } else if (Files.exists(Paths.get("./app/src"))) {
            scanPath = "./app/src";
        }
        
        analyzer.analyze(scanPath);
        analyzer.saveReport();
    }

    /**
     * Går gjennom alle filer i en mappe rekursivt og filtrerer for Java-filer.
     * * @param rootPath Stien som skal skannes.
     */
    public void analyze(String rootPath) {
        Path startPath = Paths.get(rootPath);
        
        if (!Files.exists(startPath)) {
            System.err.println("[FEIL] Kunne ikke finne mappen: " + startPath.toAbsolutePath());
            System.err.println("[TIPS] Sørg for at du står i prosjektrot eller oppgi sti som argument.");
            System.exit(1);
        }

        System.out.println("[ANALYZER] Starter skanning av: " + startPath.toAbsolutePath());
        
        try {
            List<Path> files = Files.walk(startPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            totalFiles = files.size();
            System.out.println("[ANALYZER] Fant " + totalFiles + " Java-filer.");

            for (Path file : files) {
                processFile(file);
            }
        } catch (IOException e) {
            System.err.println("[FEIL] Feil under gjennomgang av filer: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Leser en enkelt fil linje for linje for å telle kodelengde, TODOs og Javadoc.
     * Inneholder også logikk for å identifisere sikkerhetshull og stilfeil.
     * * @param path Stien til filen som skal prosesseres.
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

                // 1. Identifiserer TODOs og FIXMEs
                if (trimmed.contains("TODO") || trimmed.contains("FIXME")) {
                    todoCount++;
                    report.todos++;
                }

                // 2. Enkel sjekk for Javadoc-start
                if (trimmed.startsWith("/**")) {
                    javadocCount++;
                    report.javadoc++;
                }

                // 3. Måling av syklomatisk kompleksitet (forenklet)
                if (trimmed.matches(".*\\b(if|for|while|case|catch|&&|\\|\\|)\\b.*")) {
                    report.complexity++;
                }

                // 4. Sjekk av navnestandard
                if (trimmed.contains("class ")) {
                    Pattern pattern = Pattern.compile("class\\s+([A-Z][a-zA-Z0-9]*)");
                    Matcher matcher = pattern.matcher(trimmed);
                    if (!matcher.find()) {
                        report.addIssue("Stil: Klassenavn bør være PascalCase");
                    }
                }

                // 5. Sikkerhet: Hardkodede hemmeligheter
                if (SECRET_PATTERN.matcher(trimmed).matches() && !trimmed.contains("null")) {
                    report.addIssue("Sikkerhet: Potensiell hardkodet hemmelighet oppdaget");
                }

                // 6. Sikkerhet: Svake kryptoalgoritmer
                if (trimmed.contains("MessageDigest.getInstance(\"MD5\")") || 
                    trimmed.contains("MessageDigest.getInstance(\"SHA-1\")")) {
                    report.addIssue("Sikkerhet: Sårbar kryptoalgoritme brukt (MD5/SHA-1)");
                }
                if (trimmed.contains("new Random()")) {
                    report.addIssue("Sikkerhet: Bruk SecureRandom i stedet for Random");
                }

                // 7. Sikkerhet: SQL-injeksjon via strengsammensetning
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
     * Prøver først Docker-mappe, deretter lokal rotmappe.
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

        // Lagrer både til Docker-sti og lokal fallback
        String[] targets = {"/app/quality_report.json", "quality_report.json"};
        boolean saved = false;

        for (String target : targets) {
            try {
                Files.write(Paths.get(target), json.toString().getBytes());
                System.out.println("[ANALYZER] Rapport lagret: " + Paths.get(target).toAbsolutePath());
                saved = true;
                break; 
            } catch (IOException e) {
                // Fortsett til neste mål hvis dette feiler
            }
        }

        if (!saved) {
            System.err.println("[FEIL] Kunne ikke lagre rapporten noen steder.");
        }
    }

    /**
     * Hjelpeklasse for å lagre analysedata for hver enkelt fil.
     */
    private static class FileReport {
        String fileName;
        int lines = 0;
        int javadoc = 0;
        int todos = 0;
        int complexity = 1; 
        List<String> issues = new ArrayList<>();

        FileReport(String fileName) {
            this.fileName = fileName;
        }

        /**
         * Legger til et funn/problem i listen hvis det ikke allerede eksisterer.
         * @param issue Beskrivelse av problemet.
         */
        void addIssue(String issue) {
            if (!issues.contains(issue)) {
                issues.add(issue);
            }
        }

        /**
         * Formaterer listen over funn til et JSON-array format.
         * @return JSON-string med issues.
         */
        String getIssuesJson() {
            return issues.stream()
                    .map(s -> "\"" + s + "\"")
                    .collect(Collectors.joining(", ", "[", "]"));
        }
    }
}