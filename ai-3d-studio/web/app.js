import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';

const $ = (id) => document.getElementById(id);
const MIN_FACES = 500;
let maxFaces = 500_000;
let current = null;        // metadata of the model shown
let imageFile = null;
let busy = false;

/* ---------------- 3D viewport ---------------- */
const view = $('viewport');
const renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true });
renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
renderer.toneMapping = THREE.ACESFilmicToneMapping;
view.appendChild(renderer.domElement);

const scene = new THREE.Scene();
const pmrem = new THREE.PMREMGenerator(renderer);
scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
const camera = new THREE.PerspectiveCamera(40, 1, 0.01, 100);
camera.position.set(2.2, 1.6, 2.6);
const controls = new OrbitControls(camera, renderer.domElement);
controls.enableDamping = true;
const key = new THREE.DirectionalLight(0xffffff, 1.2);
key.position.set(3, 5, 4);
scene.add(key, new THREE.HemisphereLight(0xffffff, 0x444444, 0.6));
const grid = new THREE.GridHelper(4, 20, 0x888888, 0x555555);
grid.material.transparent = true; grid.material.opacity = 0.35;
scene.add(grid);

const solidMat = new THREE.MeshStandardMaterial({ color: 0xc8c8c8, roughness: 0.75, metalness: 0 });
const normalMat = new THREE.MeshNormalMaterial();
const wireMat = new THREE.MeshBasicMaterial({ color: 0x111111, wireframe: true, transparent: true, opacity: 0.35 });
let model = null;
let shading = 'textured';
let wire = false;

function setBackground() {
  scene.background = new THREE.Color(getComputedStyle(document.body).getPropertyValue('--viewport').trim());
  wireMat.color.set(matchMedia('(prefers-color-scheme: light)').matches ? 0x222222 : 0xdddddd);
}
setBackground();
matchMedia('(prefers-color-scheme: light)').addEventListener('change', setBackground);

function resize() {
  const { clientWidth: w, clientHeight: h } = view;
  renderer.setSize(w, h, false);
  camera.aspect = w / Math.max(h, 1);
  camera.updateProjectionMatrix();
}
new ResizeObserver(resize).observe(view);
renderer.setAnimationLoop(() => { controls.update(); renderer.render(scene, camera); });

function meshes() { const out = []; model?.traverse((o) => o.isMesh && !o.userData.isWire && out.push(o)); return out; }

function applyShading() {
  for (const m of meshes()) {
    m.material = shading === 'solid' ? solidMat : shading === 'normals' ? normalMat : m.userData.orig;
    m.userData.wire.visible = wire;
  }
  document.querySelectorAll('#shading button').forEach((b) => b.classList.toggle('on', b.dataset.v === shading));
  $('wire').classList.toggle('on', wire);
}

function frameModel() {
  if (!model) return;
  const box = new THREE.Box3().setFromObject(model);
  const size = box.getSize(new THREE.Vector3()).length() || 1;
  const center = box.getCenter(new THREE.Vector3());
  controls.target.copy(center);
  const dir = camera.position.clone().sub(controls.target).normalize();
  camera.position.copy(center).addScaledVector(dir, size * 1.25);
  camera.near = size / 100; camera.far = size * 100; camera.updateProjectionMatrix();
}

function disposeModel() {
  if (!model) return;
  scene.remove(model);
  model.traverse((o) => {
    if (!o.isMesh) return;
    o.geometry.dispose();
    const mat = o.userData.orig;
    if (mat && !o.userData.isWire) {
      for (const k in mat) if (mat[k]?.isTexture) mat[k].dispose();
      mat.dispose();
    }
  });
  model = null;
}

const loader = new GLTFLoader();
async function showModel(meta, { keepCamera = false } = {}) {
  const gltf = await loader.loadAsync(meta.model_url + '?v=' + meta.version);
  disposeModel();
  model = gltf.scene;
  // Center on the grid, scale to about 2 units.
  const box = new THREE.Box3().setFromObject(model);
  const s = 2 / (box.getSize(new THREE.Vector3()).length() || 1);
  model.scale.setScalar(s);
  const b2 = new THREE.Box3().setFromObject(model);
  const c = b2.getCenter(new THREE.Vector3());
  model.position.sub(new THREE.Vector3(c.x, b2.min.y, c.z));
  let tris = 0, verts = 0;
  for (const m of meshes()) {
    if (!m.geometry.attributes.normal) m.geometry.computeVertexNormals();
    m.userData.orig = m.material;
    const w = new THREE.Mesh(m.geometry, wireMat);
    w.userData.isWire = true;
    w.visible = wire;
    m.add(w);
    m.userData.wire = w;
    tris += m.geometry.index ? m.geometry.index.count / 3 : m.geometry.attributes.position.count / 3;
    verts += m.geometry.attributes.position.count;
  }
  scene.add(model);
  applyShading();
  if (!keepCamera) frameModel();
  $('empty').hidden = true;
  $('stats').hidden = false;
  $('stats').innerHTML = `Triangles ${Math.round(tris).toLocaleString()}<br>Vertices ${verts.toLocaleString()}<br>` +
    `Texture ${meta.texture_size}×${meta.texture_size}<br>${meta.ai ? 'AI' : 'Demo (not AI)'} · seed ${meta.seed}`;
  const dl = $('download');
  dl.href = meta.model_url;
  dl.download = (meta.prompt || 'model').slice(0, 40).replace(/[^\w-]+/g, '_') + '.glb';
  dl.hidden = false;
}

