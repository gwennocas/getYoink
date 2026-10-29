# getYoink

Um link. Seu arquivo. Aplicativo para Android e Windows em Tauri 2 + Rust, com integração Android em Kotlin para yt-dlp e FFmpeg. Interface responsiva em português com tema Gruvbox, fundo pontilhado e cartões de bordas finas.

## Downloads

- [Windows x64 — primeira versão de teste](https://github.com/gwennocas/getYoink/releases/tag/v0.1.2-windows.1): instalador com os motores incluídos. Ainda sem assinatura digital e com validação manual da janela pendente.
- [Android ARM64 — v0.1.2](https://github.com/gwennocas/getYoink/releases/tag/v0.1.2): APK com assinatura de produção para Android 10 ou superior.

## Funcionalidades

- YouTube: vídeos e Shorts; Instagram: vídeos de publicações e Reels públicos.
- MP4 com limite de 480p, 720p, 1080p ou melhor resolução disponível.
- Extração MP3 em 128, 192 ou 320 kbps. A qualidade da fonte limita o resultado.
- Consulta de título, autor e duração; fila sequencial e progresso real do motor.
- Histórico local dos downloads concluídos, com caminho do arquivo.
- Android: fila nativa em segundo plano, com progresso e resultado em notificações. Pode trocar de app ou apagar a tela.
- Android: exportação via MediaStore para `Downloads/getYoink`, sem acesso amplo ao armazenamento.
- Ajustes → **Atualizar yt-dlp** baixa a versão estável dentro do Android. Não requer reinstalar o APK.
- Sem login, cookies ou suporte a publicações privadas. O app ignora configurações externas do yt-dlp.

## Desenvolvimento no Windows

### Versão para PC (Windows x64)

O instalador inclui yt-dlp, FFmpeg/FFprobe e Deno. Não é necessário instalar Python ou configurar o PATH. Os arquivos vão para a pasta Downloads/getYoink do usuário. A interface em duas colunas aproveita a janela do PC, com botão **Abrir pasta de downloads**, **Ctrl + L** para selecionar o campo de link e **Ctrl + Enter** para baixar. Pode minimizar a janela; mantenha o app e o PC ligados até concluir a fila. O fechamento é bloqueado enquanto houver tarefas em andamento.

Para desenvolver a versão PC, instale Node.js LTS, Rust estável MSVC e Visual Studio Build Tools com C++. JDK e SDK Android são necessários apenas para Android.

```powershell
npm install
npm run pc:dev
npm run pc:build
```

O primeiro comando PC baixa os motores das releases oficiais no GitHub e verifica o SHA-256 informado em cada release. Os downloads ficam em `.tools/windows-engines`, e os recursos incluídos no instalador em `src-tauri/resources/windows` (ambos ignorados pelo Git). Para buscar novos motores para uma nova distribuição, remova essa pasta de recursos e execute `npm run pc:prepare` novamente.

O instalador fica em `artifacts/getYoink-0.1.2-windows-x64-setup.exe`, instala para o usuário atual e usa o WebView2 do Windows (o instalador o prepara quando necessário). A distribuição local ainda não tem assinatura de código do Windows. O yt-dlp usado pelo app fica nos dados locais do usuário, permitindo **Ajustes → Atualizar yt-dlp** sem privilégios de administrador. Deno e FFmpeg acompanham a versão do instalador. As licenças e referências das versões incluídas ficam na pasta `engines` da instalação.

O identificador e os builds Android são preservados. A configuração específica de Windows fica em `src-tauri/tauri.windows.conf.json`, carregada automaticamente pelo Tauri no Windows. Referência: [instaladores Windows no Tauri](https://v2.tauri.app/distribute/windows-installer/).

Validação em 29/10/2026: nove testes JavaScript e dois testes Rust aprovados; compilação Windows release e instalador NSIS gerados; consulta pública e downloads reais em MP4 e MP3 concluídos com os motores incluídos, e streams/duração conferidos com FFprobe. A janela nativa abriu e reconheceu os motores e o destino. O layout e a troca de formato foram conferidos na prévia web. A automação de entrada/captura da janela nativa falhou, portanto o fluxo completo pelos controles do app, o fechamento durante tarefas e a instalação pelo assistente ainda precisam de validação manual.

Pré-requisitos: Node.js LTS, Rust estável (MSVC), Visual Studio Build Tools com C++, JDK 21, SDK Android com plataforma 36, Build Tools 36, Platform Tools e NDK. O Android mínimo deste projeto é 10 (API 29).

```powershell
npm install
rustup target add aarch64-linux-android
```

O script `scripts/env.ps1` configura a sessão atual com o JDK Temurin instalado e o SDK localizado em `.tools/android-sdk`. Não muda variáveis globais do Windows. Em outro computador, configure `JAVA_HOME`, `ANDROID_HOME` e `NDK_HOME` para suas instalações.

```powershell
. ./scripts/env.ps1
npm run tauri -- android init --ci # apenas se src-tauri/gen/android ainda não existir
npm run android:build
```

No Windows, o Tauri precisa criar links simbólicos em cada compilação. Se o Windows negar essa etapa, execute `scripts/build-apk.ps1` em um PowerShell aberto como administrador. O projeto não precisa habilitar o modo de desenvolvedor do Windows.

O APK ARM64 será gerado em `src-tauri/gen/android/app/build/outputs/apk/`. Builds release sem assinatura não podem ser instalados diretamente. Para um APK de teste assinado com a chave de debug:

```powershell
. ./scripts/env.ps1
npm run tauri -- android build --debug --apk --target aarch64
```

O mesmo comando está disponível como `npm run android:apk`.

## APK com assinatura de produção

O build de release usa uma chave própria do getYoink, separada da chave de debug. A configuração privada fica, por padrão, em `%USERPROFILE%/.android/getYoink-release/keystore.properties`, fora do repositório. Para outro local ou CI, defina `YOINK_SIGNING_PROPERTIES` com o caminho do arquivo. Ele deve conter `storeFile` (caminho absoluto do JKS, usando `/` no Windows), `storePassword`, `keyAlias` e `keyPassword`. A chave e as senhas nunca devem entrar no Git, nos logs ou nos assets públicos da release.

```powershell
npm run android:release
```

O script configura o ambiente, compila em modo release, verifica a assinatura e copia o APK para `artifacts/getYoink-VERSAO-arm64.apk`. Quando o Windows não permite links simbólicos, ele copia a biblioteca Rust já compilada e conclui o empacotamento pelo Gradle, sem precisar de administrador. A compilação de release falha se a configuração de assinatura estiver ausente. O build de debug continua independente da chave de produção.

Guarde no gerenciador o arquivo `getYoink-release.jks` como anexo, as duas senhas e o alias. Guardar somente as senhas não permite recuperar a chave. Mantenha um backup seguro e reutilize a mesma chave nas próximas versões; aumente a versão do app a cada publicação.

A primeira instalação de produção não pode atualizar diretamente o APK assinado com a chave de debug. Será necessário desinstalar a versão de teste, o que remove o histórico local. Os arquivos exportados para Downloads permanecem. Não desinstale o app automaticamente durante o build.

Referências: [assinatura Android no Tauri](https://v2.tauri.app/distribute/sign/android/), [assinatura de aplicativos Android](https://developer.android.com/studio/publish/app-signing).

Antes de publicar um APK de produção, teste o próprio artefato otimizado no aparelho. A versão 0.1.2 preserva os construtores dos campos extras ZIP do Commons Compress, usados por reflexão ao extrair o motor; removê-los com R8 causava fechamento na abertura da versão 0.1.1. A assinatura válida e o build concluído não verificam esse comportamento em execução.

```powershell
./scripts/smoke-android-release.ps1 -Device IP:PORTA -ApkPath artifacts/getYoink-0.1.2-arm64.apk
```

O teste instala a atualização sem desinstalar ou limpar dados e abre o app três vezes, verificando processo e logs de crash. Execute com a fila vazia: cada abertura usa uma parada forçada, que interrompe downloads ativos. Complete a validação consultando um vídeo e realizando um download no APK de release.

## Testar por Wi-Fi (Android 11+)

PC e celular precisam estar na mesma rede. No celular: Opções do desenvolvedor → Depuração sem fio → Parear dispositivo com código. O código de pareamento é temporário; informe-o somente quando formos conectar.

```powershell
. ./scripts/env.ps1
adb pair IP_DO_CELULAR:PORTA_DE_PAREAMENTO
# O ADB solicita o código sem gravá-lo no comando.
adb connect IP_DO_CELULAR:PORTA_DE_CONEXAO
adb devices -l
adb install -r CAMINHO_DO_APK_DEBUG
adb shell am start -n app.getyoink.mobile/.MainActivity
```

A porta de conexão exibida na tela principal de depuração sem fio é diferente da porta de pareamento. Desative a depuração sem fio quando terminar.

## Prévia e verificações

```powershell
npm run dev
npm run build
npm test
cargo test --manifest-path src-tauri/plugins/downloader/Cargo.toml
```

No navegador, a interface abre como prévia e explica que downloads exigem o app nativo. Não há downloads simulados. No Windows, use os comandos `pc:dev` e `pc:build` para preparar os motores incluídos. Em outros desktops, instale `yt-dlp` e `ffmpeg` no PATH; opcionalmente configure `YTDLP_PATH`. Nesse caso, a atualização depende de uma instalação binária que suporte `yt-dlp --update`.

### Validação em aparelho real — 28/10/2026

Testado via ADB por Wi-Fi em Moto G7 Plus (ARM64), LineageOS com Android 15:

- APK debug compilado, assinatura verificada e instalação concluída.
- Atualização interna para yt-dlp `2026.10.26` concluída e preservada ao atualizar o app.
- Consulta e download do YouTube em MP4 e conversão para MP3 a 320 kbps.
- Consulta e download em MP4 de um Reel público do Instagram, sem cookies.
- Arquivos exportados para `Downloads/getYoink` e histórico atualizado.
- Nove testes JavaScript e dois testes Rust aprovados; Kotlin Android e APK debug compilados.
- Fila nativa de MP4 e MP3 concluída em sequência com o app fora de foco e a tela apagada; serviço e bloqueio de CPU encerrados ao esvaziar a fila.

APK de teste local: `artifacts/getYoink-0.1.0-arm64-debug.apk` (ignorado pelo Git). O APK de debug inclui ferramentas de diagnóstico e é maior que uma compilação de distribuição. A partir da versão 0.1.1, o projeto também está configurado para gerar APKs de release com uma chave privada externa ao repositório.

## Limites desta primeira versão

No Android, a fila roda em um serviço em primeiro plano e continua ao trocar de app ou apagar a tela, sem depender dos temporizadores da interface. No Android 13+, permita as notificações para ver o progresso; negar essa permissão não impede o download. Ao voltar, a interface sincroniza os resultados e atualiza o histórico.

Forçar a parada do app, reiniciar o celular ou o sistema encerrar o processo interrompe a fila. Na próxima abertura, os itens interrompidos aparecem como falha; adicione os links novamente. Resultados concluídos ficam preservados. Não há retomada automática após reinício, playlists, fotos ou login. No desktop, mantenha o app aberto durante o download.

O Android 15+ limita serviços `dataSync` em segundo plano a seis horas por período de 24 horas. Ao receber o aviso de limite, o serviço encerra a fila e registra a interrupção, conforme a [documentação do Android](https://developer.android.com/develop/background-work/services/fgs/timeout). Alguns links públicos podem exigir autenticação ou sofrer bloqueio do provedor; nesses casos o app apresenta a falha e não tenta usar cookies. Atualizar o yt-dlp não garante contornar restrições do site.

## Estrutura

- `src/`: interface e fila.
- `src-tauri/`: aplicação Rust/Tauri e permissões da janela local.
- `src-tauri/plugins/downloader/`: validação, comandos e integração desktop.
- `src-tauri/plugins/downloader/android/`: yt-dlp, FFmpeg, atualização e MediaStore.
- `scripts/`: ambiente e comandos de desenvolvimento Android.

Referências: [Tauri mobile plugins](https://v2.tauri.app/develop/plugins/develop-mobile/), [pré-requisitos Tauri](https://v2.tauri.app/start/prerequisites/), [youtubedl-android](https://github.com/yausername/youtubedl-android), [yt-dlp](https://github.com/yt-dlp/yt-dlp).
