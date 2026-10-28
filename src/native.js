import { invoke, isTauri } from '@tauri-apps/api/core';
export const native = isTauri();
export async function call(command, args = {}) {
  if (!native) throw new Error('Você está na prévia web. Abra o aplicativo Android ou desktop para usar o yt-dlp.');
  return invoke(`plugin:downloader|${command}`, args);
}
