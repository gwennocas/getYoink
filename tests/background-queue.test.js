import test from 'node:test';
import assert from 'node:assert/strict';
import { reconcileDownloads } from '../src/background-queue.js';

const job = (id, status, extra = {}) => ({ id, status, title: `Vídeo ${id}`, format: 'mp4', percent: 0, ...extra });

test('restaura conclusões feitas em segundo plano com a data e caminho nativos', () => {
  const done = job('a', 'done', { percent: 100, path: 'Downloads/getYoink/a.mp4', date: '2026-10-28T12:00:00Z' });
  const next = reconcileDownloads([job('a', 'running')], [done, job('b', 'running', { percent: 48 })], []);
  assert.equal(next.finished, 1);
  assert.equal(next.history[0].path, done.path);
  assert.equal(next.history[0].date, done.date);
  assert.equal(next.queue[1].progress, 48);
});

test('reconectar e limpar histórico não reimportam downloads já vistos', () => {
  const incoming = [job('a', 'done', { percent: 100 })];
  const first = reconcileDownloads([], incoming, []);
  const reconnected = reconcileDownloads(JSON.parse(JSON.stringify(first.queue)), incoming, first.history);
  assert.equal(reconnected.finished, 0);
  assert.equal(reconnected.history.length, 1);
  const cleared = reconcileDownloads(reconnected.queue, incoming, []);
  assert.equal(cleared.history.length, 0);
});

test('interrupção de processo aparece como falha sem gerar histórico', () => {
  const next = reconcileDownloads([job('a', 'running')], [job('a', 'failed', { error: 'O serviço foi interrompido.' })], []);
  assert.equal(next.queue[0].status, 'failed');
  assert.match(next.queue[0].error, /interrompido/);
  assert.equal(next.history.length, 0);
});

test('fila avança nativamente e resultados não duplicam IDs já importados', () => {
  const incoming = [job('a', 'done'), job('b', 'done'), job('c', 'running', { percent: 24 })];
  const next = reconcileDownloads([job('a', 'running'), job('b', 'waiting')], incoming, [job('a', 'done')]);
  assert.deepEqual(next.history.map(j => j.id), ['b', 'a']);
  assert.equal(next.queue[2].status, 'running');
});
