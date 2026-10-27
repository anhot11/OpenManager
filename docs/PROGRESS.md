# OpenManager Development Progress

## Resumen del Proyecto
OpenManager es un administrador de archivos avanzado y suite de ingeniería inversa para Android con paridad funcional completa respecto a MT Manager.

---

## Estado Actual de las Fases

### Fase 0 – Auditoría y Mapeo
- [x] Análisis exhaustivo de los módulos existentes en el repositorio.
- [x] Creación de `docs/PARITY.md` con matriz completa de funciones de MT Manager.
- [x] Auditoría de fallos y diseño de paridad.

### Fase 1 – Implementación y Estabilidad (100% Completo)
1. **Crash Handler Global (`app/CrashHandler.kt`)**: ✅ Implementado y registrado en `AppInitializers.kt`. Captura información del sistema, memoria, hilos y traza en `/sdcard/OpenManager/crash_logs/`.
2. **Editor de Recursos ARSC (`resources.arsc`)**: ✅ `ArscEngine.kt` y `ArscEditorActivity.kt` implementados con navegación por pestañas (Cadenas, Recursos, Paquetes), edición interactiva, recálculo atómico de desplazamientos y tamaños de chunks y guardado persistente.
3. **Motor AXML (`file/AxmlEngine.kt`)**: ✅ Decodificación y re-ensamblado directo de cabeceras binarias y pool de cadenas para `AndroidManifest.xml`.
4. **Suite de Pruebas Unitarias**: ✅ Tests automatizados `HexBufferTest.kt` y `ArscEngineTest.kt` creados con JUnit 4.
5. **Configuración de CI y Firma con Keystore Segura**: ✅ Generada keystore permanente de 2048-bit RSA almacenada de forma segura en GitHub Actions Secrets (`RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`). Workflow `.github/workflows/android.yml` ampliado para compilar debug y release firmados y correr pruebas unitarias.
6. **Editor de Código de Alto Rendimiento (`FastSyntaxScanner.kt`, `CodeEditText.kt`, `LineNumberView.kt`)**: ✅ Arquitectura profesional con tokenizador lineal $O(N)$ en segundo plano (`Dispatchers.Default`), renderizado de búsqueda y línea activa directamente en Canvas sin sobrecarga de spans en `Editable`, números de línea laterales sincronizados (`LineNumberView`), auto-indentación inteligente en Enter, des-indentación en Backspace, emparejamiento de llaves/corchetes, búsqueda por regex y palabra completa, conversión de fin de línea (LF ⇄ CRLF), y suite de tests unitarios.
7. **Selector y Diálogo de Creación de Elementos (`CreateChoiceBottomSheetDialogFragment.kt`, `name_dialog_name_include.xml`)**: ✅ Selector instantáneo Material 3 Bottom Sheet con iconos enriquecidos para Nueva Carpeta (ámbar), Nuevo Archivo (azul) y Archivo ZIP (morado), corrección de iconos en `ThemedSpeedDialView`, caja de texto moderna redondeada (14dp) con startIcon, clear button y chips de extensiones rápidas (`.txt`, `.json`, `.xml`, `.java`, `.kt`, `.smali`, `.sh`, `.md`, etc.) y auto-foco del teclado.
8. **Navegación Estable, Botón Retorno a Home y Chips con Iconos (`FileListFragment.kt`, `FileListActivity.kt`)**: ✅ Corrección del retroceso desde Home para no escapar a la raíz `/`, doble toque atrás con Toast para salir, inclusión de botón "Volver al Home" (`home_icon_white_24dp`) en pantalla de error root / acceso denegado, e iconos Material 3 en chips de rutas predeterminadas (Interna con casa, Descargas con flecha, Android/data con carpeta, Raíz con dispositivo). Validado con suite `NavigationLogicTest.kt`.
9. **Rendimiento e Interfaz en Extracción de APK / Gestor de Aplicaciones (`InstalledAppsActivity.kt`, `InstalledAppIcon.kt`, `activity_installed_apps.xml`)**: ✅ Corrección completa de superposición con barra de notificaciones mediante `CoordinatorLayout` y `WindowInsetsCompat` reactivos; iconos de alta fidelidad HD sin pixelado mediante `InstalledAppIconFetcherFactory` aprovechando `ActivityManager.launcherLargeIconSize` y caché Coil persistente; carga paralela ultrarrápida en segundo plano con corrutinas y caché instantánea en memoria (0 ms); desplazamiento fluido con `ThemedFastScroller` e IDs estables; extracción de APKs de alto rendimiento con copia zero-copy vía `FileChannel.transferTo()` a nivel de kernel y compresión rápida para split APKs. Validado con `AppManagerLogicTest.kt`.