/* ---------------- Controls ---------------- */
document.querySelectorAll('#shading button').forEach((b) => b.onclick = () => { shading = b.dataset.v; applyShading(); });
$('wire').onclick = () => { wire = !wire; applyShading(); };
$('grid').onclick = () => { grid.visible = !grid.visible; $('grid').classList.toggle('on', grid.visible); };
$('spin').onclick = () => { controls.autoRotate = !controls.autoRotate; $('spin').classList.toggle('on', controls.autoRotate); };
$('frame').onclick = frameModel;
addEventListener('keydown', (e) => {
  if (e.target.closest('input, textarea, select')) return;
  const k = e.key.toLowerCase();
  if (k === '1') shading = 'solid'; else if (k === '2') shading = 'textured'; else if (k === '3') shading = 'normals';
  else if (k === 'w') wire = !wire;
  else if (k === 'g') return $('grid').click();
  else if (k === 'r') return $('spin').click();
  else if (k === 'f') return frameModel();
  else return;
  applyShading();
});

let quality = 'balanced';
document.querySelectorAll('#quality button').forEach((b) => b.onclick = () => {
  quality = b.dataset.v;
  document.querySelectorAll('#quality button').forEach((x) => x.classList.toggle('on', x === b));
});
$('randSeed').onclick = () => { $('seed').value = Math.floor(Math.random() * 1e6); };

// Triangle slider is logarithmic so both 1k and 500k are easy to pick.
const toFaces = (t) => Math.round(MIN_FACES * (maxFaces / MIN_FACES) ** (t / 1000) / 100) * 100 || MIN_FACES;
const toSlider = (f) => Math.round(1000 * Math.log(f / MIN_FACES) / Math.log(maxFaces / MIN_FACES));
function setFaces(f) {
  f = Math.max(MIN_FACES, Math.min(maxFaces, Math.round(f)));
  $('facesNum').value = f;
  $('faces').value = toSlider(f);
  updateApply();
}
function setMaxFaces(m) {
  const cur = +$('facesNum').value;
  maxFaces = Math.max(m, MIN_FACES * 2);
  $('facesMax').textContent = maxFaces.toLocaleString() + ' (full detail)';
  setFaces(cur);
}
$('faces').oninput = () => { $('facesNum').value = toFaces(+$('faces').value); updateApply(); };
$('facesNum').onchange = () => setFaces(+$('facesNum').value || MIN_FACES);
$('tex').onchange = updateApply;

function updateApply() {
  const changed = current && (+$('facesNum').value !== current.target_faces || +$('tex').value !== current.texture_size);
  $('apply').disabled = busy || !current?.can_reexport || !changed;
  $('apply').title = current && !current.can_reexport ? 'Only the last few models stay in memory. Regenerate to change this one.' : '';
}

/* Image input */
const drop = $('drop');
function setImage(file) {
  imageFile = file;
  $('preview').hidden = !file;
  $('clearImage').hidden = !file;
  $('dropText').hidden = !!file;
  if (file) $('preview').src = URL.createObjectURL(file);
}
drop.onclick = (e) => { if (e.target !== $('clearImage')) $('image').click(); };
drop.onkeydown = (e) => { if (e.key === 'Enter' || e.key === ' ') $('image').click(); };
$('image').onchange = () => setImage($('image').files[0] || null);
$('clearImage').onclick = (e) => { e.stopPropagation(); $('image').value = ''; setImage(null); };
drop.ondragover = (e) => { e.preventDefault(); drop.classList.add('over'); };
drop.ondragleave = () => drop.classList.remove('over');
drop.ondrop = (e) => {
  e.preventDefault(); drop.classList.remove('over');
  const f = [...e.dataTransfer.files].find((x) => x.type.startsWith('image/'));
  if (f) setImage(f);
};

