# Bitácora de decisiones

Registro de las decisiones de diseño de GestiLab que tuvieron un costo: una
alternativa razonable se evaluó y se descartó. Cada decisión se amarra a un
requisito de calidad.

## Reglas

- Se registra solo lo que tuvo costo: una alternativa razonable fue descartada.
- Cada fila nombra al menos una alternativa descartada y la razón concreta de cada una.
- La última columna apunta a un requisito de calidad (RQ). Si no hay RQ, la entrada no va.
- La fecha es la de la decisión. Mientras el equipo no la ratifique en reunión, la fila lleva la marca **(propuesta)**.
- Las filas no se borran. Si una decisión cambia, se agrega una fila nueva y la anterior se anota como reemplazada.

## Registro

| Fecha | Decisión | Alternativas descartadas | Por qué se descartaron | Requisito |
|---|---|---|---|---|
| 2026-10-22 (propuesta) | **D-01** · La duración máxima y el requisito de autorización son columnas del tipo de equipo. `inventario-service` las devuelve junto con el estado de la unidad. | 1) Condicional por tipo en `prestamos-service`. 2) Archivo de configuración con reglas leído al arrancar. 3) Motor de reglas externo. | 1) Cada tipo nuevo exige modificar y desplegar préstamos. 2) Evita el cambio de código, pero exige reiniciar y deja las reglas lejos del dato. 3) Agrega un tercer proceso al sistema, contra la restricción de dos servicios. | RQ-01 Modificabilidad |
| 2026-11-07 (propuesta) | **D-10** · `prestamos-service` consulta a inventario a través de una interfaz del dominio (`IInventarioClient`); la implementación HTTP (`RestClient` con tiempo de espera de 3 s, URL por configuración) vive fuera del dominio. | 1) Usar `RestClient` directamente dentro del servicio de dominio. 2) Feign (`spring-cloud-openfeign`) o `WebClient`. | 1) Las reglas de préstamo quedarían atadas a HTTP y solo se probarían con inventario o un servidor simulado levantado. 2) Agregan una dependencia (Spring Cloud o WebFlux) para una sola llamada; `RestClient` ya viene en `spring-web`. | RQ-02 Testabilidad |
| 2026-11-09 (propuesta) | **D-11** · Cuando `inventario-service` responde con un 4xx distinto de 404 (400, 401, 403...), `prestamos-service` responde 502 con un mensaje fijo y sin reenviar el cuerpo recibido. El 503 queda solo para caída, tiempo agotado, 5xx o respuesta que no cumple el contrato. | 1) Tratar todo 4xx inesperado como 503, como en la primera versión del cliente. 2) Responder 500 genérico. 3) Reenviar al cliente el 401 o 403 que dio inventario. | 1) Presenta como caída un fallo de credenciales o de contrato que reintentar no arregla, y quien opera busca un servicio caído que está sano. 2) El 500 mezcla este fallo con los errores del propio código de `prestamos-service` y no permite distinguirlo. 3) La persona sí está autenticada ante `prestamos-service`: un 401 o 403 le haría creer que no tiene permiso y filtraría detalles de otro servicio. | RQ-03 Seguridad |
| 2026-11-13 (propuesta) | **D-12** · El realm de Keycloak tiene dos clientes: `gestilab-web` (Angular) solo con código de autorización y PKCE, y `gestilab-verificacion`, de uso exclusivo en desarrollo, que permite pedir tokens por contraseña para el guion `keycloak/verificar_tokens.py`. | 1) Habilitar el flujo de contraseña directo en `gestilab-web` para que el guion lo use. 2) Verificar los roles solo iniciando sesión a mano desde un navegador. | 1) El cliente real del sistema aceptaría credenciales enviadas directamente, saltándose PKCE y la pantalla de inicio de sesión de Keycloak. 2) La verificación de los roles de cada token no se podría repetir ni automatizar, y el escenario de seguridad pide pruebas con el token de cada rol. | RQ-03 Seguridad |
| 2026-11-14 (propuesta) | **D-13** · Al entregar o recibir, `prestamos-service` guarda el cambio de estado y el evento `UnidadCambioDeEstado` en la misma transacción (buzón de eventos pendientes) y un proceso aparte lo publica en RabbitMQ con confirmación y reintentos, en orden; nunca escribe en la base de inventario. | 1) Publicar a RabbitMQ dentro de la transacción y responder 503 con reversa si el broker falla. 2) Publicar después de confirmar la transacción, sin guardar el evento. 3) Que `prestamos-service` escriba el estado de la unidad directamente en la base de inventario. | 1) Una caída del broker bloquearía entregas y devoluciones que son válidas, y aun así el mensaje podría perderse si el proceso muere entre publicar y confirmar. 2) Si el proceso cae o el broker no responde justo después de confirmar, el cambio queda guardado y el evento se pierde sin rastro. 3) Rompe la regla de que cada servicio es dueño de su base y que inventario cambia el estado solo al consumir el evento (SCRUM-21, CA-7). | Sin requisito de calidad propio: decisión de fiabilidad del evento (SCRUM-19) |
| 2026-11-17 (propuesta) | **D-14** · El backend se despliega en un clúster Kubernetes local con kind; los manifiestos viven en `k8s/` con kustomize y un guion (`k8s/desplegar.sh`) crea el clúster, carga las imágenes y aplica todo. | 1) minikube. 2) El Kubernetes integrado de Docker Desktop. 3) Un clúster gestionado en la nube (GKE, EKS o AKS). 4) Charts de Helm. | 1) Necesita un controlador de máquina virtual o de contenedores adicional y pesa más en los equipos del grupo. 2) Se activa a mano en la interfaz de cada persona y no se puede crear desde un guion. 3) Exige cuentas, tarjeta y costo, y no se puede probar sin conexión; queda como opción si la entrega lo pide. 4) Una herramienta más para siete piezas que kustomize ya resuelve. | RQ-02: el despliegue se crea con un comando y se comprueba con `k8s/comprobar.sh`, que falla en el paso roto y se puede repetir en cualquier máquina. |
| 2026-11-17 (propuesta) | **D-15** · Las fotos se guardan en SeaweedFS 4.05 a través de su pasarela compatible con S3; `prestamos-service` usa el mismo adaptador S3 y solo cambian el endpoint y las credenciales. | 1) MinIO oficial. 2) MinIO de `bitnamilegacy`. 3) Adobe S3Mock. 4) SeaweedFS 3.97. | 1) Dejó de publicar imágenes: `minio/minio` y `quay.io/minio/minio` ya no existen (comprobado el 17 de noviembre de 2026). 2) Imagen congelada, sin actualizaciones. 3) Es una herramienta de pruebas, no un almacenamiento. 4) Guardaba la envoltura `aws-chunked` que envía el SDK de AWS y la foto recuperada no era la imagen subida. | RQ-01: cambiar de almacenamiento compatible con S3 no toca el código de `prestamos-service`, solo su configuración. |
### D-01 explicada

**El problema.** Cada tipo de equipo tiene sus reglas: un multímetro se presta 7 días y otro equipo puede exigir autorización. Si esas reglas estuvieran en `prestamos-service`, cada tipo nuevo obligaría a cambiar código y a desplegar ese servicio otra vez.

**La decisión.** Las reglas se guardan como columnas del tipo de equipo en `inventario-service`. Al pedir una unidad, `prestamos-service` las recibe en la respuesta y las aplica, sin saber qué tipos existen.

**Ejemplo.** Para agregar un tipo con 3 días y autorización obligatoria, se crea un registro en inventario. `prestamos-service` no cambia.

**Por qué importa.** RQ-01 mide esto: agregar un tipo debe dejar 0 archivos modificados en `prestamos-service`.

Las demás decisiones (D-02 en adelante) las agrega quien las toma, con la misma plantilla.

## Plantilla de entrada

| Fecha | Decisión | Alternativas descartadas | Por qué se descartaron | Requisito |
|---|---|---|---|---|
| AAAA-MM-DD | D-nn · Qué se decidió, en una frase con sujeto y verbo. | Cada alternativa razonable que se descartó, numerada. | Una razón concreta por alternativa, ligada al costo en el sistema. | RQ-nn y nombre del atributo |
