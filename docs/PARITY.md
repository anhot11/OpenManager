# MT Manager Feature Parity Matrix

Este documento contiene la matriz exhaustiva de paridad de funciones entre **MT Manager** y **OpenManager**. Cada función cuenta con su estado actual (✅ Completo, 🟡 Parcial, ❌ Pendiente) y su evidencia técnica en el repositorio.

---

## 1. Núcleo del Explorador y Doble Panel

| Función | Estado | Evidencia / Ubicación | Notas |
| :--- | :---: | :--- | :--- |
| Doble panel independiente | ✅ | `FileListActivity.kt`, `layout/file_list_activity.xml` | Dos paneles sincronizados (`panelA` y `panelB`) con foco activo `#363636` e inactivo `#242424`. |
| Navegación y pila de retroceso | ✅ | `FileListActivity.kt#setupBackPressedHandler` | Soporta navegación atrás por panel, cierre de cajón y deselección. |
| Estadísticas de disco y rutas en barra | ✅ | `FileListActivity.kt#updateTopBarInfo` | Muestra ruta legible, conteo de ficheros y espacio libre de disco en tiempo real. |
| Salto rápido a ruta (Jump to path) | ✅ | `FileListActivity.kt#showJumpToPathDialog` | Diálogo con sugerencias rápidas (`/sdcard`, `Download`, `Android/data`) y validación. |
| Copiar y Mover entre paneles | ✅ | `FileListActivity.kt#btnCopyOpposite`, `FileListFragment.kt#copyFilesTo` | Operación en segundo plano usando `FileJobService` con refresco reactivo. |
| Crear carpeta / archivo | ✅ | `FileListActivity.kt#showCreateDialog`, `CreateDirectoryDialogFragment` | Creación atómica con soporte SAF y rutas Linux. |
| Eliminación simple y permanente | ✅ | `ConfirmDeleteFilesDialogFragment.kt`, `FileJobService` | Manejo de papelera y borrado forzado. |
| Búsqueda de archivos | ✅ | `SearchFileListLiveData.kt`, `FileListActivity.kt#showSearchDialog` | Búsqueda por patrón y extensión dentro del directorio y subdirectorios. |
| Enlaces simbólicos (Symlinks) | ✅ | `CreateSymlinkHelper.kt` | Creación de enlaces relativos y absolutos. |
| Dividir y Unir archivos (Split/Merge) | ✅ | `FileSplitMergeHelper.kt` | División configurable por tamaño (MB/GB) y unión secuencial `.001, .002...`. |
| Intercambiar nombres de archivos (Swap) | ✅ | `FileSwapNamesHelper.kt` | Intercambio atómico de nombres entre 2 archivos seleccionados. |
| Cifrado y Descifrado de archivos | ✅ | `FileEncryptDecryptHelper.kt` | Cifrado AES-256 con contraseña y extensión `.enc`. |
| Exportar lista de archivos a texto | ✅ | `ExportFileListHelper.kt` | Exportación con formatos: Solo nombres, Rutas absolutas, o Con tamaño y fecha. |
| Propiedades de archivos y permisos Linux | ✅ | `FilePropertiesDialogFragment.kt`, `fileproperties/` | Visor detallado de permisos octales (chmod), tamaño, hashes, propietario y fechas. |
| Hash / Checksums (MD5, SHA-1, SHA-256) | ✅ | `fileproperties/checksum/` | Cálculo asíncrono con barra de progreso y copia al portapapeles. |

---

## 2. Gestión de Archivos Comprimidos

| Función | Estado | Evidencia / Ubicación | Notas |
| :--- | :---: | :--- | :--- |
| Explorar archivos ZIP/APK sin descomprimir | ✅ | `provider/archive/`, `ArchiveFileSystem` | Navegación virtual dentro de archivos comprimidos como carpetas del sistema. |
| Crear archivos ZIP, 7Z, TAR | ✅ | `CreateArchiveDialogFragment.kt`, `provider/archive/` | Soporte multiformato con selección de nivel de compresión. |
| Descompresión de formatos TAR, GZ, BZ2, XZ | ✅ | `provider/archive/`, Apache Commons Compress | Soporte completo para empaquetados tarballs y variantes Unix. |
| Modificación en caliente dentro de ZIP/APK | 🟡 | `provider/archive/ArchiveWriter.kt` | Añadir y reemplazar ficheros en ZIP existe en el sistema de archivos de archivo; requiere verificación en APK re-sign. |

