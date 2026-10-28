#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_downloader::init())
        .run(tauri::generate_context!())
        .expect("Não foi possível iniciar o getYoink");
}
