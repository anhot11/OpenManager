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
