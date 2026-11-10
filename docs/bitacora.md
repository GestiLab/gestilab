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
