# Technical Decisions & Architecture Log

Este archivo documenta todas las decisiones arquitecturales, técnicas y de diseño tomadas de manera autónoma para garantizar que OpenManager sea 100% equivalente en funcionalidad a MT Manager manteniendo una base de código limpia, moderna y con licencia propia.

---

## 1. Crash Handler en nivel de aplicación
- **Decisión**: Implementar un `CrashHandler` propio que se registre en `App.kt` (`Thread.setDefaultUncaughtExceptionHandler`).
- **Motivo**: Garantizar que ante cualquier error inesperado en cualquier hilo de la app, el sistema capture la traza completa, versión de Android, arquitectura de CPU, estado de memoria y la almacene en `/sdcard/OpenManager/crash_logs/crash_<timestamp>.txt`. Si el almacenamiento externo no está disponible o no tiene permisos, se almacena en el directorio interno de archivos de la app (`filesDir/crash_logs/`).

## 2. Editor de Recursos ARSC (`resources.arsc`)
- **Decisión**: Desarrollar un analizador y editor de ARSC dedicado (`ArscEngine` y `ArscEditorActivity`) capaz de recorrer la cabecera `RES_TABLE_TYPE`, extraer la tabla de cadenas global (`StringPool`), permitir buscar y editar strings y reescribir la tabla de cadenas recalculando desplazamientos y tamaños de chunks de forma atómica.
- **Motivo**: MT Manager es reconocido por su editor ARSC que permite traducir o modificar textos en aplicaciones sin necesidad de deodexar o decompilar con apktool.

## 3. Editor de AXML Binario (`AndroidManifest.xml`)
- **Decisión**: Implementar un codificador/modificador AXML complementario a `AxmlDecoder`. Permitir edición de atributos principales (versionCode, versionName, package, debuggable, permissions) y guardado atómico del binario AXML.
- **Motivo**: Modificar un manifiesto de APK directamente en binario sin tener que reconstruir todo el APK con apktool ahorra tiempo y conserva la integridad de firmas o recursos.

## 4. Keystore Fija para Releases en CI
- **Decisión**: Generar un almacén de claves (keystore) estándar para OpenManager, almacenarla de forma segura como secreto de GitHub (`KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) usando la CLI `gh`, y configurar el workflow de GitHub Actions para que compile tanto el APK de depuración como el APK de lanzamiento firmado (`OpenManager-release.apk`).
- **Motivo**: Cumplir la regla de que las versiones sucesivas compartan la misma firma para que los usuarios puedan actualizar sin tener que desinstalar la app, sin filtrar secretos en el repositorio público.

## 5. Pruebas Unitarias Integradas
- **Decisión**: Añadir `junit:junit:4.13.2` y librerías de prueba en `app/build.gradle` para comprobar mecánicamente los componentes de parsing (`HexBufferTest`, `AxmlTest`, `ArscTest`) dentro del pipeline de CI con `./gradlew testDebugUnitTest`.
- **Motivo**: Prevenir regresiones de código y asegurar que cada modificación esté avalada por pruebas antes de los lanzamientos.
