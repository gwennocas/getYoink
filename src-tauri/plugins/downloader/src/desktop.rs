use crate::DownloadRequest;
use serde_json::{json, Value};
use std::{
    collections::HashMap,
    io::{BufRead, BufReader, Read},
    path::PathBuf,
    process::{Command, Stdio},
    sync::{Mutex, OnceLock},
};
use tauri::{AppHandle, Manager, Runtime};

static PROGRESS: OnceLock<Mutex<HashMap<String, f64>>> = OnceLock::new();
static ENGINE_INIT: Mutex<()> = Mutex::new(());
fn progress() -> &'static Mutex<HashMap<String, f64>> {
    PROGRESS.get_or_init(|| Mutex::new(HashMap::new()))
}
fn engine_dir<R: Runtime>(app: &AppHandle<R>) -> Result<PathBuf, String> {
    let bundled = app
        .path()
        .resource_dir()
        .map_err(|e| e.to_string())?
        .join("engines");
    if bundled.join("yt-dlp.exe").is_file() {
        return Ok(bundled);
    }
    Ok(PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../resources/windows"))
}
fn command<R: Runtime>(app: &AppHandle<R>) -> Result<Command, String> {
    let engines = engine_dir(app)?;
    let executable = if let Some(path) = std::env::var_os("YTDLP_PATH") {
        PathBuf::from(path)
    } else if engines.join("yt-dlp.exe").is_file() {
        // Keep updates in a writable directory, independent of the installation folder.
        let _guard = ENGINE_INIT.lock().map_err(|e| e.to_string())?;
        let local = app
            .path()
            .app_local_data_dir()
            .map_err(|e| e.to_string())?
            .join("engines");
        std::fs::create_dir_all(&local).map_err(|e| e.to_string())?;
        let target = local.join("yt-dlp.exe");
        if !target.is_file() {
            let staging = local.join("yt-dlp.installing.exe");
            std::fs::copy(engines.join("yt-dlp.exe"), &staging).map_err(|e| e.to_string())?;
            std::fs::rename(staging, &target).map_err(|e| e.to_string())?;
        }
        target
    } else {
        PathBuf::from("yt-dlp")
    };
    let mut c = Command::new(executable);
    if engines.join("ffmpeg.exe").is_file() {
        c.arg("--ffmpeg-location").arg(&engines);
    }
    if engines.join("deno.exe").is_file() {
        c.arg("--no-js-runtimes")
            .arg("--js-runtimes")
            .arg(format!("deno:{}", engines.join("deno.exe").display()));
    }
    #[cfg(target_os = "windows")]
    {
        use std::os::windows::process::CommandExt;
        c.creation_flags(0x08000000);
    }
    Ok(c)
}
fn output<R: Runtime>(app: &AppHandle<R>, args: &[&str]) -> Result<String, String> {
    let result = command(app)?.args(args).output().map_err(|_| "Não foi possível iniciar o motor de downloads. Reinstale o getYoink para restaurar os componentes.".to_string())?;
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
            json!({"version": output(app, &["--version"] )?, "destination": destination.to_string_lossy(), "desktop": true}),
        ),
        "update" => {
            let message = output(app, &["--ignore-config", "--update"])?;
            Ok(json!({"message": message}))
        }
        "progress" => {
            let map = progress().lock().map_err(|e| e.to_string())?;
            Ok(json!({"percent": map.get(args["id"].as_str().unwrap_or_default()).unwrap_or(&0.0)}))
        }
        "inspect" => {
            let raw = output(
                app,
                &[
                    "--ignore-config",
                    "--no-playlist",
                    "--skip-download",
                    "--dump-single-json",
                    "--",
                    args["url"].as_str().ok_or("Link ausente")?,
                ],
            )?;
            let info: Value = serde_json::from_str(&raw).map_err(|e| e.to_string())?;
            Ok(
                json!({"title": info["title"], "uploader": info["uploader"], "duration": info["duration"]}),
            )
        }
        "download" => {
            let request: DownloadRequest =
                serde_json::from_value(args).map_err(|e| e.to_string())?;
            std::fs::create_dir_all(&destination).map_err(|e| e.to_string())?;
            let mut cmd = command(app)?;
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
                // Drain the pipe completely so verbose errors cannot block the child.
                let mut reader = BufReader::new(stderr);
                let mut buffer = [0; 4096];
                while let Ok(count) = reader.read(&mut buffer) {
                    if count == 0 {
                        break;
                    }
                    if text.len() < 65536 {
                        text.push_str(&String::from_utf8_lossy(&buffer[..count]));
                    }
                }
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
        "open_folder" => {
            std::fs::create_dir_all(&destination).map_err(|e| e.to_string())?;
            #[cfg(target_os = "windows")]
            Command::new("explorer.exe")
                .arg(&destination)
                .spawn()
                .map_err(|e| e.to_string())?;
            #[cfg(target_os = "macos")]
            Command::new("open")
                .arg(&destination)
                .spawn()
                .map_err(|e| e.to_string())?;
            #[cfg(target_os = "linux")]
            Command::new("xdg-open")
                .arg(&destination)
                .spawn()
                .map_err(|e| e.to_string())?;
            Ok(json!({}))
        }
        _ => Err("Comando desconhecido".into()),
    }
}
