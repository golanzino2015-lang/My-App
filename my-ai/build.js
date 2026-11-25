// Builds index.html: the app page with the AI engine inlined, so it is one file that works offline.
const fs = require('fs'), path = require('path');
const dir = __dirname, read = (f) => fs.readFileSync(path.join(dir, f), 'utf8');
const scripts = ['engine', 'lessons', 'host'].map((n) => `<script id="${n}-src">\n${read(n + '.js')}\n</script>`).join('\n');
fs.writeFileSync(path.join(dir, 'index.html'), read('app.html').replace('<!--SCRIPTS-->', scripts));
console.log('Wrote index.html', fs.statSync(path.join(dir, 'index.html')).size, 'bytes');