---

## 3. Editor de Texto

| Función | Estado | Evidencia / Ubicación | Notas |
| :--- | :---: | :--- | :--- |
| Editor de texto para ficheros grandes | ✅ | `viewer/text/TextEditorActivity.kt` | Soporte de carga asíncrona mediante flujos y paginación. |
| Resaltado de sintaxis multilinguaje | ✅ | `viewer/text/SyntaxLanguage.kt` | Soporte para Java, Kotlin, Smali, XML, JSON, C/C++, Shell, Markdown, etc. |
| Búsqueda y reemplazo con Regex | ✅ | `viewer/text/TextEditorActivity.kt` | Búsqueda hacia delante, atrás, mayúsculas/minúsculas y reemplazo masivo. |
| Numeración de líneas y ajuste de línea | ✅ | `viewer/text/TextEditorFragment.kt` | Opciones configurables en tiempo real. |
| Selector de codificación de caracteres | ✅ | `viewer/text/TextEditorActivity.kt` | UTF-8, GBK, ISO-8859-1, Windows-1252, etc. |

---

## 4. Editor Hexadecimal

| Función | Estado | Evidencia / Ubicación | Notas |
| :--- | :---: | :--- | :--- |
| Visualizador virtualizado fila por fila | ✅ | `viewer/hex/HexEditorActivity.kt`, `HexRowAdapter.kt` | Renderizado rápido a 16 bytes por línea con offset, hex y columna ASCII. |
| Inspector de datos en tiempo real | ✅ | `viewer/hex/HexEditorActivity.kt` | Decodificación instantánea: Int8, Int16 (LE/BE), Int32 (LE/BE), Float32, Binario. |
| Búsqueda Hexadecimal y de Texto | ✅ | `viewer/hex/HexBuffer.kt#searchHex`, `searchText` | Búsqueda hacia delante y atrás protegida contra desbordamientos. |
| Búfer de Deshacer / Rehacer | ✅ | `viewer/hex/HexBuffer.kt` | Pila de acciones con restauración de offset y marca de guardado. |
| Edición y reemplazo de bytes | ✅ | `viewer/hex/HexBuffer.kt#replace`, `replaceAllHex` | Inserción, reemplazo directo y truncado. |
| Cálculo de Checksums del búfer | ✅ | `viewer/hex/HexEditorActivity.kt#showChecksumsDialog` | MD5, SHA-1 y SHA-256 en hilo secundario. |
| Salto a offset (Go to offset) | ✅ | `viewer/hex/HexEditorActivity.kt#showGoToDialog` | Acepta valores decimales y hexadecimales (`0x...`). |

---

## 5. Herramientas de Ingeniería Inversa y APK

| Función | Estado | Evidencia / Ubicación | Notas |
| :--- | :---: | :--- | :--- |
| Firma de APK (V1, V2, V3) y Zipalign | ✅ | `filelist/ApkSignerHelper.kt` | Firma integrada con Android v1/v2/v3 y alineación a 4 bytes. |
| Visor de información detallada de APK | ✅ | `filelist/OpenApkDialogFragment.kt` | Package, versionCode, versionName, SDKs, permisos, UID y rutas. |
| Extracción y visor de icono de APK | ✅ | `filelist/OpenApkDialogFragment.kt#showAppIconViewer` | Permite visualizar y guardar el icono original en PNG. |
| Instalador integrado de APK y APKS divididos | ✅ | `filelist/ApkInstallHelper.kt` | Uso de `PackageInstaller.Session` con soporte para Split APKs y Root. |
| Editor DEX / DEX Plus (Smali) | ✅ | `viewer/dex/DexEditorActivity.kt`, `DexEngine.kt` | Desensamblado a Smali con Baksmali, navegación de clases/métodos/campos/strings, edición y reensamblado con Smali. |
| Decodificador de AndroidManifest binario (AXML) | ✅ | `file/AxmlDecoder.kt` | Descompilación de binario AXML a XML legible en TextEditor y visualizador. |
| Compilador de XML a binario AXML | ✅ | `file/AxmlEngine.kt` | Re-ensamblado directo de StringPool y cabeceras binarias AXML sin necesidad de recompilación pesada. |
| Visor / Editor de `resources.arsc` | ✅ | `viewer/arsc/ArscEditorActivity.kt`, `ArscEngine.kt` | Explorador y editor completo de tablas de recursos binarios de Android (cadenas, recursos y paquetes). |

---

