const fs   = require('fs');
const path = require('path');

// directories to skip entirely
const EXCLUDE_DIRS = new Set([
    'node_modules', 'dist', 'build', 'coverage', '.angular',
    '.cache', '.idea', 'out', 'target', '.git', '.mvn', '.vscode'
]);

// file extensions to skip entirely
const EXCLUDE_EXTS = new Set([
    '.iml', '.log', '.class', '.jar', '.war', '.png', '.jpg',
    '.jpeg', '.gif', '.ico', '.svg', '.woff', '.woff2', '.ttf',
    '.eot', '.map', '.lock'
]);

// only dump contents for these extensions
const CONTENT_EXTS = new Set([
    '.java', '.ts', '.html', '.scss', '.css', '.json', '.xml',
    '.properties', '.yml', '.yaml', '.md', '.txt', '.http',
    '.sql', '.sh', '.js'
]);

const structureLines = [];
const contentLines   = [];

function dumpTree(dirPath, prefix = '') {
    let entries;
    try {
        entries = fs.readdirSync(dirPath, { withFileTypes: true });
    } catch {
        return;
    }

    entries = entries
        .filter(e => {
            if (e.isDirectory()) return !EXCLUDE_DIRS.has(e.name);
            return !EXCLUDE_EXTS.has(path.extname(e.name));
        })
        .sort((a, b) => {
            if (a.isDirectory() !== b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.name.localeCompare(b.name);
        });

    entries.forEach((entry, i) => {
        const isLast    = i === entries.length - 1;
        const connector = isLast ? '└── ' : '├── ';
        const suffix    = entry.isDirectory() ? '/' : '';
        structureLines.push(`${prefix}${connector}${entry.name}${suffix}`);

        if (entry.isDirectory()) {
            const extension = isLast ? '    ' : '│   ';
            dumpTree(path.join(dirPath, entry.name), prefix + extension);
        }
    });
}

function dumpContents(dirPath) {
    let entries;
    try {
        entries = fs.readdirSync(dirPath, { withFileTypes: true });
    } catch {
        return;
    }

    entries
        .filter(e => {
            if (e.isDirectory()) return !EXCLUDE_DIRS.has(e.name);
            return !EXCLUDE_EXTS.has(path.extname(e.name));
        })
        .sort((a, b) => {
            if (a.isDirectory() !== b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.name.localeCompare(b.name);
        })
        .forEach(entry => {
            const fullPath = path.join(dirPath, entry.name);
            if (entry.isDirectory()) {
                dumpContents(fullPath);
            } else if (CONTENT_EXTS.has(path.extname(entry.name))) {
                const relative = path.relative(process.cwd(), fullPath).replace(/\\/g, '/');
                contentLines.push('');
                contentLines.push('='.repeat(80));
                contentLines.push(`FILE: ${relative}`);
                contentLines.push('='.repeat(80));
                try {
                    const content = fs.readFileSync(fullPath, 'utf8');
                    contentLines.push(content);
                } catch {
                    contentLines.push('[could not read file]');
                }
            }
        });
}

const root = process.cwd();

// structure section
structureLines.push(path.basename(root) + '/');
dumpTree(root);

// contents section
dumpContents(root);

const output = [
    '='.repeat(80),
    'PROJECT STRUCTURE',
    '='.repeat(80),
    ...structureLines,
    '',
    '='.repeat(80),
    'FILE CONTENTS',
    '='.repeat(80),
    ...contentLines
].join('\n');

const outFile = path.join(root, 'project_dump.txt');
fs.writeFileSync(outFile, output, 'utf8');
console.log(`Done. project_dump.txt written to ${root}`);
