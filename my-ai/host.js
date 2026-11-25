/* Runs the AI: training loop, chatting, teaching and saving. Talks to the page with messages.
 * The same code runs inside a Web Worker or, as a fallback, on the page itself.
 */
'use strict';
(function (root) {
const C = root.TinyAICore, Lessons = root.TinyAILessons;

const MICRO_BATCHES = 8;           // sequences per learning step (8 x 64 = 512 words)
const PEAK_LR = 3e-3, MIN_LR = 4e-4, WARMUP = 150, DECAY_STEPS = 8000;
const CORPUS_CONVS = 9000;
const SAVE_EVERY_MS = 45000;

let post = () => {};
let brain = null;   // { name, size, vocab, model, taught, stats }
let data = null;    // { stream: Int32Array, starts: Int32Array }
let training = false;
let lastSave = 0;
let dirty = false;
let lossWindow = [];
let rate = { t: 0, tokens: 0, perSec: 0 };

/* ---------- Saving (IndexedDB, works in workers and pages) ---------- */
const DB = 'tiny-ai', STORE = 'brain';
function idb() {
  return new Promise((res, rej) => {
    if (typeof indexedDB === 'undefined') return rej(new Error('no storage'));
    const r = indexedDB.open(DB, 1);
    r.onupgradeneeded = () => r.result.createObjectStore(STORE);
    r.onsuccess = () => res(r.result);
    r.onerror = () => rej(r.error);
  });
}
async function dbDo(mode, fn) {
  const db = await idb();
  return new Promise((res, rej) => {
    const tx = db.transaction(STORE, mode);
    const req = fn(tx.objectStore(STORE));
    tx.oncomplete = () => { db.close(); res(req && req.result); };
    tx.onerror = () => { db.close(); rej(tx.error); };
  });
}
async function save() {
  if (!brain) return;
  const m = brain.model;
  const rec = {
    v: 1, name: brain.name, size: brain.size, words: brain.vocab.words, taught: brain.taught,
    stats: brain.stats, cfg: m.cfg, step: m.step,
    params: m.params.buffer.slice(0), m: m.m.buffer.slice(0), vv: m.v.buffer.slice(0),
  };
  try {
    await dbDo('readwrite', (s) => s.put(rec, 'main'));
    lastSave = Date.now(); dirty = false;
    post({ type: 'saved', at: lastSave });
  } catch (e) {
    post({ type: 'warn', text: 'Progress could not be saved on this device. Keep this page open while training.' });
  }
}
async function load() {
  try {
    const rec = await dbDo('readonly', (s) => s.get('main'));
    if (!rec) return null;
    const model = new C.GPT(rec.cfg);
    model.params.set(new Float32Array(rec.params));
    model.m.set(new Float32Array(rec.m));
    model.v.set(new Float32Array(rec.vv));
    model.step = rec.step;
    return { name: rec.name, size: rec.size, vocab: new C.Vocab(rec.words), taught: rec.taught || [], stats: rec.stats, model };
  } catch (e) { return null; }
}

/* ---------- Lessons -> numbers ---------- */
function buildData(seed = 12345) {
  C.setProper([...Lessons.PROPER_NAMES, brain.name.toLowerCase()]);
  const convs = Lessons.buildConversations(brain.name, brain.taught, CORPUS_CONVS, seed);
  // Learn any new words (new brain, or words from taught lessons).
  for (const conv of convs) for (const [u, a] of conv) {
    for (const w of C.userWords(u)) if (w !== 'zzunk') brain.vocab.add(w);
    for (const w of C.normalize(a)) brain.vocab.add(w);
  }
  if (brain.model && brain.vocab.size > brain.model.cfg.V) brain.model.growVocab(brain.vocab.size + 64);
  const stream = [], starts = [];
  for (const conv of convs) {
    starts.push(stream.length);
    for (const [u, a] of conv) stream.push(...C.encodeExchange(brain.vocab, u, a));
  }
  data = { stream: Int32Array.from(stream), starts: Int32Array.from(starts) };
  brain.stats.lessonWords = stream.length;
}

function newBrain(name, size) {
  const vocab = new C.Vocab(C.SPECIAL);
  brain = { name: name.trim() || 'Pip', size, vocab, taught: [], model: null,
    stats: { tokens: 0, seconds: 0, lossHist: [], quiz: null, sample: null, lessonWords: 0, born: Date.now() } };
  buildData();
  const s = C.SIZES[size];
  // Leave room for 256 more words so teaching rarely needs to grow the table.
  brain.model = new C.GPT({ V: vocab.size + 256, C: s.C, L: s.L, H: s.H, T: s.T }, (Date.now() & 0xffff) + 1);
}

/* ---------- Training ---------- */
function lrAt(step) {
  if (step < WARMUP) return PEAK_LR * (step + 1) / WARMUP;
  const p = Math.min(1, (step - WARMUP) / DECAY_STEPS);
  return MIN_LR + (PEAK_LR - MIN_LR) * 0.5 * (1 + Math.cos(Math.PI * p));
}
const pickRand = (n) => Math.floor(Math.random() * n);

let micro = 0, microLoss = 0;
function trainOneSequence() {
  const m = brain.model, T = m.cfg.T, st = data.stream;
  let start = data.starts[pickRand(data.starts.length)];
  if (start + T + 1 > st.length) start = 0;
  const inp = st.subarray(start, start + T), tgt = st.subarray(start + 1, start + T + 1);
  microLoss += m.forward(inp, tgt, 1, T);
  m.backward(inp, tgt, 1, T, 1 / MICRO_BATCHES);
  micro++;
  rate.tokens += T;
  brain.stats.tokens += T;
  if (micro === MICRO_BATCHES) {
    m.update(lrAt(m.step));
    const loss = microLoss / MICRO_BATCHES;
    micro = 0; microLoss = 0;
    lossWindow.push(loss);
    if (lossWindow.length >= 10) {
      const avg = lossWindow.reduce((a, b) => a + b, 0) / lossWindow.length;
      lossWindow = [];
      const h = brain.stats.lossHist;
      h.push([m.step, +avg.toFixed(4)]);
      if (h.length > 400) brain.stats.lossHist = h.filter((_, i) => i % 2 === 0);
    }
    dirty = true;
    if (m.step % 60 === 0 || m.step === 20) runQuiz();
    return true;
  }
  return false;
}

let loopTimer = null;
function loop() {
  loopTimer = null;
  if (!training) return;
  const t0 = Date.now(), budget = root.document ? 30 : 200;  // keep the page responsive if not in a worker
  let stepped = false;
  while (Date.now() - t0 < budget) stepped = trainOneSequence() || stepped;
  const now = Date.now();
  brain.stats.seconds += (now - t0) / 1000;
  if (now - rate.t > 2000) {
    rate.perSec = Math.round(rate.tokens / ((now - rate.t) / 1000));
    rate.t = now; rate.tokens = 0;
    progress();
  }
  if (dirty && now - lastSave > SAVE_EVERY_MS) save();
  loopTimer = setTimeout(loop, 0);
}
function setTraining(on) {
  training = on && !!brain;
  if (training) { rate = { t: Date.now(), tokens: 0, perSec: 0 }; if (!loopTimer) loop(); }
  else if (dirty) save();
  progress();
}

/* ---------- Talking ---------- */
function generate(history, text, { greedy = false, temperature = 0.7, topK = 12, maxWords = 48 } = {}) {
  const m = brain.model, T = m.cfg.T, V = m.cfg.V, vocab = brain.vocab;
  const userIds = C.encodeExchange(vocab, text, null);
  // Keep as much recent conversation as fits, leaving room for the reply.
  let ctx = userIds.slice(-(T - 24));
  for (let i = history.length - 1; i >= 0; i--) {
    const ex = C.encodeExchange(vocab, history[i][0], history[i][1]);
    if (ex.length + ctx.length > T - 24) break;
    ctx = ex.concat(ctx);
  }
  const out = [];
  const banned = new Set([0, C.UNK, C.USER, C.AI]);
  const logitsBuf = new Float32Array(V);
  for (let k = 0; k < maxWords; k++) {
    const window = ctx.slice(-T);
    const logits = m.forward(Int32Array.from(window), null, 1, window.length, true);
    logitsBuf.set(logits);
    for (const b of banned) logitsBuf[b] = -Infinity;
    for (let v = vocab.size; v < V; v++) logitsBuf[v] = -Infinity; // unused word slots
    let next;
    if (greedy) {
      next = 0; for (let v = 1; v < V; v++) if (logitsBuf[v] > logitsBuf[next]) next = v;
    } else {
      const idx = Array.from(logitsBuf.keys()).sort((a, b) => logitsBuf[b] - logitsBuf[a]).slice(0, topK);
      const mx = logitsBuf[idx[0]];
      const ps = idx.map((i) => Math.exp((logitsBuf[i] - mx) / temperature));
      let x = Math.random() * ps.reduce((a, b) => a + b, 0);
      next = idx[idx.length - 1];
      for (let j = 0; j < idx.length; j++) { if ((x -= ps[j]) <= 0) { next = idx[j]; break; } }
    }
    if (next === C.END) break;
    out.push(next); ctx.push(next);
  }
  return out.map((i) => vocab.words[i]);
}

function runQuiz() {
  const items = Lessons.quiz(brain.name);
  let score = 0;
  const results = items.map(([q, want, check]) => {
    const got = C.detokenize(generate([], q, { greedy: true, maxWords: 20 }));
    const ok = check ? check(got.toLowerCase()) : got.toLowerCase() === C.detokenize(C.normalize(want)).toLowerCase();
    if (ok) score++;
    return { q, got, ok };
  });
  brain.stats.quiz = { score, total: items.length, step: brain.model.step };
  const s = results[Math.floor(Math.random() * results.length)];
  brain.stats.sample = { q: s.q, a: s.got || '…' };
  progress();
}

/* ---------- Messages ---------- */
function summary() {
  if (!brain) return { type: 'state', exists: false };
  const m = brain.model;
  return {
    type: 'state', exists: true, name: brain.name, size: brain.size, sizeLabel: C.SIZES[brain.size].label,
    params: m.numParams, words: brain.vocab.size, taught: brain.taught, training,
  };
}
function progress() {
  if (!brain) return;
  const st = brain.stats;
  post({
    type: 'progress', training, step: brain.model.step, tokens: st.tokens, seconds: st.seconds,
    perSec: training ? rate.perSec : 0, lossHist: st.lossHist, quiz: st.quiz, sample: st.sample,
    lessonWords: st.lessonWords,
  });
}

async function handle(msg, postFn) {
  if (postFn) post = postFn;
  switch (msg.type) {
    case 'init': {
      brain = await load();
      if (brain) buildData();
      post(summary()); progress();
      break;
    }
    case 'create': {
      setTraining(false);
      newBrain(msg.name, msg.size);
      await save();
      post(summary()); progress();
      break;
    }
    case 'train': setTraining(msg.on); post(summary()); break;
    case 'chat': {
      if (!brain) return;
      const words = generate(msg.history || [], msg.text, msg.opts || {});
      post({ type: 'reply', id: msg.id, text: C.detokenize(words) || '…' });
      break;
    }
    case 'teach': {
      if (!brain) return;
      brain.taught = msg.pairs.filter((p) => p[0].trim() && p[1].trim()).slice(0, 200);
      buildData();
      dirty = true; await save();
      post(summary()); progress();
      break;
    }
    case 'reset': {
      setTraining(false);
      brain = null; data = null;
      try { await dbDo('readwrite', (s) => s.delete('main')); } catch (e) { /* nothing saved */ }
      post(summary());
      break;
    }
    case 'save': if (dirty) await save(); break;
  }
}

root.TinyAI = { handle, _test: { newBrain: (n, s) => newBrain(n, s), trainOneSequence, generate, runQuiz, get brain() { return brain; }, setPost: (p) => { post = p; } } };
if (typeof WorkerGlobalScope !== 'undefined' && root instanceof WorkerGlobalScope) {
  root.onmessage = (e) => handle(e.data, (m) => root.postMessage(m));
}
})(typeof self !== 'undefined' ? self : globalThis);