## 6. Almacenamiento Remoto y Red

| Función | Estado | Evidencia / Ubicación | Notas |
| :--- | :---: | :--- | :--- |
| Cliente FTP / FTPS | ✅ | `provider/ftp/`, `commons-net` | Conexión, listado y transferencia remota. |
| Cliente SFTP (SSH) | ✅ | `provider/sftp/`, `jsch` | Navegación segura SSH y operaciones de archivos. |
| Cliente SMB (Samba / Windows) | ✅ | `provider/smb/`, `smbj` | Conexión a carpetas compartidas locales de Windows/Linux. |
| Cliente WebDAV | ✅ | `provider/webdav/`, `dav4jvm` | Acceso a nubes compatibles con WebDAV (Nextcloud, ownCloud). |
| Servidor FTP integrado | ✅ | `ftpserver/FtpServerService.kt` | Comparte el almacenamiento por Wi-Fi mediante servicio en primer plano y Quick Settings Tile. |

---

## 7. Visores de Medios y Bases de Datos

| Función | Estado | Evidencia / Ubicación | Notas |
| :--- | :---: | :--- | :--- |
| Visor de Imágenes con zoom y rotación | ✅ | `viewer/image/ImageViewerActivity.kt` | Implementado con SubsamplingScaleImageView y PhotoView. |
| Reproductor de Audio Flotante | ✅ | `viewer/audio/AudioPlayerDialogFragment.kt` | Barra de progreso, control de velocidad (0.75x a 2.0x), bucle y rebobinado. |
| Reproductor de Video | ✅ | `viewer/video/VideoPlayerActivity.kt` | Reproducción a pantalla completa con controles de navegación. |
| Visor de bases de datos SQLite | ✅ | `viewer/sqlite/SqliteViewerActivity.kt` | Exploración de tablas, visualización de filas y ejecución de sentencias SQL. |
| Visor de fuentes tipográficas (.ttf / .otf) | ✅ | `filelist/FontViewerDialogFragment.kt` | Previsualización interactiva de familias tipográficas con tamaño configurable. |
| Ejecutor de scripts Shell | ✅ | `filelist/ScriptRunnerHelper.kt` | Ejecución de scripts `.sh` en terminal con soporte Root. |

---

## 8. Gestor de Aplicaciones Instaladas (Estilo MT Manager)

| Función | Estado | Evidencia / Ubicación | Notas |
| :--- | :---: | :--- | :--- |
| Listado de apps de Usuario y Sistema | ✅ | `appmanager/InstalledAppsActivity.kt` | Pestañas independientes, carga rápida de iconos mediante Coil. |
| Extracción de APK estándar | ✅ | `appmanager/InstalledAppsActivity.kt#extractSingleApk` | Copia directa a `/sdcard/OpenManager/apks/`. |
| Extracción de Split APKs (APKS divididos) | ✅ | `appmanager/InstalledAppsActivity.kt#extractSplitApk` | Empaquetado en formato `.apks` (Zip con base.apk y splits). |
| Localizar APK y Datos en explorador | ✅ | `appmanager/InstalledAppsActivity.kt#showAppDetails` | Salto directo a la carpeta `/data/app` o `/Android/data/<pkg>`. |
| Control Root (Congelar / Inhabilitar / Forzar detención) | ✅ | `appmanager/InstalledAppsActivity.kt` | Comandos `pm enable`, `pm disable`, `am force-stop` vía libsu. |
| Huella digital SHA-256 de certificados | ✅ | `appmanager/InstalledAppsActivity.kt#getCertificateSha256` | Extracción de firmas V1-V3 y cálculo de huella. |

---

## 9. Sistema, Estabilidad y Resiliencia

| Función | Estado | Evidencia / Ubicación | Notas |
| :--- | :---: | :--- | :--- |
| Permisos Android 11+ (`MANAGE_EXTERNAL_STORAGE` / SAF) | ✅ | `FileListActivity.kt`, `ShowRequestStoragePermission...` | Secuencia limpia sin solapamientos ni diálogos redundantes. |
| Capturador Global de Excepciones (CrashHandler) | ✅ | `app/CrashHandler.kt` | Intercepta cualquier crash no controlado y guarda el log detallado en `/sdcard/OpenManager/crash_logs/`. |
| Flujo de CI ampliado con pruebas y signing | ✅ | `.github/workflows/android.yml` | Ejecución de pruebas unitarias automáticas, compilación debug y release con firma keystore segura. |
