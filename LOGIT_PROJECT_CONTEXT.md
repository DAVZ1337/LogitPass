# LOGIT ECOSYSTEM CONTEXT: LogitNotes 🌿 & LogitPass 🔐

Este documento consolida el análisis técnico, arquitectónico y de diseño verificado del repositorio público de **LogitNotes** (https://github.com/DAVZ1337/LogitNotes) y establece las directrices para conectar y evolucionar **LogitPass** dentro del mismo ecosistema de aplicaciones privadas, locales y seguras.

---

## 1. Visión y Filosofía del Ecosistema "Logit"

Ambos proyectos comparten una propuesta de valor común centrada en el usuario consciente de la privacidad y la soberanía de sus datos:
1. **100% Offline-First / Zero-Cloud**: Ningún dato sensible sale del dispositivo hacia servidores externos. No hay Firebase, no hay telemetría ni SDKs de rastreo propietarios.
2. **Cifrado Fuerte de Extremo a Extremo en Reposo**: Cifrado autenticado AES-256-GCM con derivación de clave maestra PBKDF2 (100.000 iteraciones).
3. **Autenticación Biométrica Nativa**: Acceso rápido mediante `BiometricPrompt` (huella / rostro) con derivación o envoltura segura de clave.
4. **Diseño Eco-Minimalista y Editorial Cálido**: Interfaz limpia, ágil, tipografía legible y consumo mínimo de recursos (RAM y batería).
5. **Preparación para F-Droid y FLOSS**: Licenciamiento compatible con código abierto (Apache-2.0 / MIT), metadatos Fastlane (`metadata/android`) y compilación estándar sin blobs propietarios.

---

## 2. Análisis Técnico Detallado de LogitNotes

### A. Repositorio e Información General
* **URL:** `https://github.com/DAVZ1337/LogitNotes`
* **Autor:** DAVZ1337
* **Lenguaje:** Kotlin (100%)
* **Framework UI:** Jetpack Compose con Material Design 3
* **Package Name:** `com.aistudio.vaultnotes.zqmvx` (namespace `com.example`)
* **Versión:** 1.1.0 (versionCode 2)

### B. Arquitectura de Código
* **Patrón:** MVVM (Model-View-ViewModel) reactivo con Kotlin Coroutines y Flow (`StateFlow`, `combine`, `flowOn(Dispatchers.IO)`).
* **Estructura de Directorios:**
  ```text
  app/src/main/java/com/example/
  ├── MainActivity.kt               # FragmentActivity con lifecycle y BiometricPrompt host
  ├── data/
  │   ├── local/
  │   │   ├── Entities.kt           # NoteEntity, AttachmentEntity con índices
  │   │   ├── VaultDao.kt           # Consultas Room reactivas (Flow)
  │   │   └── VaultDatabase.kt      # RoomDatabase local
  │   ├── VaultFileManager.kt      # Manejo SAF (Storage Access Framework), ZIP y Markdown
  │   └── VaultRepository.kt       # Capa de cifrado/descifrado transparente
  ├── model/
  │   └── Models.kt                # Modelos de dominio desacoplados (Note, Attachment, VaultStats)
  ├── security/
  │   ├── BiometricHelper.kt       # Wrapper sobre androidx.biometric.BiometricPrompt
  │   └── CryptoManager.kt         # Motor criptográfico AES-256-GCM + PBKDF2 + Canary
  └── ui/
      ├── VaultViewModel.kt        # Orquestador de estados (bóveda, bloqueo, notas, búsqueda)
      ├── components/
      │   └── MarkdownViewer.kt    # Renderizado y checklists interactivos
      ├── screens/
      │   ├── LockScreen.kt        # Onboarding de clave maestra, desbloqueo y biometría
      │   ├── VaultListScreen.kt   # Lista, vista grid/lista, tags, carpetas y búsqueda
      │   ├── NoteDetailScreen.kt  # Edición, previsualización, adjuntos y exportación
      │   └── SettingsScreen.kt    # Seguridad, cambio de contraseña, dark mode, exportar ZIP
      └── theme/
          ├── Color.kt             # Paleta Warm Ivory, acentos Ámbar y Esmeralda
          ├── Theme.kt             # Material 3 light/dark custom theme
          └── Type.kt              # Tipografía con estilo editorial
  ```

### C. Mecanismo Criptográfico (`CryptoManager`)
* **Algoritmo:** `AES/GCM/NoPadding` (clave de 256 bits, etiqueta de autenticación de 128 bits, IV aleatorio de 12 bytes por operación).
* **Derivación de Clave:** `PBKDF2WithHmacSHA256`, 100.000 iteraciones, salt criptográfico de 16 bytes (`SecureRandom`).
* **Verificación de Clave (Canary):** Emplea un string de control `"LOGIT_VAULT_CANARY_V1"` cifrado. Al desbloquear con la contraseña ingresada, se intenta descifrar el canary; si coincide exactamente, la clave se valida y se almacena en memoria volátil (`activeSessionKey`).
* **Ciclo de Vida de la Clave:** Cuando la bóveda se bloquea, la clave activa en memoria se elimina de inmediato (`activeSessionKey = null`).
* **Integración Biométrica:** Envuelve la clave de sesión utilizando entropía protegida a nivel de dispositivo para permitir desbloqueo biométrico sin solicitar la contraseña maestra en cada apertura.

### D. Identidad Visual y Diseño
* **Paleta Cálida ("Warm Ivory"):**
  * Fondo lienzo: `#F7F5F0` (Luz) / `#161514` (Oscuro).
  * Tarjetas: `#FFFFFF` con borde sutil `#E5E0D8` (Luz) / `#22201E` con borde `#33302C` (Oscuro).
  * Acentos de seguridad: `EmeraldSecurity` (`#059669` / `#10B981`) para validar estados seguros y cifrado activo.
  * Acentos organizativos: `FolderAmber` (`#F59E0B`) para carpetas y etiquetas.
  * Tipografía y espaciado: M3 con bordes redondeados (16–20dp), badges compactos, transiciones suaves (`AnimatedVisibility`, `Crossfade`).

---

## 3. Estado Actual de LogitPass

* El proyecto en el espacio de trabajo actual cuenta con la plantilla base limpia de Android con Jetpack Compose y dependencias listas en `build.gradle.kts`.
* **Objetivo de LogitPass**: Ser el gestor de contraseñas y credenciales de la familia Logit, complementando a LogitNotes pero enfocado específicamente en la seguridad de accesos digitales (cuentas, contraseñas, tarjetas bancarias, tokens 2FA/TOTP e identidades).

---

## 4. Qué Ideas de LogitNotes se Deben Reutilizar en LogitPass

| Componente | Qué se reutiliza de LogitNotes | Cómo se adapta para LogitPass sin romper ni clonar |
| :--- | :--- | :--- |
| **Criptografía (`CryptoManager`)** | Motor AES-256-GCM + PBKDF2 (100k iteraciones) + verificación Canary. | Se mantiene idéntico en robustez, pero se añade un generador seguro de contraseñas (`PasswordGenerator`) con entropía calculada y soporte para caracteres especiales configurables. |
| **Biometría (`BiometricHelper`)** | Flujo `BiometricPrompt` nativo y envoltura segura de clave. | Se reutiliza directamente para desbloquear la bóveda al abrir la app o antes de revelar/copiar un secreto crítico. |
| **Identidad Visual & Theming** | Paleta cálida, tarjetas limpias, tipografía y soporte claro/oscuro. | Se mantiene la coherencia visual "Logit" (fondos cálidos, tarjetas con bordes elegantes), usando acentos azul índigo/esmeralda para reflejar "Cuentas y Claves" con alta seguridad. |
| **Arquitectura de Datos (Room + Flow)** | Arquitectura de entidades cifradas en reposo (`encryptedPayload`). | Se crean entidades específicas para credenciales: `PasswordEntity` (título, username, ciphertext cifrado, url, categoría, fortaleza, fecha de caducidad, notas) y `CategoryEntity`. |
| **Filosofía F-Droid / FLOSS** | Estructura sin servicios propietarios ni analytics. | Se mantiene 100% local, sin permisos de red innecesarios en el manifiesto (`android.permission.INTERNET` no requerido para almacenar credenciales). |
| **Exportación / Respaldo Seguro** | Mecanismo de exportación de `VaultFileManager`. | En LogitPass se implementa exportación a archivo JSON cifrado (protegido por contraseña) y opción de CSV en claro con advertencia de seguridad explícita. |

---

## 5. Funcionalidades Exclusivas que Necesita LogitPass

A diferencia de LogitNotes (que es un bloc de notas y adjuntos en Markdown), LogitPass requiere:
1. **Generador Avanzado de Contraseñas:**
   - Sliders de longitud (8 a 64 caracteres).
   - Toggles: Mayúsculas, Minúsculas, Números, Símbolos (`!@#$%^&*`).
   - Modo Passphrase (palabras legibles aleatorias).
   - Medidor de entropía y fortaleza (Débil, Media, Fuerte, Militar).
2. **Campos Especializados de Credenciales:**
   - Nombre de cuenta / servicio (ej. "Google", "GitHub", "Banco").
   - Usuario / Email / Nombre de usuario.
   - Contraseña oculta por defecto (`••••••••`), con botón de revelar y botón de copiar en 1 toque.
   - URL del sitio web o aplicación vinculada.
   - Categorías: Cuentas Web, Redes Sociales, Trabajo, Tarjetas/Finanzas, Notas Seguras.
   - Clave secreta TOTP / 2FA con generación de código de 6 dígitos cada 30 segundos (opcional).
3. **Seguridad Operativa en Android:**
   - **FLAG_SECURE**: Protección contra capturas de pantalla o previsualizaciones en la lista de aplicaciones recientes (`WindowManager.LayoutParams.FLAG_SECURE`).
   - **Auto-Lock por Inactividad**: Temporizador para bloquear la bóveda si la app queda en segundo plano.
   - **Limpieza de Portapapeles**: Opción de borrado automático de la contraseña copiada tras 30 segundos para prevenir robo por otras apps.
4. **Auditoría de Salud de Contraseñas:**
   - Diagnóstico automático de contraseñas reutilizadas, débiles o antiguas (>90 días).

---

## 6. Plan Breve de Implementación (Pendiente de Aprobación del Usuario)

* **Fase 1: Capa de Seguridad y Persistencia**
  * Adaptar `CryptoManager` e integrar `PasswordGenerator`.
  * Diseñar `VaultDatabase`, `PasswordEntity` y `PasswordDao` con cifrado AES-256-GCM.
  * Añadir `BiometricHelper` y `libs.androidx.biometric`.
* **Fase 2: Identidad Visual y UI Theme**
  * Adaptar `Color.kt`, `Theme.kt` y `Type.kt` preservando la identidad del ecosistema Logit.
  * Configurar `FLAG_SECURE` en `MainActivity.kt`.
* **Fase 3: Pantallas Principales de LogitPass**
  * `LockScreen`: Desbloqueo por contraseña maestra o biometría, creación inicial de bóveda.
  * `VaultListScreen`: Buscador rápido, filtrado por categorías/favoritos, medidor de seguridad global y FAB para añadir credenciales.
  * `CredentialDetailScreen`: Vista y edición de credenciales con visibilidad enmascarada, generador de contraseñas flotante y copia rápida.
  * `PasswordGeneratorDialog`: Herramienta rápida e interactiva para generar claves robustas.
  * `SettingsScreen`: Auditor de seguridad, auto-bloqueo, cambio de clave maestra y exportación/importación cifrada.
* **Fase 4: Verificación y Compilación**
  * Validación con `compile_applet`.

---
*Fin del documento de contexto. Ningún cambio de código en archivos existentes se ejecutará sin la previa confirmación del usuario.*
