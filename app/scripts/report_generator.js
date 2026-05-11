const fs = require('fs');
const path = require('path');

/**
 * Sentinel Report Generator
 * Seksjoner:
 * 1. Konfigurasjon og stier
 * 2. Historikk og arkivering
 * 3. Dataprosessering og Karakterlogikk
 * 4. HTML-generering
 * * @author Kefmat
 * @version 1.2.1
 */

// --- 1. Konfigurasjon og stier ---
const dataPath = fs.existsSync('/app/quality_report.json') 
    ? '/app/quality_report.json' 
    : path.join(__dirname, '../../quality_report.json');

const outputDir = path.join(__dirname, '../output');
const templatePath = path.join(__dirname, '../templates/quality_template.html');
const cssTemplatePath = path.join(__dirname, '../templates/style.css');
const historyDir = path.join(__dirname, '../history');

if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
}

// --- 2. Historikk og arkivering ---

function archiveReport(reportData) {
    try {
        if (!fs.existsSync(historyDir)) {
            fs.mkdirSync(historyDir, { recursive: true });
        }
        const now = new Date();
        const timestamp = now.toISOString().replace(/T/, '_').replace(/\..+/, '').replace(/:/g, '-');
        const archivePath = path.join(historyDir, `report_${timestamp}.json`);
        fs.writeFileSync(archivePath, JSON.stringify(reportData, null, 2));
        console.log(`[GENERATOR] Historisk rapport arkivert: ${archivePath}`);
    } catch (error) {
        console.error(`[FEIL] Kunne ikke arkivere rapport: ${error.message}`);
    }
}

function getHistoricalData() {
    try {
        if (!fs.existsSync(historyDir)) return [];
        const files = fs.readdirSync(historyDir)
            .filter(f => f.startsWith('report_') && f.endsWith('.json'))
            .sort();

        const recentFiles = files.slice(-7);
        return recentFiles.map(file => {
            const content = JSON.parse(fs.readFileSync(path.join(historyDir, file), 'utf8'));
            const dateMatch = file.match(/report_(\d{4}-\d{2}-\d{2})_(\d{2}-\d{2})/);
            const label = dateMatch ? `${dateMatch[1]} ${dateMatch[2].replace('-', ':')}` : 'Ukjent';

            const total = content.files.length;
            const avgComp = total > 0 ? (content.files.reduce((sum, f) => sum + f.complexity, 0) / total) : 0;
            
            // LOGIKK-FIKS: Historisk data må også bruke unik fil-sjekk
            const documentedFiles = content.files.filter(f => f.javadoc > 0).length;
            const docR = total > 0 ? (documentedFiles / total) * 100 : 0;

            return { label, complexity: parseFloat(avgComp.toFixed(1)), docRate: parseFloat(docR.toFixed(1)) };
        });
    } catch (error) {
        console.error(`[FEIL] Historikk-lesing feilet: ${error.message}`);
        return [];
    }
}

// --- 3. Dataprosessering og Karakterlogikk ---

try {
    const rawData = fs.readFileSync(dataPath, 'utf8');
    const data = JSON.parse(rawData);
    archiveReport(data);

    const totalFiles = data.files.length;
    const totalLines = data.files.reduce((sum, f) => sum + f.lines, 0);
    const totalTodos = data.files.reduce((sum, f) => sum + f.todos, 0);
    
    // LOGIKK-FIKS: Beregn basert på antall filer som har Javadoc (verdi 1 fra Java)
    const filesWithDoc = data.files.filter(f => f.javadoc > 0).length;
    const docRate = totalFiles > 0 ? (filesWithDoc / totalFiles) * 100 : 0;
    const finalDocRate = Math.min(docRate, 100);

    const avgComplexity = totalFiles > 0 ? (data.files.reduce((sum, f) => sum + f.complexity, 0) / totalFiles) : 0;

    // Oppdatert Karakterlogikk
    let grade = 'F', statusClass = 'danger';
    if (finalDocRate >= 80 && avgComplexity < 8) { grade = 'A'; statusClass = 'success'; }
    else if (finalDocRate >= 60 && avgComplexity < 12) { grade = 'B'; statusClass = 'success'; }
    else if (finalDocRate >= 40 && avgComplexity < 20) { grade = 'C'; statusClass = 'warning'; }
    else if (finalDocRate >= 20) { grade = 'D'; statusClass = 'warning'; }

    // Generer Kritiske Funn (Topp 3 mest komplekse filer over 5)
    const criticalFilesHtml = data.files
        .sort((a, b) => b.complexity - a.complexity)
        .slice(0, 3)
        .filter(f => f.complexity >= 5)
        .map(f => `
            <div class="critical-item">
                <div style="color: #ef4444; font-weight: bold; margin-bottom: 4px;">${f.name}</div>
                <div style="color: #94a3b8;">Kompleksitet: ${f.complexity}</div>
            </div>
        `).join('');

    // Generer tabellrader
    const fileTableRows = data.files.map(f => {
        const docPercent = f.javadoc > 0 ? 100 : 0;
        return `
            <tr>
                <td>${f.name}</td>
                <td>${f.lines}</td>
                <td>${f.complexity}</td>
                <td>${f.todos}</td>
                <td>
                    <div class="doc-bar-bg"><div class="doc-bar-fill" style="width: ${docPercent}%"></div></div>
                    ${f.issues && f.issues.length > 0 
                        ? `<span style="color: #ff6b6b">${f.issues.join(', ')}</span>` 
                        : '<span style="color: #10b981">OK</span>'}
                </td>
            </tr>`;
    }).join('');

    // --- 4. HTML-generering ---
    let html = fs.readFileSync(templatePath, 'utf8');
    const replacements = {
        '{{GRADE}}': grade,
        '{{STATUS_CLASS}}': statusClass,
        '{{FILES}}': totalFiles,
        '{{LINES}}': totalLines,
        '{{TODOS}}': totalTodos,
        '{{COMPLEXITY}}': avgComplexity.toFixed(1),
        '{{DOC_RATE}}': finalDocRate.toFixed(1),
        '{{CRITICAL_FILES}}': criticalFilesHtml || '<p style="color:#94a3b8">Ingen kritiske filer funnet.</p>',
        '{{FILE_TABLE}}': fileTableRows,
        '{{TREND_DATA}}': JSON.stringify(getHistoricalData()),
        '{{TIMESTAMP}}': new Date().toLocaleString('no-NO')
    };

    Object.keys(replacements).forEach(key => {
        html = html.split(key).join(replacements[key]);
    });

    fs.writeFileSync(path.join(outputDir, 'index.html'), html);
    
    if (fs.existsSync(cssTemplatePath)) {
        fs.copyFileSync(cssTemplatePath, path.join(outputDir, 'style.css'));
    }

    console.log(`[NODE] Rapport generert: ${grade} (${finalDocRate.toFixed(1)}% dokumentasjon)`);

} catch (err) {
    console.error("[NODE] Feil under generering:", err.message);
    process.exit(1);
}