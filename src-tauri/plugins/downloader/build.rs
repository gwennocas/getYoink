const COMMANDS: &[&str] = &[
    "inspect_video",
    "download_video",
    "download_progress",
    "engine_status",
    "update_engine",
];
fn main() {
    tauri_plugin::Builder::new(COMMANDS)
        .android_path("android")
        .build();
}
