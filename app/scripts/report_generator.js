const fs = require('fs');
const path = require('path');

// Dynamisk stihåndtering for å støtte både Docker og lokal kjøring
const dataPath = fs.existsSync('/app/quality_report.json') 
    ? '/app/quality_report.json' 
    : path.join(__dirname, '../../quality_report.json');

const outputDir = path.join(__dirname, '../output');
const templatePath = path.join(__dirname, '../templates/quality_template.html');
const historyDir = path.join(__dirname, '../history');

if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
}

/**
 * Arkiverer den gjeldende analysen i en historikk-mappe med tidsstempel.
 * @param {Object} reportData Dataen som skal lagres.
 */
function archiveReport(reportData) {
    try {
        if (!fs.existsSync(historyDir)) {
            fs.mkdirSync(historyDir, { recursive: true });
        }

        const now = new Date();
        const timestamp = now.toISOString()
            .replace(/T/, '_')
            .replace(/\..+/, '')
            .replace(/:/g, '-');

        const archivePath = path.join(historyDir, `report_${timestamp}.json`);
        fs.writeFileSync(archivePath, JSON.stringify(reportData, null, 2));
        console.log(`[GENERATOR] Historisk rapport arkivert: ${archivePath}`);
    } catch (error) {
        console.error(`[FEIL] Kunne ikke arkivere rapport: ${error.message}`);
    }
}

try {
    const rawData = fs.readFileSync(dataPath, 'utf8');
    const data = JSON.parse(rawData);

    // Steg 1: Arkiver gjeldende rådata
    archiveReport(data);

    // Beregn aggregerte verdier fra den nye "files"-listen
    const totalFiles = data.files.length;
    const totalLines = data.files.reduce((sum, f) => sum + f.lines, 0);
    const totalTodos = data.files.reduce((sum, f) => sum + f.todos, 0);
    const totalJavadoc = data.files.reduce((sum, f) => sum + f.javadoc, 0);
    const avgComplexity = totalFiles > 0 
        ? (data.files.reduce((sum, f) => sum + f.complexity, 0) / totalFiles) 
        : 0;
    
    const docRate = totalFiles > 0 ? (totalJavadoc / totalFiles) * 100 : 0;

    // Logikk for karaktersetting (justert for kompleksitet)
    let grade = 'F';
    let statusClass = 'danger';

    if (docRate >= 80 && avgComplexity < 5) {
        grade = 'A';
        statusClass = 'success';
    } else if (docRate >= 60 && avgComplexity < 10) {
        grade = 'B';
        statusClass = 'warning';
    } else if (docRate >= 40) {
        grade = 'C';
        statusClass = 'warning';
    }

    // Generer tabell-rader for hver fil
    const fileTableRows = data.files.map(f => `
        <tr>
            <td>${f.name}</td>
            <td>${f.lines}</td>
            <td>${f.complexity}</td>
            <td>${f.todos}</td>
            <td>${f.issues.length > 0 
                ? `<span style="color: #ff6b6b">${f.issues.join(', ')}</span>` 
                : '<span style="color: #51cf66">OK</span>'}</td>
        </tr>
    `).join('');

    // Les malen og erstatt variabler
    let html = fs.readFileSync(templatePath, 'utf8');
    html = html.replace(/{{GRADE}}/g, grade)
               .replace(/{{STATUS_CLASS}}/g, statusClass)
               .replace(/{{FILES}}/g, totalFiles)
               .replace(/{{LINES}}/g, totalLines)
               .replace(/{{TODOS}}/g, totalTodos)
               .replace(/{{COMPLEXITY}}/g, avgComplexity.toFixed(1))
               .replace(/{{DOC_RATE}}/g, docRate.toFixed(1))
               .replace(/{{FILE_TABLE}}/g, fileTableRows)
               .replace(/{{TIMESTAMP}}/g, new Date().toLocaleString());

    // Lagre ferdig HTML
    fs.writeFileSync(path.join(outputDir, 'index.html'), html);

    // Kopier CSS
    const cssOutputPath = path.join(outputDir, 'style.css');
    fs.copyFileSync(path.join(__dirname, '../templates/style.css'), cssOutputPath);

    console.log(`[NODE] Rapport generert med suksess. Karakter: ${grade}`);

} catch (err) {
    console.error("[NODE] Feil under generering:", err.message);
    process.exit(1);
}