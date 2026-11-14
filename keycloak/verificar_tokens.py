#!/usr/bin/env python3
"""Pide un token a cada usuario de prueba de Keycloak.

Uso:
  python3 keycloak/verificar_tokens.py              imprime los roles que trae cada token
  python3 keycloak/verificar_tokens.py --exportar   imprime export TOKEN_USUARIO=... y export TOKEN_AUXILIAR=...
                                                    (uso: eval "$(python3 keycloak/verificar_tokens.py --exportar)")

Hasta el momento,solo se usó la biblioteca estándar. Sale con código 1 si algún token no trae lo esperado
y con código 2 si no puede conectar con Keycloak (apagado o todavía arrancando).
"""
import base64
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://localhost:8180"
REALM = "gestilab"
CLIENT = "gestilab-verificacion"
URL = f"{BASE}/realms/{REALM}/protocol/openid-connect/token"

# usuario: (contraseña de EJEMPLO, rol esperado, rol que NO debe traer, variable de entorno)
USUARIOS = {
    "usuario.prueba": ("Usuario123!", "usuario", "auxiliar", "TOKEN_USUARIO"),
    "auxiliar.prueba": ("Auxiliar123!", "auxiliar", "usuario", "TOKEN_AUXILIAR"),
}


def pedir_token(usuario, clave):
    cuerpo = urllib.parse.urlencode({
        "grant_type": "password",
        "client_id": CLIENT,
        "username": usuario,
        "password": clave,
    }).encode()
    with urllib.request.urlopen(urllib.request.Request(URL, data=cuerpo)) as r:
        return json.load(r)["access_token"]


def contenido(jwt):
    parte = jwt.split(".")[1]
    parte += "=" * (-len(parte) % 4)
    return json.loads(base64.urlsafe_b64decode(parte))


def main():
    exportar = "--exportar" in sys.argv[1:]
    fallos = 0
    for usuario, (clave, debe, no_debe, variable) in USUARIOS.items():
        try:
            token = pedir_token(usuario, clave)
        except urllib.error.HTTPError as e:
            detalle = e.read().decode(errors="replace")[:200]
            print(f"Keycloak rechazó el token de {usuario}: HTTP {e.code} {detalle}", file=sys.stderr)
            return 1
        except OSError as e:
            print(f"No pude pedir el token de {usuario} a {BASE}: {e}\n"
                  "¿Keycloak ya terminó de arrancar? Revisa: docker compose -f keycloak/compose.yml logs",
                  file=sys.stderr)
            return 2
        roles = contenido(token).get("realm_access", {}).get("roles", [])
        ok = debe in roles and no_debe not in roles
        if exportar:
            print(f"export {variable}={token}")
            if not ok:
                print(f"{usuario}: roles inesperados {roles}", file=sys.stderr)
        else:
            print(f"{usuario}: realm_access.roles = {roles} -> {'OK' if ok else 'FALLO'}")
        fallos += 0 if ok else 1
    return 1 if fallos else 0


if __name__ == "__main__":
    sys.exit(main())