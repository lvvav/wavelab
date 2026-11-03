#!/usr/bin/env node
// Собирает заранее теги и обложки всех треков: tags/index.json и tags/c/*.jpg.
// Сайт читает этот файл сразу при открытии, поэтому названия и обложки видны без ожидания.
// Использует те же функции чтения тегов, что и index.html (берёт их оттуда).
// Запуск из корня репозитория: node tools/build-tags.js   (нужны node и ffmpeg)
const fs = require('fs'), path = require('path'), cp = require('child_process'), crypto = require('crypto');
const root = process.cwd(), musicDir = path.join(root, 'music'), outDir = path.join(root, 'tags'), covDir = path.join(outDir, 'c');
const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
const a = html.indexOf('function u32(b,o)'), b = html.indexOf('function applyTags');
if (a < 0 || b < 0 || b < a) { console.error('Не нашёл функции чтения тегов в index.html'); process.exit(1); }
const lib = new Function(html.slice(a, b) + '\nreturn {id3Tags,flacTags,m4aTags,clean};')();
fs.mkdirSync(covDir, { recursive: true });

function walk(d) { let o = []; for (const e of fs.readdirSync(d, { withFileTypes: true })) { const p = path.join(d, e.name); if (e.isDirectory()) o = o.concat(walk(p)); else o.push(p); } return o; }
const files = walk(musicDir).filter(f => /\.(mp3|flac|m4a)$/i.test(f)).sort();
const old = fs.existsSync(path.join(outDir, 'index.json')) ? JSON.parse(fs.readFileSync(path.join(outDir, 'index.json'), 'utf8')).t || {} : {};
const T = {}, used = new Set(); let reread = 0;

(async () => {
  for (const f of files) {
    const key = path.relative(root, f).split(path.sep).join('/'), size = fs.statSync(f).size, ck = key + '|' + size;
    if (old[ck] && (!old[ck][1] || fs.existsSync(path.join(covDir, old[ck][1] + '.jpg')))) { T[ck] = old[ck]; if (old[ck][1]) used.add(old[ck][1]); continue; }
    reread++;
    const fd = fs.openSync(f, 'r');
    const get = (pos, len) => { const n = Math.max(0, Math.min(len, size - pos)), buf = Buffer.alloc(n); fs.readSync(fd, buf, 0, n, pos); return Promise.resolve(new Uint8Array(buf.buffer, buf.byteOffset, n)); };
    const fn = /\.flac$/i.test(f) ? lib.flacTags : /\.m4a$/i.test(f) ? lib.m4aTags : lib.id3Tags;
    let r; try { r = await fn(get); } catch (e) { fs.closeSync(fd); continue; }
    fs.closeSync(fd);
    const g = lib.clean(r.tags, r); g.p = r.pic ? 1 : 0;
    let h = '';
    if (r.pic) {
      h = crypto.createHash('sha1').update(r.pic).digest('hex').slice(0, 12);
      const out = path.join(covDir, h + '.jpg');
      if (!fs.existsSync(out)) {
        const tmp = path.join(outDir, '.tmp-pic'); fs.writeFileSync(tmp, r.pic);
        const q = cp.spawnSync('ffmpeg', ['-nostdin', '-y', '-loglevel', 'error', '-i', tmp, '-vf', "scale='min(320,iw)':'min(320,ih)':force_original_aspect_ratio=decrease", '-frames:v', '1', '-q:v', '4', out]);
        fs.rmSync(tmp, { force: true });
        if (q.status !== 0 || !fs.existsSync(out)) { h = ''; g.p = -1; }
      }
    }
    if (h) used.add(h);
    T[ck] = h ? [g, h] : [g];
  }
  for (const f of fs.readdirSync(covDir)) if (!used.has(f.replace(/\.jpg$/, ''))) fs.rmSync(path.join(covDir, f));
  fs.writeFileSync(path.join(outDir, 'index.json'), JSON.stringify({ v: 1, t: T }));
  const sz = fs.readdirSync(covDir).reduce((s, f) => s + fs.statSync(path.join(covDir, f)).size, 0);
  console.log(`треков: ${Object.keys(T).length} из ${files.length}, прочитано заново: ${reread}, обложек: ${used.size} (${(sz / 1048576).toFixed(1)} МБ), index.json: ${(fs.statSync(path.join(outDir, 'index.json')).size / 1024).toFixed(0)} КБ`);
})();
