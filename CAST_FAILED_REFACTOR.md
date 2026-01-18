# Informe de intentos fallidos de reproducción Cast (AirReceiver)

## Estado actual observado
- La conexión al dispositivo remoto se establece correctamente.
- El receptor muestra el texto de metadata (título y artista).
- La imagen del álbum no aparece.
- La barra de progreso avanza rápidamente a una velocidad constante (no realista).
- Luego se desconecta y aparece el toast: **"Failed to load media on cast device."**

## Objetivo de los cambios
Se intentó lograr reproducción remota estable en AirReceiver/Default Media Receiver, evitando errores **"Invalid Request"** y compatibles con sus restricciones. Los cambios se centraron en:
- Ajustar la carga de medios (MediaInfo/MediaLoadRequestData/queueLoad/legacy load).
- Mejorar el servidor HTTP local para servir archivos con headers correctos.
- Reducir la complejidad de la carga (metadata/cues/customData/seek/queue) cuando el receptor era estricto.

## Resumen de intentos y por qué no funcionaron

### 1) Debounce y control de transferencias Cast
**Qué se hizo:**
- Se añadió tracking de `lastCastTransferSessionId`, `lastCastTransferAttemptAt` e `inFlightCastTransferSessionId` para evitar reintentos rápidos o simultáneos.
- Se limpiaron flags al finalizar o fallar sesiones.

**Resultado:**  
Redujo reintentos duplicados, pero no resolvió el fallo de reproducción. El error de carga persistió.

---

### 2) Servidor HTTP local más estricto y compatible
**Qué se hizo:**
- Separación de `serveSong`/`serveArt` con soporte `GET/HEAD/OPTIONS`.
- CORS manual, headers `Content-Type`, `Content-Length`, `Content-Range`, `Accept-Ranges`.
- Soporte de `Range` con `206 Partial Content`.
- Se añadieron logs detallados (User-Agent, Accept, etc.).
- Se añadió `Content-Length` y `Content-Type` también en respuestas completas para evitar transferencias chunked.

**Resultado:**  
El receptor sí accede al servidor y pide `/art/...` pero la reproducción de audio sigue fallando. El error “Invalid Request” persiste aun con headers correctos. La imagen del álbum tampoco se muestra en el receptor.

---

### 3) Estrategias de carga Cast (queueLoad → load → legacy load)
**Qué se hizo:**
- Se implementaron varios intentos secuenciales: `queueLoad`, `MediaLoadRequestData`, y `legacy client.load(MediaInfo)`.
- Se añadieron delays configurables y polling de conexión.
- Se añadieron logs del resultado y `MediaStatus` tras cada intento.

**Resultado:**  
Los tres métodos fallan con `statusCode=2100` e “Invalid Request” en el MediaControlChannel. La falla persiste incluso cuando el servidor responde correctamente.

---

### 4) Ajustes específicos para AirReceiver / Default Media Receiver
**Qué se hizo:**
- Detección de AirReceiver por nombre/modelo/versión.
- Carga minimalista (cola de 1 ítem), evitando `queueLoad` y/o `MediaLoadRequestData`.
- Cambios de `MediaInfo`:  
  - `STREAM_TYPE_LIVE` sin duración (primer intento).  
  - Luego `STREAM_TYPE_BUFFERED` con duración para evitar “Invalid Request”.
- Se forzó `autoplay=true` en loads minimalistas.
- Se eliminó `seek` en cargas minimalistas.
- Se removió `customData` para loads minimalistas.
- Se removió el `album art` en loads minimalistas para reducir complejidad.
- Se agregó metadata básica (title/artist/album) para mostrar texto en el receptor.

**Resultado:**  
La metadata sí se visualiza, pero la reproducción sigue fallando. El receiver continúa devolviendo “Invalid Request” y se desconecta. La barra de progreso avanza rápido y luego se detiene.

---

### 5) Ajustes de MIME/type y extensión de URL
**Qué se hizo:**
- Se derivó la extensión y el MIME desde el `path` cuando `mimeType` faltaba o era inconsistente.
- Soporte para `flac`, `m4a`, `ogg`, `wav`, `aac`, `mp3`.

**Resultado:**  
No cambió el fallo de reproducción. El receptor sigue rechazando la carga aun con MIME/URL correctos.

---

## Conclusión
Todos los intentos realizados (cambios de `MediaInfo`, control de transferencias, estrategia de carga, servidor HTTP y headers) **no lograron que el audio reproduzca** en AirReceiver.  
El comportamiento estable es:
- La app se conecta y la metadata textual se muestra.
- La reproducción falla con **"Invalid Request"**.
- El receptor se desconecta y se muestra el toast de error.

Este informe resume lo hecho para evitar repetir los mismos enfoques.
