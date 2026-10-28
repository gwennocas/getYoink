use crate::DownloadRequest;
use serde_json::{json, Value};
use std::{
    collections::HashMap,
    io::{BufRead, BufReader, Read},
    process::{Command, Stdio},
    sync::{Mutex, OnceLock},
};
use tauri::{AppHandle, Manager, Runtime};

static PROGRESS: OnceLock<Mutex<HashMap<String, f64>>> = OnceLock::new();
fn progress() -> &'static Mutex<HashMap<String, f64>> {
    PROGRESS.get_or_init(|| Mutex::new(HashMap::new()))
}
fn command() -> Command {
    let mut c = Command::new(std::env::var_os("YTDLP_PATH").unwrap_or_else(|| "yt-dlp".into()));
    #[cfg(target_os = "windows")]
    {
        use std::os::windows::process::CommandExt;
        c.creation_flags(0x08000000);
    }
    c
}
fn output(args: &[&str]) -> Result<String, String> {
    let result = command().args(args).output().map_err(|e| format!("Não foi possível executar yt-dlp. Instale yt-dlp e FFmpeg ou configure YTDLP_PATH. {e}"))?;
    if !result.status.success() {
        return Err(String::from_utf8_lossy(&result.stderr).to_string());
    }
    Ok(String::from_utf8_lossy(&result.stdout).trim().to_string())
}
pub fn dispatch<R: Runtime>(
    app: &AppHandle<R>,
    action: &str,
    args: Value,
) -> Result<Value, String> {
    let destination = app
        .path()
        .download_dir()
        .map_err(|e| e.to_string())?
        .join("getYoink");
    match action {
        "status" => Ok(
            json!({"version": output(&["--version"] )?, "destination": destination.to_string_lossy()}),
        ),
        "update" => {
            let message = output(&["--ignore-config", "--update"])?;
            Ok(json!({"message": message}))
        }
        "progress" => {
            let map = progress().lock().map_err(|e| e.to_string())?;
            Ok(json!({"percent": map.get(args["id"].as_str().unwrap_or_default()).unwrap_or(&0.0)}))
        }
        "inspect" => {
            let raw = output(&[
                "--ignore-config",
                "--no-playlist",
                "--skip-download",
                "--dump-single-json",
                "--",
                args["url"].as_str().ok_or("Link ausente")?,
            ])?;
            let info: Value = serde_json::from_str(&raw).map_err(|e| e.to_string())?;
            Ok(
                json!({"title": info["title"], "uploader": info["uploader"], "duration": info["duration"]}),
            )
        }
        "download" => {
            let request: DownloadRequest =
                serde_json::from_value(args).map_err(|e| e.to_string())?;
            std::fs::create_dir_all(&destination).map_err(|e| e.to_string())?;
            let mut cmd = command();
            cmd.args([
                "--ignore-config",
                "--no-playlist",
                "--newline",
                "--progress",
                "--progress-template",
                "download:yoink:%(progress._percent_str)s",
                "--print",
                "after_move:filepath",
                "--no-simulate",
                "--windows-filenames",
                "-o",
            ])
            .arg(destination.join("%(title).180B [%(id)s].%(ext)s"));
            if request.format == "mp3" {
                cmd.args([
                    "-x",
                    "--audio-format",
                    "mp3",
                    "--audio-quality",
                    &format!("{}K", request.quality),
                ]);
            } else {
                let limit = if request.quality == "best" {
                    String::new()
                } else {
                    format!("[height<={}]", request.quality)
                };
                cmd.args([
                    "-f",
                    &format!("bv*[ext=mp4]{limit}+ba[ext=m4a]/b[ext=mp4]{limit}"),
                    "--merge-output-format",
                    "mp4",
                ]);
            }
            let mut child = cmd
                .arg("--")
                .arg(&request.url)
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .spawn()
                .map_err(|e| e.to_string())?;
            let stderr = child.stderr.take().ok_or("Sem saída de erros")?;
            let errors = std::thread::spawn(move || {
                let mut text = String::new();
                let _ = stderr.take(65536).read_to_string(&mut text);
                text
            });
            let mut path = String::new();
            for line in BufReader::new(child.stdout.take().ok_or("Sem saída do motor")?).lines() {
                let line = line.map_err(|e| e.to_string())?;
                if let Some(p) = line.strip_prefix("yoink:") {
                    if let Ok(p) = p.trim().trim_end_matches('%').parse::<f64>() {
                        progress()
                            .lock()
                            .map_err(|e| e.to_string())?
                            .insert(request.id.clone(), p);
                    }
                } else if !line.starts_with('[') && !line.trim().is_empty() {
                    path = line;
                }
            }
            let status = child.wait().map_err(|e| e.to_string())?;
            let error = errors.join().unwrap_or_else(|_| "Falha no motor.".into());
            progress()
                .lock()
                .map_err(|e| e.to_string())?
                .remove(&request.id);
            if !status.success() {
                return Err(error);
            }
            if path.is_empty() {
                return Err("O motor não retornou o arquivo final.".into());
            }
            Ok(json!({"path": path}))
        }
        _ => Err("Comando desconhecido".into()),
    }
}
