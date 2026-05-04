const fs = require('fs');
const path = require('path');

const dataPath = path.join(__dirname, '../../quality_report.json');
const outputDir = path.join(__dirname, '../output');
const templatePath = path.join(__dirname, '../templates/quality_template.html');

if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
}

try {
    const data = JSON.parse(fs.readFileSync(dataPath, 'utf8'));
    
    // Logikk for karaktersetting
    let grade = 'F';
    let statusClass = 'danger';

    if (data.doc_rate >= 90 && data.todo_count < 2) {
        grade = 'A';
        statusClass = 'success';
    } else if (data.doc_rate >= 70) {
        grade = 'B';
        statusClass = 'warning';
    } else if (data.doc_rate >= 50) {
        grade = 'C';
        statusClass = 'warning';
    }

    let html = fs.readFileSync(templatePath, 'utf8');
    html = html.replace(/{{GRADE}}/g, grade)
               .replace(/{{STATUS_CLASS}}/g, statusClass)
               .replace('{{FILES}}', data.total_files)
               .replace('{{LINES}}', data.total_lines)
               .replace('{{TODOS}}', data.todo_count)
               .replace('{{DOC_RATE}}', data.doc_rate.toFixed(1))
               .replace('{{TIMESTAMP}}', new Date().toLocaleString());

    fs.writeFileSync(path.join(outputDir, 'index.html'), html);
    console.log(`[NODE] Kvalitetsrapport fullført. Karakter: ${grade}`);
} catch (err) {
    console.error("[NODE] Feil under generering:", err.message);
    process.exit(1);
}