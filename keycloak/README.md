# Keycloak de GestiLab

Identidad delegada: Keycloak emite el token; los servicios solo lo validan y leen los roles.

## Contenido

| Archivo | Para qué sirve |
| --- | --- |
| `gestilab-realm.json` | Realm `gestilab`: roles `usuario` y `auxiliar`, clientes y usuarios de prueba |
| `compose.yml` y `.env.example` | Levantan Keycloak e importan el realm |
| `verificar_tokens.py` | Pide un token a cada usuario de prueba, imprime sus roles o exporta `TOKEN_USUARIO` y `TOKEN_AUXILIAR` |

## Levantar desde cero

Esta carpeta sigue el mismo patrón que los servicios: su propio `compose.yml` y su propio `.env.example`. Desde la raíz del repo:

```bash
docker compose -f keycloak/compose.yml up -d
python3 keycloak/verificar_tokens.py
```

Debe imprimir `-> OK` para los dos usuarios (salida 0). Keycloak queda en `http://localhost:8180` (8080/8081 son de inventario y 8090/8091 de préstamos). Las credenciales del administrador salen de `KEYCLOAK_ADMIN_USER` y `KEYCLOAK_ADMIN_PASSWORD` (ver `.env.example`; el `.env` real no se sube y tiene valores por defecto de ejemplo).

`--import-realm` solo importa si el realm no existe. Para reimportar desde cero: `docker compose -f keycloak/compose.yml down` y volver a levantar.

## Tokens para las mediciones (RQ-01 y RQ-03)

`medir.sh` espera las variables `TOKEN_AUXILIAR` y `TOKEN_USUARIO`. Con Keycloak levantado, desde bash (en Windows, Git Bash):

```bash
eval "$(python3 keycloak/verificar_tokens.py --exportar)"
```

Los tokens vencen a los 5 minutos (valor por defecto de Keycloak): pídelos justo antes de ejecutar `medir.sh`, o repite el comando si pasó más tiempo.

## Clientes

| Cliente | Uso | Flujo |
| --- | --- | --- |
| `gestilab-web` | Angular | Público, Authorization Code + **PKCE (S256)**, redirección `http://localhost:4200/*` |
| `gestilab-verificacion` | Solo el guion de verificación | Password grant, únicamente en desarrollo |

## Usuarios de prueba (contraseñas de EJEMPLO, no son secretos reales)

| Usuario | Contraseña de ejemplo | Rol |
| --- | --- | --- |
| `usuario.prueba` | `Usuario123!` | `usuario` |
| `auxiliar.prueba` | `Auxiliar123!` | `auxiliar` |

## Dónde viajan los roles en el token

Los roles de realm viajan en el access token en **`realm_access.roles`**. Los dos servicios deben leer esa misma ruta.

```json
{
  "iss": "http://localhost:8180/realms/gestilab",
  "preferred_username": "auxiliar.prueba",
  "realm_access": {
    "roles": ["auxiliar"]
  }
}
```

No se usa `resource_access`: los roles son de realm, no de cliente. El realm no define rol por defecto, así que el token trae únicamente el rol asignado al usuario (`usuario` o `auxiliar`).Se verificó con Keycloak 26.0.8.
