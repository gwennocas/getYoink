import './style.css';
import { icon } from './icons.js';
import { call, native } from './native.js';
import { reconcileDownloads } from './background-queue.js';
import { validateUrl, escapeHtml as esc, duration, publicError } from './domain.js';

function readHistory() { try { const h = JSON.parse(localStorage.getItem('getyoink-history') || '[]'); return Array.isArray(h) ? h.slice(0, 100).filter(x => x && typeof x.title === 'string') : []; } catch { return []; } }
const state = { tab: 'download', format: 'mp4', quality: '1080', info: null, url: '', busy: false, updating: false, queue: [], history: readHistory(), engine: null };
let toastTimer;
const app = document.querySelector('#app');
app.innerHTML = `
  <header class="topbar"><a class="brand" href="#" aria-label="getYoink, início"><span class="brand-icon">${icon('download')}</span><span>get<span class="brand-accent">Yoink</span><span class="brand-dot">.</span></span></a><span class="header-note">um link. seu arquivo.</span><span class="version">v0.1.2 <span class="status-dot"></span></span></header>
  <main>
    <section class="hero"><span class="eyebrow"><span class="tiny-grid">⊞</span> PEGA E LEVA.</span><h1>Se você curtiu,<br class="mobile-break"> <span>guarda.</span></h1><p>Vídeos e áudios dos seus links favoritos.<br>Direto pro seu dispositivo, sem complicação.</p><div class="platforms"><span>${icon('youtube')} YouTube</span><span class="separator">/</span><span>${icon('instagram')} Instagram</span><span class="public-pill">somente públicos</span></div></section>
    <nav class="tabs" aria-label="Navegação"><button data-tab="download" class="tab active">${icon('download')} Baixar</button><button data-tab="history" class="tab">${icon('history')} Histórico <span id="history-count" class="count">0</span></button><button data-tab="settings" class="tab">${icon('settings')} Ajustes</button></nav>
    <section id="download-panel" class="panel">
      <form id="link-form" class="card input-card"><div class="card-heading"><span class="step">01</span><h2>O que vamos baixar?</h2><span class="mini-label">COLE O LINK</span></div><label class="sr-only" for="url">Link do vídeo público</label><div class="url-field">${icon('link')}<input id="url" name="url" type="url" placeholder="https://youtube.com/watch?v=…" autocomplete="off" required><button id="paste" type="button" class="icon-button" title="Colar link" aria-label="Colar link">${icon('clipboard')}</button></div><div class="input-bottom"><span>Vídeos, Shorts, Reels e publicações públicas.</span><button class="text-button" id="inspect" type="submit">Buscar vídeo ${icon('arrow')}</button></div><p id="form-error" class="error" role="alert" hidden></p><div id="video-info" hidden></div></form>
      <section class="card format-card"><div class="card-heading"><span class="step">02</span><h2>Do seu jeito.</h2></div><div class="format-options"><button data-format="mp4" class="format-option selected" aria-pressed="true"><span class="format-icon">${icon('video')}</span><span><strong>Vídeo</strong><small>Imagem e som, juntos.</small></span><span class="format-tag">MP4</span><span class="radio"></span></button><button data-format="mp3" class="format-option" aria-pressed="false"><span class="format-icon">${icon('music')}</span><span><strong>Só o áudio</strong><small>Pra ouvir onde quiser.</small></span><span class="format-tag">MP3</span><span class="radio"></span></button></div><div class="quality-row"><label for="quality">Qualidade</label><select id="quality"><option value="1080">Até 1080p · Full HD</option><option value="720">Até 720p · HD</option><option value="480">Até 480p · Menor arquivo</option><option value="best">Melhor disponível</option></select></div><button id="download" class="primary-button">${icon('download')} <span>Baixar vídeo</span>${icon('arrow')}</button><div class="save-hint">${icon('folder')} <span id="destination">Downloads / getYoink</span></div></section>
      <section class="queue-section"><div class="section-heading"><h2>${icon('queue')} Sua fila <span class="count" id="queue-count">0</span></h2><span>UM DE CADA VEZ, SEM PRESSA.</span></div><div id="queue"></div></section>
      <div class="feature-grid"><article class="feature-card"><span class="feature-icon green">${icon('bolt')}</span><h3>Do link pro play.</h3><p>Cole, escolha o formato<br> e deixe o resto com a gente.</p></article><article class="feature-card"><span class="feature-icon orange">${icon('music')}</span><h3>Seu formato favorito.</h3><p>MP4 pra assistir.<br> MP3 pra levar no bolso.</p></article><article class="feature-card"><span class="feature-icon aqua">${icon('shield')}</span><h3>Fica com você.</h3><p>Sem conta, sem cookies.<br> Os arquivos são seus.</p></article></div>
    </section>
    <section id="history-panel" class="panel" hidden><div class="section-heading"><h2>Seus últimos yoinks.</h2><button id="clear-history" class="text-button">Limpar histórico</button></div><div id="history"></div><p class="muted footnote">O histórico fica neste dispositivo. Limpar a lista não apaga os arquivos.</p></section>
    <section id="settings-panel" class="panel" hidden><article class="card"><div class="card-heading"><span class="feature-icon orange">${icon('refresh')}</span><h2>Sempre pronto pro próximo link.</h2></div><p class="muted">Os sites mudam. Atualize o yt-dlp quando um link público deixar de funcionar.</p><div class="engine-info"><span>yt-dlp</span><code id="engine-version">Verificando…</code></div><button id="update-engine" class="primary-button">${icon('refresh')} Atualizar yt-dlp</button><p id="update-status" class="muted footnote" role="status"></p></article><article class="card settings-note">${icon('shield')}<div><h3>Só conteúdo público.</h3><p>O getYoink não usa login ou cookies. Posts privados e conteúdos que exigem autenticação não são baixados.</p></div></article><article class="card settings-note">${icon('folder')}<div><h3>Um lugar pros seus arquivos.</h3><p id="settings-destination">Downloads/getYoink. No Android, os arquivos são publicados na pasta Downloads do sistema.</p><p id="background-hint">Mantenha o app aberto durante o download nesta versão.</p></div></article></section>
    <footer><span>feito para guardar o que importa.</span><span>getYoink <span class="footer-star">✳</span> local por natureza</span></footer>
  </main><div id="toast" class="toast" role="status" hidden></div>`;

