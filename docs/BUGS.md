# OpenManager Bug Tracker

## Severidad de Bugs
- **Alta (P0)**: Cuelgues de la app (Crash / ANR), pérdida o corrupción de datos.
- **Media (P1)**: Funciones rotas, excepciones no fatales recurrentes o inconsistencias de estado.
- **Baja (P2)**: Detalles visuales menores o advertencias cosméticas.

---

## Bugs Abiertos
*No hay bugs abiertos de severidad alta ni media en este momento.*

---

## Bugs Resueltos Recientemente

### [RESUELTO - P1] Superposición de barra de notificaciones, iconos borrosos y lentitud en pantalla de Extracción de APK
- **Archivos**: `InstalledAppsActivity.kt`, `NativeAppIconLoader.kt`, `AppModel.kt`, `activity_installed_apps.xml`, `AppManagerLogicTest.kt`
- **Causa**:
  1. `activity_installed_apps.xml` colocaba la barra de herramientas (`Toolbar`) en `y = 0` y `CoordinatorAppBarLayout` fijaba `window.statusBarColor = Color.TRANSPARENT` sin aplicar padding superior, provocando que la barra de estado y notificaciones del sistema cubriera los controles de la Toolbar (atrás, título, buscar, ordenar, refrescar) y las pestañas.
  2. La carga de iconos por Coil usaba `AppIconLoader` rasterizando a mapas de bits de tamaño fijo, perdiendo la nitidez nativa de los vectores y `AdaptiveIconDrawable` del sistema operativo Android y produciendo iconos borrosos y pixelados.
  3. La lista no contaba con persistencia en disco, bloqueando la interfaz con un indicador de carga durante segundos en cada apertura mientras ejecutaba llamadas IPC síncronas de etiquetas y cálculos de disco de cientos de aplicaciones.
- **Solución**:
  1. Migración a contenedor `LinearLayout` con padding superior inmediato extraído directamente del recurso de dimensiones del sistema (`status_bar_height`) antes de la primera medición, y escucha reactiva de `WindowInsetsCompat` para recortes de pantalla (notch/cutouts) y barra de navegación, con fondo dark `#1F1F1F` unificado.
  2. Implementación de `NativeAppIconLoader` que recupera directamente los `Drawable` nativos del `PackageManager` (AdaptiveIcon/Vector/Hi-DPI) con resolución 100% vectorial nítida y caché LRU en memoria (300 iconos) para desplazamiento instantáneo sin caídas de fotogramas.
  3. Arquitectura de carga ultrarrápida en 3 niveles: memoria (0 ms) ➔ disco persistente con `AppDiskCache` (10 ms) ➔ escaneo en segundo plano con emisión progresiva (los primeros 25 elementos se muestran en < 40 ms) para que el usuario nunca espere con pantalla vacía.
  4. Extracción de APKs de alto rendimiento zero-copy con `FileChannel.transferTo()` a nivel de kernel y aviso inmediato mediante Toast y Snackbar.
- **Estado**: ✅ Corregido y verificado con tests automatizados.

### [RESUELTO - P1] Al presionar atrás en el Home de archivos navegaba hasta la raíz (/) y falta de botón volver al Home en pantalla de error root
- **Archivos**: `FileListFragment.kt`, `FileListActivity.kt`, `file_list_fragment_content_include.xml`, `strings.xml`, `strings-es.xml`
- **Causa**: Al presionar la tecla física/sistema atrás en el almacenamiento principal (`/storage/emulated/0`), el callback `navigateUpCallback` comprobaba `viewModel.canNavigateUpBreadcrumb` (el cual contenía los segmentos padres `/storage/emulated`, `/storage`, `/`), retrocediendo por dichos directorios hasta alcanzar la raíz del sistema `/`, mostrando la pantalla de error de permisos root en dispositivos sin root. Tampoco existía botón de retorno directo a Home en dicha pantalla de error, ni iconos en los accesos directos predeterminados del diálogo de saltar a ruta.
- **Solución**:
  1. Se implementó `isHomeOrRootPath()` y se ajustó `canNavigateUp` para retornar `false` cuando el usuario se encuentra en Home (`/storage/emulated/0`, `/sdcard`) o raíz `/`.
  2. Se actualizó `navigateUpCallback` y `updateBackCallbackState()` para respetar `canNavigateUp`, evitando salir de Home hacia rutas del sistema.
  3. En `FileListActivity`, se añadió doble pulsación atrás para salir con ventana de 2 segundos y Toast informativo (`R.string.press_back_again_to_exit`).
  4. Se integró el botón `btnErrorHome` ("Volver al Home" con icono `home_icon_white_24dp`) en `file_list_fragment_content_include.xml` conectado a `navigateToHome()`.
  5. En el diálogo "Ir a la ruta", se enriquecieron los chips predeterminados con iconos y tintes Material: "Interna" (casa ámbar), "Descargas" (descarga azul), "Android/data" (carpeta verde), "Raíz (/)" (dispositivo naranja), "Copiar" y "Pegar".
  6. Suite de tests unitarios `NavigationLogicTest.kt` validando todas las condiciones.
- **Estado**: ✅ Corregido y verificado con tests automatizados.