/* ---------------- Server calls ---------------- */
function showError(msg) { $('error').hidden = !msg; $('error').textContent = msg || ''; }
function setBusy(b) {
  busy = b;
  $('go').disabled = b;
  $('go').textContent = b ? 'Working…' : 'Generate 3D model';
  updateApply();
}

async function api(path, opts) {
  let res;
  try { res = await fetch(path, opts); } catch { throw new Error('Cannot reach the studio server. Is it running?'); }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.detail || `Error ${res.status}`);
  return data;
}

async function runJob(jobId) {
  $('job').hidden = false;
  for (;;) {
    const job = await api(`/api/jobs/${jobId}`);
    $('barFill').style.width = Math.round(job.progress * 100) + '%';
    $('jobText').textContent = job.stage;
    if (job.status === 'done') { $('job').hidden = true; return job.result; }
    if (job.status === 'error') { $('job').hidden = true; throw new Error(job.error); }
    await new Promise((r) => setTimeout(r, 700));
  }
}

$('go').onclick = async () => {
  showError('');
  const prompt = $('prompt').value.trim();
  if (!prompt && !imageFile) return showError('Type a prompt or add an image.');
  const fd = new FormData();
  fd.append('prompt', prompt);
  fd.append('generator', $('generator').value);
  fd.append('seed', $('seed').value || '1');
  fd.append('quality', quality);
  fd.append('target_faces', $('facesNum').value);
  fd.append('texture_size', $('tex').value);
  if (imageFile) fd.append('image', imageFile);
  setBusy(true);
  try {
    const { job_id } = await api('/api/generate', { method: 'POST', body: fd });
    await open(await runJob(job_id));
    loadHistory();
  } catch (e) { showError(e.message); } finally { setBusy(false); }
};

$('apply').onclick = async () => {
  showError('');
  setBusy(true);
  try {
    const { job_id } = await api(`/api/models/${current.id}/export`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ target_faces: +$('facesNum').value, texture_size: +$('tex').value }),
    });
    await open(await runJob(job_id), { keepCamera: true });
    loadHistory();
  } catch (e) { showError(e.message); } finally { setBusy(false); }
};

async function open(meta, opts) {
  current = meta;
  setMaxFaces(meta.source_faces);
  setFaces(meta.target_faces);
  $('tex').value = meta.texture_size;
  await showModel(meta, opts);
  document.querySelectorAll('#history li').forEach((li) => li.classList.toggle('on', li.dataset.id === meta.id));
  updateApply();
}

async function loadHistory() {
  const items = await api('/api/models').catch(() => []);
  const ul = $('history');
  ul.innerHTML = '';
  if (!items.length) { ul.innerHTML = '<li class="m">Nothing yet</li>'; return; }
  for (const it of items) {
    const li = document.createElement('li');
    li.dataset.id = it.id;
    li.classList.toggle('on', it.id === current?.id);
    const t = document.createElement('span'); t.className = 't';
    t.textContent = it.prompt || (it.has_image ? 'From image' : 'Untitled');
    const m = document.createElement('span'); m.className = 'm';
    m.textContent = `${Math.round(it.faces / 1000)}k${it.ai ? '' : ' · demo'}`;
    const del = document.createElement('button'); del.className = 'del'; del.textContent = '✕'; del.title = 'Delete';
    del.onclick = async (e) => {
      e.stopPropagation();
      if (!confirm('Delete this model?')) return;
      await api(`/api/models/${it.id}`, { method: 'DELETE' }).catch((er) => showError(er.message));
      loadHistory();
    };
    li.onclick = () => open(it).catch((er) => showError(er.message));
    li.append(t, m, del);
    ul.append(li);
  }
}

async function init() {
  try {
    const st = await api('/api/status');
    const sel = $('generator');
    for (const g of st.generators) {
      const o = document.createElement('option');
      o.value = g.id;
      o.textContent = g.label + (g.available ? '' : ' — unavailable');
      o.disabled = !g.available;
      o.title = g.reason;
      sel.append(o);
    }
    sel.value = st.default;
    for (const s of st.texture_sizes) {
      const o = document.createElement('option'); o.value = s; o.textContent = `${s} × ${s}`; $('tex').append(o);
    }
    $('tex').value = 1024;
    const ai = st.generators.find((g) => g.ai && g.available);
    const chip = $('engine');
    chip.textContent = ai ? `AI · ${ai.reason}` : 'Demo mode (no AI)';
    chip.classList.toggle('ai', !!ai);
    if (!ai) {
      const why = st.generators.find((g) => g.ai)?.reason;
      chip.title = why || '';
      showError(`AI engine unavailable: ${why}. Using demo shapes until it is set up (see README).`);
    }
  } catch (e) { showError(e.message); }
  setMaxFaces(maxFaces);
  setFaces(50_000);
  loadHistory();
}
init();
