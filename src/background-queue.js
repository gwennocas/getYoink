import { publicError } from './domain.js';

// Durable Android results are imported once, even after clearing history or recreating the WebView.
export function reconcileDownloads(previous, incoming, history) {
  const completed = new Set(previous.filter(j => j.status === 'done').map(j => j.id));
  const queue = incoming.map(job => ({ ...job,
    progress: Math.min(100, Math.max(0, Number(job.percent) || 0)),
    error: job.error ? publicError(job.error) : undefined,
  }));
  const finished = queue.filter(j => j.status === 'done' && !completed.has(j.id));
  const known = new Set(history.map(j => j.id).filter(Boolean));
  const additions = finished.filter(j => !known.has(j.id)).map(j => ({
    id: j.id, title: j.title, format: j.format, path: j.path,
    date: j.date || new Date().toISOString(),
  }));
  return { queue, history: [...additions.reverse(), ...history].slice(0, 100), finished: finished.length };
}