const $ = s => document.querySelector(s);
function toast(message) { $('#toast').textContent = message; $('#toast').hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(() => $('#toast').hidden = true, 5500); }
function error(message) { $('#form-error').textContent = message; $('#form-error').hidden = !message; }
function persist() { try { localStorage.setItem('getyoink-history', JSON.stringify(state.history.slice(0, 100))); } catch { toast('Não foi possível salvar o histórico local.'); } }
function renderLists() {
  $('#history-count').textContent = state.history.length;
  $('#queue-count').textContent = state.queue.filter(x => ['waiting', 'running'].includes(x.status)).length;
  $('#queue').innerHTML = state.queue.length ? state.queue.map(job => `<article class="job card"><span class="job-icon">${icon(job.format === 'mp3' ? 'music' : 'video')}</span><div class="job-body"><strong>${esc(job.title)}</strong><small>${esc(job.format.toUpperCase())} <span>·</span> ${esc(({ waiting: 'Na fila', running: job.progress >= 99 ? 'Finalizando arquivo…' : 'Baixando…', done: 'Salvo no dispositivo', failed: 'Não foi possível baixar' })[job.status])}</small>${job.status === 'running' ? `<progress aria-label="Progresso do download" max="100" value="${job.progress}"></progress>` : ''}${job.error ? `<p class="error">${esc(job.error)}</p>` : ''}</div><span class="job-status ${job.status}">${job.status === 'done' ? icon('check') : job.status === 'running' ? Math.floor(job.progress) + '%' : job.status === 'failed' ? icon('close') : '···'}</span></article>`).join('') : `<div class="empty-queue">${icon('download')}<span>Sua próxima descoberta começa com um link.</span></div>`;
  $('#history').innerHTML = state.history.length ? state.history.map(job => `<article class="job card"><span class="job-icon">${icon(job.format === 'mp3' ? 'music' : 'video')}</span><div class="job-body"><strong>${esc(job.title)}</strong><small>${esc(job.format.toUpperCase())} · ${esc(new Date(job.date).toLocaleDateString('pt-BR'))}</small><small class="file-path">${esc(job.path || '')}</small></div><span class="green">${icon('check')}</span></article>`).join('') : `<div class="empty-state">${icon('history')}<h3>Ainda está tudo por aqui.</h3><p>Seus downloads concluídos vão aparecer nesta lista.</p></div>`;
}
document.querySelector('.brand').addEventListener('click', e => { e.preventDefault(); $('[data-tab="download"]').click(); });
document.querySelectorAll('[data-tab]').forEach(button => button.addEventListener('click', () => { state.tab = button.dataset.tab; document.querySelectorAll('[data-tab]').forEach(b => { b.classList.toggle('active', b === button); b.setAttribute('aria-current', b === button ? 'page' : 'false'); }); document.querySelectorAll('.panel').forEach(p => p.hidden = p.id !== `${state.tab}-panel`); }));
document.querySelectorAll('[data-format]').forEach(button => button.addEventListener('click', () => { state.format = button.dataset.format; document.querySelectorAll('[data-format]').forEach(b => { b.classList.toggle('selected', b === button); b.setAttribute('aria-pressed', String(b === button)); }); $('#quality').innerHTML = state.format === 'mp3' ? '<option value="320">320 kbps · Alta qualidade</option><option value="192">192 kbps · Equilibrado</option><option value="128">128 kbps · Menor arquivo</option>' : '<option value="1080">Até 1080p · Full HD</option><option value="720">Até 720p · HD</option><option value="480">Até 480p · Menor arquivo</option><option value="best">Melhor disponível</option>'; $('#download span').textContent = state.format === 'mp3' ? 'Baixar áudio' : 'Baixar vídeo'; }));
$('#paste').addEventListener('click', async () => { try { $('#url').value = await navigator.clipboard.readText(); $('#url').dispatchEvent(new Event('input')); $('#url').focus(); } catch { toast('Toque no campo de link e use a opção Colar do dispositivo.'); $('#url').focus(); } });
$('#url').addEventListener('input', () => { state.info = null; $('#video-info').hidden = true; error(''); });
async function inspect() {
  if (state.busy || state.updating) return null;
  error('');
  try {
    const url = validateUrl($('#url').value);
    state.busy = true; $('#inspect').disabled = true; $('#inspect').textContent = 'Buscando…';
    const info = await call('inspect_video', { url });
    // Ignore stale metadata if the user changed the input while fetching.
    if (validateUrl($('#url').value) !== url) return null;
    state.info = info; state.url = url;
    $('#video-info').hidden = false;
    $('#video-info').innerHTML = `<span class="video-badge">${icon(url.includes('instagram.com') ? 'instagram' : 'youtube')}</span><div><strong>${esc(info.title)}</strong><small>${esc(info.uploader || 'Conteúdo público')}${Number(info.duration) > 0 ? ` · ${duration(info.duration)}` : ''}</small></div><span class="green">${icon('check')}</span>`;
    return info;
  } catch (e) { error(publicError(e)); return null; }
  finally { state.busy = false; $('#inspect').disabled = false; $('#inspect').innerHTML = `Buscar vídeo ${icon('arrow')}`; }
}
$('#link-form').addEventListener('submit', e => { e.preventDefault(); inspect(); });
let adding = false;
$('#download').addEventListener('click', async () => {
  if (adding || state.busy || state.updating) return;
  adding = true; $('#download').disabled = true;
  const format = state.format, quality = $('#quality').value;
  try { await engineReady; if (native && !state.engine) throw new Error('Não foi possível iniciar o motor. Reabra o app e tente novamente.'); const url = validateUrl($('#url').value); const info = state.info && state.url === url ? state.info : await inspect(); if (!info) return;
    const job = { id: crypto.randomUUID(), url, title: info.title, format, quality, status: 'waiting', progress: 0 };
    if (state.engine?.backgroundDownloads) {
      await call('download_video', { request: job });
      await syncBackgroundQueue();
      toast('Adicionado à fila. Pode sair do app; o download continua.');
    } else {
      state.queue.push(job);
      renderLists(); runQueue(); toast('Adicionado à fila. Pode colar o próximo link.');
    }
  } catch (e) { error(publicError(e)); } finally { adding = false; $('#download').disabled = false; }
});
let running = false;
async function runQueue() {
  if (running) return;
  const job = state.queue.find(j => j.status === 'waiting'); if (!job) return;
  running = true; job.status = 'running'; renderLists();
  const poll = setInterval(async () => { try { const p = await call('download_progress', { id: job.id }); if (job.status === 'running') { job.progress = Math.min(99, Math.max(0, Number(p.percent) || 0)); renderLists(); } } catch { /* The download command reports actionable errors. */ } }, 1100);
  try { const result = await call('download_video', { request: { id: job.id, url: job.url, format: job.format, quality: job.quality } }); job.status = 'done'; job.path = result.path; job.progress = 100; state.history.unshift({ title: job.title, format: job.format, path: result.path, date: new Date().toISOString() }); state.history = state.history.slice(0, 100); persist(); toast('Yoink! Arquivo salvo no dispositivo.'); }
  catch (e) { job.status = 'failed'; job.error = publicError(e); }
  finally { clearInterval(poll); running = false; renderLists(); runQueue(); }
}
let syncing = false;
async function syncBackgroundQueue() {
  if (syncing || !state.engine?.backgroundDownloads) return;
  syncing = true;
  try {
    const { jobs } = await call('download_progress', { id: '' });
    const next = reconcileDownloads(state.queue, jobs, state.history);
    state.queue = next.queue; state.history = next.history;
    persist();
    try { localStorage.setItem('getyoink-background-queue', JSON.stringify(state.queue)); } catch { /* Native results remain available. */ }
    renderLists();
    if (next.finished) toast('Yoink! Arquivo salvo no dispositivo.');
  } catch (e) { $('#update-status').textContent = publicError(e); }
  finally { syncing = false; }
}
setInterval(() => { if (!document.hidden) syncBackgroundQueue(); }, 1500);
document.addEventListener('visibilitychange', () => { if (!document.hidden) syncBackgroundQueue(); });
window.addEventListener('focus', () => syncBackgroundQueue());
$('#clear-history').addEventListener('click', () => { state.history = []; persist(); renderLists(); });
let backgroundRestored = false;
async function engineStatus() {
  if (!native) { $('#engine-version').textContent = 'Prévia web'; return; }
  try { state.engine = await call('engine_status');
    if (state.engine.backgroundDownloads) {
      if (!backgroundRestored) {
        try { const saved = JSON.parse(localStorage.getItem('getyoink-background-queue') || '[]'); if (Array.isArray(saved)) state.queue = saved.filter(j => j && typeof j.id === 'string'); } catch { /* Native snapshot restores the queue. */ }
        backgroundRestored = true;
      }
      $('#background-hint').textContent = 'Pode trocar de app ou apagar a tela: a fila continua no Android. Permita as notificações para acompanhar o progresso.';
      await syncBackgroundQueue();
    }
    $('#engine-version').textContent = state.engine.version;
    if (state.engine.destination) {
      $('#destination').textContent = state.engine.destination;
      $('#settings-destination').textContent = state.engine.destination;
    }
  }
  catch (e) { $('#engine-version').textContent = 'Indisponível'; $('#update-status').textContent = publicError(e); }
}
$('#update-engine').addEventListener('click', async () => {
  if (running || state.queue.some(j => ['waiting', 'running'].includes(j.status)) || state.busy) { toast('Aguarde a fila e a consulta terminarem antes de atualizar.'); return; }
  state.updating = true; $('#update-engine').disabled = true; $('#inspect').disabled = true; $('#update-status').textContent = 'Baixando a versão estável do yt-dlp…';
  try { const result = await call('update_engine'); $('#update-status').textContent = result.message; await engineStatus(); toast('yt-dlp atualizado e pronto.'); }
  catch (e) { $('#update-status').textContent = publicError(e); }
  finally { state.updating = false; $('#update-engine').disabled = false; $('#inspect').disabled = false; }
});
renderLists();
const engineReady = engineStatus();
