import test from 'node:test';
import assert from 'node:assert/strict';
import { validateUrl, escapeHtml, publicError, duration } from '../src/domain.js';
test('permite YouTube e publicações públicas do Instagram', () => {
  for (const url of ['https://youtu.be/abc', 'https://www.youtube.com/watch?v=abc', 'https://m.youtube.com/shorts/abc', 'https://www.instagram.com/reel/abc/', 'https://instagram.com/p/abc/']) assert.equal(validateUrl(url), url);
});
test('rejeita hosts enganosos, credenciais, protocolos e páginas de login', () => {
  for (const url of ['https://youtube.com.evil.org/watch', 'https://evil.youtube.com/watch', 'http://youtube.com/watch', 'javascript:alert(1)', 'file:///tmp/video', 'https://user:password@youtube.com/watch', 'https://youtube.com:444/watch', 'https://instagram.com/accounts/login/', 'https://instagram.com/someone/']) assert.throws(() => validateUrl(url), undefined, url);
});
test('títulos remotos não injetam HTML', () => assert.equal(escapeHtml('<img src=x onerror="alert(1)">'), '&lt;img src=x onerror=&quot;alert(1)&quot;&gt;'));
test('falha de conteúdo privado explica a restrição sem solicitar cookies', () => assert.match(publicError('Login required. Pass cookies'), /apenas conteúdo público, sem cookies/));
test('duração trata valores ausentes e vídeos longos', () => { assert.equal(duration(null), '0:00'); assert.equal(duration(3601), '60:01'); });