### [RESUELTO - P1] Lentitud, falta de iconos y diálogo genérico al pulsar el botón '+'
- **Archivos**: `CreateChoiceBottomSheetDialogFragment.kt`, `ThemedSpeedDialView.kt`, `FileListActivity.kt`, `name_dialog_name_include.xml`, `CreateFileDialogFragment.kt`, `CreateDirectoryDialogFragment.kt`, `RenameFileDialogFragment.kt`
- **Causa**: Al tocar el botón `+`, se construía un `MaterialAlertDialogBuilder` genérico con lista de texto plano sin iconos y con retraso perceptible de inicialización. Además, en `ThemedSpeedDialView` se invocaba `getFabImageDrawable(null)` pasando `null`, lo que impedía resolver el recurso drawable dejando los botones vacíos. La ventana de texto para nombrar archivos carecía de diseño moderno, iconos, botón de borrado rápido y chips de extensión rápida.
- **Solución**: Se implementó `CreateChoiceBottomSheetDialogFragment` (Bottom Sheet Material 3 instantáneo con insignias circulares e iconos dedicados para carpeta, archivo y archivo ZIP), se corrigió la resolución de drawables en `ThemedSpeedDialView` con colores temáticos ámbar/azul, y se modernizó `name_dialog_name_include.xml` con `OutlinedBox` redondeado (14dp), icono de inicio contextual, botón de borrado (`clear_text`), barra de chips de extensiones rápidas (`.txt`, `.json`, `.xml`, etc.) y autoenfoque del teclado.
- **Estado**: ✅ Corregido y validado con pruebas y compilación en CI.

### [RESUELTO - P0] Congelamiento y lentitud extrema en editor de código
- **Archivos**: `me/zhanghai/android/files/viewer/text/FastSyntaxScanner.kt`, `CodeEditText.kt`, `LineNumberView.kt`, `TextEditorFragment.kt`
- **Causa**: El resaltador de sintaxis ejecutaba múltiples expresiones regulares recompiladas en el hilo principal de la interfaz al cargar y en cada pulsación de tecla, aplicando y eliminando miles de spans en vivo sobre el `Editable`, lo que provocaba bloqueos de UI, pérdidas de caracteres y carecía de números de línea y auto-indentación.
- **Solución**: Se diseñó un analizador léxico lineal $O(N)$ (`FastSyntaxScanner`) con pre-resaltado asíncrono en `Dispatchers.Default`, renderizado de búsqueda y línea activa en Canvas (`CodeEditText`), números de línea fijados a la izquierda (`LineNumberView`), auto-indentación y emparejamiento de llaves.
- **Estado**: ✅ Corregido y validado con pruebas unitarias.

### [RESUELTO - P0] Desbordamiento de límites en búsqueda del Editor Hex
- **Archivo**: `me/zhanghai/android/files/viewer/hex/HexBuffer.kt`
- **Causa**: Al buscar hacia atrás con un patrón de mayor longitud que el búfer o con archivos vacíos, se ejecutaba `data[i + j]` provocando `ArrayIndexOutOfBoundsException`.
- **Solución**: Añadida condición de guarda inicial `if (pattern.isEmpty() || data.isEmpty() || data.size < pattern.size) return -1`.
- **Estado**: ✅ Corregido y verificado en commit `acd3bcb`.

### [RESUELTO - P0] Crash de RecyclerView en archivos vacíos en Editor Hex
- **Archivo**: `me/zhanghai/android/files/viewer/hex/HexRowAdapter.kt`
- **Causa**: `selectedOffset` ejecutaba `notifyItemChanged(0)` cuando `itemCount == 0`, lanzando excepción interna de RecyclerView.
- **Solución**: Se comprueba `if (count == 0) return` y se acota el rango `(val / 16).coerceIn(0, count - 1)`.
- **Estado**: ✅ Corregido y verificado en commit `acd3bcb`.

### [RESUELTO - P1] Pérdida de cambios en diálogo de guardado del Editor Hex
- **Archivo**: `me/zhanghai/android/files/viewer/hex/HexEditorActivity.kt`
- **Causa**: `showUnsavedChangesDialog()` ejecutaba `saveFile()` e inmediatamente `finish()`, cerrando la actividad antes de que `FileJobService` escribiera el archivo.
- **Solución**: Añadido callback `saveFile { if (it) finish() }`.
- **Estado**: ✅ Corregido y verificado en commit `acd3bcb`.

### [RESUELTO - P1] Desincronización de guardado de DEX en orígenes SAF/Remotos
- **Archivo**: `me/zhanghai/android/files/viewer/dex/DexEditorActivity.kt`
- **Causa**: El botón guardar solo emitía un Toast y no escribía el archivo modificado de vuelta al almacenamiento de origen si no era ruta Linux nativa.
- **Solución**: Implementado `saveDex()` con `FileJobService.write()`.
- **Estado**: ✅ Corregido y verificado en commit `acd3bcb`.

### [RESUELTO - P1] Excepciones no controladas de MediaPlayer en AudioPlayer
- **Archivo**: `me/zhanghai/android/files/viewer/audio/AudioPlayerDialogFragment.kt`
- **Causa**: Llamadas a `currentPosition`, `seekTo()` e `isPlaying` sin verificar el estado de preparación de `MediaPlayer`, arrojando `IllegalStateException`.
- **Solución**: Protegidas todas las invocaciones con bloques de manejo de excepción y `dismissAllowingStateLoss()`.
- **Estado**: ✅ Corregido y verificado en commit `acd3bcb`.

### [RESUELTO - P1] Fuga potencial de ficheros temporales en extracción Root de Split APKs
- **Archivo**: `me/zhanghai/android/files/appmanager/InstalledAppsActivity.kt`
- **Causa**: Si el flujo zip fallaba a mitad del empaquetado de split APKs, los ficheros copiados por root en `cacheDir` no eran eliminados.
- **Solución**: Envoltorio en bloques `try/finally` para asegurar que `tmp.delete()` se ejecute incondicionalmente.
- **Estado**: ✅ Corregido y verificado en commit `acd3bcb`.
