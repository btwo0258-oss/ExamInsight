const fs = require('fs');
const path = require('path');

function walk(dir) {
    let results = [];
    if (!fs.existsSync(dir)) return results;
    const list = fs.readdirSync(dir);
    list.forEach(file => {
        file = path.join(dir, file);
        const stat = fs.statSync(file);
        if (stat && stat.isDirectory()) {
            results = results.concat(walk(file));
        } else {
            results.push(file);
        }
    });
    return results;
}

const dirs = [
    'd:/IdeaCode/LLM_New/frontend/src/views/admin',
    'd:/IdeaCode/LLM_New/frontend/src/layout_admin',
    'd:/IdeaCode/LLM_New/frontend/src/components/admin'
];

dirs.forEach(dir => {
    const files = walk(dir);
    files.forEach(file => {
        if (file.endsWith('.vue') || file.endsWith('.ts')) {
            let content = fs.readFileSync(file, 'utf8');
            let newContent = content
                .replace(/@\/components\//g, '@/components/admin/')
                .replace(/@\/api\//g, '@/api/admin')
                .replace(/@\/stores\/auth/g, '@/stores/adminAuth')
                .replace(/@\/layouts\//g, '@/layout_admin/');
            if (content !== newContent) {
                fs.writeFileSync(file, newContent, 'utf8');
                console.log('Updated:', file);
            }
        }
    });
});
