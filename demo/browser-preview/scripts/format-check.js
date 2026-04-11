import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const rootDir = fileURLToPath(new URL('..', import.meta.url));
const extensions = new Set(['.js', '.json', '.html', '.css']);

const files = await collectFiles(rootDir);
const failures = [];

for (const filePath of files) {
    const content = await readFile(filePath, 'utf8');
    if (content.includes('\r')) {
        failures.push(`${relativePath(filePath)}: 使用了 CRLF 换行`);
    }
    if (/[ \t]+$/m.test(content)) {
        failures.push(`${relativePath(filePath)}: 存在行尾空白字符`);
    }
    if (!content.endsWith('\n')) {
        failures.push(`${relativePath(filePath)}: 文件末尾缺少换行`);
    }
}

if (failures.length > 0) {
    console.error('format:check 未通过：');
    for (const failure of failures) {
        console.error(`- ${failure}`);
    }
    process.exit(1);
}

async function collectFiles(directory) {
    const entries = await readdir(directory, { withFileTypes: true });
    const collected = [];

    for (const entry of entries) {
        if (entry.name === 'node_modules') {
            continue;
        }

        const fullPath = path.join(directory, entry.name);
        if (entry.isDirectory()) {
            collected.push(...(await collectFiles(fullPath)));
            continue;
        }

        if (extensions.has(path.extname(entry.name))) {
            collected.push(fullPath);
        }
    }

    return collected;
}

function relativePath(filePath) {
    return path.relative(rootDir, filePath) || '.';
}
