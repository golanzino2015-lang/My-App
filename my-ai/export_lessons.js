// Writes the Android app's lesson file: app/src/main/assets/myai/lessons.txt.gz
// One conversation per line: user<TAB>ai<TAB>user<TAB>ai... "zzname" stands for the AI's name.
const fs = require('fs'), zlib = require('zlib'), path = require('path');
for (const f of ['engine', 'lessons']) require(path.join(__dirname, f + '.js'));
const L = globalThis.TinyAILessons;
const convs = L.buildConversations('zzname', [], 80000, 2024);
const text = convs.map((c) => c.map(([u, a]) => `${u}\t${a}`.replace(/\n/g, ' ')).join('\t')).join('\n');
const out = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'myai');
fs.mkdirSync(out, { recursive: true });
fs.writeFileSync(path.join(out, 'lessons.txt.gz'), zlib.gzipSync(text, { level: 9 }));
fs.writeFileSync(path.join(out, 'proper.txt'), [...new Set(L.PROPER_NAMES)].join('\n'));
console.log('conversations', convs.length, 'text MB', (text.length / 1e6).toFixed(1),
  'gz KB', Math.round(fs.statSync(path.join(out, 'lessons.txt.gz')).size / 1024));
