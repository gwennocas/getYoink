use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
#[cfg(target_os = "android")]
use tauri::Manager;
use tauri::{
    plugin::{Builder, TauriPlugin},
    AppHandle, Runtime,
};
#[cfg(not(target_os = "android"))]
mod desktop;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DownloadRequest {
    pub id: String,
    pub url: String,
    pub format: String,
    pub quality: String,
}

pub fn validate_url(value: &str) -> Result<String, String> {
    let url = url::Url::parse(value.trim()).map_err(|_| "Link inválido.")?;
    let host = url.host_str().unwrap_or_default();
    if url.scheme() != "https"
        || !url.username().is_empty()
        || url.password().is_some()
        || url.port().is_some()
        || ![
            "youtube.com",
            "www.youtube.com",
            "m.youtube.com",
            "music.youtube.com",
            "youtu.be",
            "instagram.com",
            "www.instagram.com",
        ]
        .contains(&host)
    {
        return Err("Use um link HTTPS do YouTube ou Instagram.".into());
    }
    if host.ends_with("instagram.com") {
        let parts: Vec<_> = url.path_segments().into_iter().flatten().collect();
        if parts.len() < 2
            || !["p", "reel", "reels", "tv"].contains(&parts[0])
            || parts[1].is_empty()
        {
            return Err("Use o link de uma publicação ou reel público do Instagram.".into());
        }
    }
    Ok(url.to_string())
}

fn validate_request(mut request: DownloadRequest) -> Result<DownloadRequest, String> {
    request.url = validate_url(&request.url)?;
    if request.id.is_empty()
        || request.id.len() > 64
        || !request
            .id
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '-')
    {
        return Err("Identificador de download inválido.".into());
    }
    let valid = match request.format.as_str() {
        "mp3" => ["128", "192", "320"].contains(&request.quality.as_str()),
        "mp4" => ["480", "720", "1080", "best"].contains(&request.quality.as_str()),
        _ => false,
    };
    if !valid {
        return Err("Formato ou qualidade inválidos.".into());
    }
    Ok(request)
}

#[cfg(target_os = "android")]
struct Android<R: Runtime>(tauri::plugin::PluginHandle<R>);

async fn dispatch<R: Runtime>(
    app: AppHandle<R>,
    command: &'static str,
    payload: Value,
) -> Result<Value, String> {
    tauri::async_runtime::spawn_blocking(move || {
        #[cfg(target_os = "android")]
        {
            app.state::<Android<R>>()
                .0
                .run_mobile_plugin(command, payload)
                .map_err(|e| e.to_string())
        }
        #[cfg(not(target_os = "android"))]
        {
            desktop::dispatch(&app, command, payload)
        }
    })
    .await
    .map_err(|e| e.to_string())?
}

#[tauri::command]
async fn inspect_video<R: Runtime>(app: AppHandle<R>, url: String) -> Result<Value, String> {
    dispatch(app, "inspect", json!({"url": validate_url(&url)?})).await
}
#[tauri::command]
async fn download_video<R: Runtime>(
    app: AppHandle<R>,
    request: DownloadRequest,
) -> Result<Value, String> {
    dispatch(
        app,
        "download",
        serde_json::to_value(validate_request(request)?).map_err(|e| e.to_string())?,
    )
    .await
}
#[tauri::command]
async fn download_progress<R: Runtime>(app: AppHandle<R>, id: String) -> Result<Value, String> {
    dispatch(app, "progress", json!({"id": id})).await
}
#[tauri::command]
async fn engine_status<R: Runtime>(app: AppHandle<R>) -> Result<Value, String> {
    dispatch(app, "status", json!({})).await
}
#[tauri::command]
async fn update_engine<R: Runtime>(app: AppHandle<R>) -> Result<Value, String> {
    dispatch(app, "update", json!({})).await
}

pub fn init<R: Runtime>() -> TauriPlugin<R> {
    Builder::new("downloader")
        .invoke_handler(tauri::generate_handler![
            inspect_video,
            download_video,
            download_progress,
            engine_status,
            update_engine
        ])
        .setup(|_app, _api| {
            #[cfg(target_os = "android")]
            {
                let handle =
                    _api.register_android_plugin("app.getyoink.downloader", "DownloaderPlugin")?;
                _app.manage(Android(handle));
            }
            Ok(())
        })
        .build()
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn only_supported_public_urls() {
        assert!(validate_url("https://youtu.be/abc").is_ok());
        assert!(validate_url("https://www.instagram.com/reel/abc/").is_ok());
        for url in [
            "http://youtube.com/watch?v=x",
            "https://youtube.com.evil.org/x",
            "https://user:pass@youtube.com/x",
            "file:///etc/passwd",
            "https://instagram.com/accounts/login/",
            "https://youtube.com:444/x",
        ] {
            assert!(validate_url(url).is_err(), "{url}");
        }
    }
    #[test]
    fn rejects_arbitrary_format_arguments() {
        let r = DownloadRequest {
            id: "abc".into(),
            url: "https://youtu.be/x".into(),
            format: "mp3".into(),
            quality: "--exec=bad".into(),
        };
        assert!(validate_request(r).is_err());
    }
}
