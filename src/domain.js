export function validateUrl(value) {
  let url;
  try { url = new URL(value.trim()); } catch { throw new Error('Cole um link válido do YouTube ou Instagram.'); }
  const host = url.hostname.toLowerCase();
  const allowed = ['youtube.com', 'www.youtube.com', 'm.youtube.com', 'music.youtube.com', 'youtu.be', 'instagram.com', 'www.instagram.com'];
  if (url.protocol !== 'https:' || url.username || url.password || !allowed.includes(host) || (url.port && url.port !== '443')) {
    throw new Error('Use um link HTTPS do YouTube ou Instagram.');
  }
  if (host.endsWith('instagram.com') && !/^\/(p|reel|reels|tv)\/[^/]+/.test(url.pathname)) {
    throw new Error('Cole o link de uma publicação ou reel público do Instagram.');
  }
  url.hash = '';
  return url.toString();
}
export function escapeHtml(text = '') { return String(text).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]); }
export function duration(seconds) { const s = Math.max(0, Math.floor(Number(seconds) || 0)); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`; }
export function publicError(error) {
  const text = String(error?.message || error);
  if (/private|login|log in|sign in|cookies|authentication|403/i.test(text)) return 'Este conteúdo exige autenticação ou está indisponível. O getYoink acessa apenas conteúdo público, sem cookies.';
  return text.length > 420 ? `${text.slice(0, 420)}…` : text;
}
