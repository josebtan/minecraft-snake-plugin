# SnakePlugin

Plugin de Minecraft (API **Paper**, Java 17) que recrea el clasico juego de **Snake**
dentro del servidor, pensado para multijugador: cada jugador controla su propia
serpiente, representada con **bloques de lana de colores** para poder distinguir
facilmente "cual serpiente es de quien".

No hace falta saber nada de desarrollo de plugins para usar este repositorio: el
proyecto se ira construyendo etapa por etapa, cada una en su propio commit /
pull request, con explicaciones incluidas en el codigo (comentarios en español).

## ¿Como se juega? (idea general)

- Cada jugador tiene una serpiente cuya **cabeza** es un bloque de lana **real y
  visible** de un color distinto (rojo, azul, verde, etc.), que se mueve por el
  mundo casilla a casilla.
- El jugador aparece **sentado justo encima** de ese bloque (montado en un
  asiento invisible pegado a el), y viaja automaticamente con la cabeza en cada
  movimiento — como si estuviera sentado en un carrito de minas siguiendo la
  via.
- La camara del jugador es **libre**: puede mirar a su alrededor con
  normalidad mientras viaja, no hay ningun bloqueo de vista.
- El jugador la dirige con las **teclas de movimiento (WASD)**. Estas se leen
  con la ayuda del plugin **[ProtocolLib](https://github.com/dmulloy2/ProtocolLib)**
  (ver la seccion de dependencias mas abajo): es necesario porque, estando
  sentado en una entidad invisible normal, Minecraft no ofrece ninguna forma
  nativa de saber que tecla pulsa el jugador.
- Al pasar sobre un bloque de comida, la serpiente **crece**: se anade un nuevo
  bloque a la cola, que sigue el recorrido exacto que hizo la cabeza (igual que en
  el Snake clasico).
- Chocar contra la cola propia, la de otro jugador, o contra el borde del campo de
  juego, termina la partida de ese jugador.

## Etapas de desarrollo

El desarrollo esta dividido en 4 etapas, tal y como se planifico:

- [x] **Etapa 1 — Movimiento del jugador y la cabeza de la serpiente.**
  Estructura base del proyecto (Maven + Paper API). La cabeza es un bloque de
  lana real que se mueve por el mundo; el jugador aparece sentado justo
  encima (asiento invisible), con la camara libre, controlando el movimiento
  con las teclas WASD (leidas via ProtocolLib).
- [x] **Etapa 2 — Comandos y creacion del campo de juego.**
  Comando definitivo `/snake ...` (reemplaza al `/snakedebug` temporal de la
  Etapa 1). Las arenas (campo de juego) se delimitan marcando dos esquinas al
  estilo WorldEdit; al crearlas se levantan paredes solidas alrededor. La
  deteccion de choques ya funciona: antes de mover la cabeza se revisa si el
  bloque de destino es aire; si no lo es (pared de la arena, o mas adelante
  la cola propia/ajena), la partida termina ahi mismo.
- [ ] **Etapa 3 — Aparicion de comida, puntos y mecanicas del juego.**
  Generacion aleatoria de comida dentro del campo y sistema de puntuacion.
  La deteccion de choques de la Etapa 2 habra que ampliarla: en vez de tratar
  cualquier bloque no-aire como choque, primero revisar si es comida/power-up.
- [ ] **Etapa 4 — Mecanica de movimiento y crecimiento de la cola.**
  La cola sigue exactamente el recorrido de la cabeza y crece al comer,
  incluyendo la deteccion de colision contra la propia cola / la de otros
  jugadores (usando el mismo chequeo de "bloque no-aire" de la Etapa 2).

## Requisitos para compilar

- Java 17+
- Maven 3.8+
- Un servidor [Paper](https://papermc.io/) 1.20.x para probar el plugin

## Dependencias en el servidor

Ademas de `SnakePlugin`, tu servidor necesita tener instalado
**[ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/)** en su
carpeta `plugins/` (es una dependencia obligatoria — SnakePlugin no arrancara
sin ella, ver `depend: [ProtocolLib]` en `plugin.yml`).

⚠️ Si tu servidor usa una version de Paper muy reciente, puede que necesites
una **build de desarrollo** de ProtocolLib en vez de la version estable
publicada en Spigot — la propia pagina de ProtocolLib indica para que rango de
versiones hace falta la dev build. Revisa las builds mas recientes en su
[Jenkins](https://ci.dmulloy2.net/job/ProtocolLib/) o su hilo de Spigot.

## Como compilar

```bash
mvn clean package
```

El `.jar` resultante aparece en `target/` y se copia a la carpeta `plugins/` de tu
servidor Paper. El repositorio tambien incluye un workflow de GitHub Actions
(`.github/workflows/build.yml`) que compila el proyecto automaticamente en cada
push, para que puedas descargar el `.jar` sin necesidad de tener Maven instalado
localmente (pestaña **Actions** del repositorio → build → Artifacts).

## Probar la Etapa 2

1. Compila el plugin y colocalo en `plugins/` de un servidor Paper, junto con
   **ProtocolLib** (ver seccion de dependencias mas arriba — imprescindible).
2. Inicia el servidor y entra con un jugador que tenga permiso de operador
   (hace falta para crear arenas — permiso `snakeplugin.arena.admin`, por
   defecto solo `op`).
3. Parate en una esquina de la zona donde quieras el campo de juego y ejecuta
   `/snake arena pos1`. Ve hasta la esquina opuesta y ejecuta `/snake arena pos2`.
4. Ejecuta `/snake arena create <nombre>` (por ejemplo `/snake arena create
   arena1`): se limpia el interior y se levantan paredes solidas alrededor
   del rectangulo marcado.
5. Cualquier jugador ejecuta `/snake join <nombre>` para aparecer sentado
   sobre la cabeza de su serpiente, en el centro de esa arena. Camara libre,
   se controla con **W / A / S / D**.
6. Si la cabeza choca contra una pared (o, en etapas futuras, contra una
   cola), la partida termina automaticamente y se avisa al jugador.
7. `/snake leave` para levantarse y detener la partida manualmente.
8. `/snake arena list` lista las arenas ya creadas.

> Nota: las arenas se guardan solo en memoria por ahora — se pierden al
> reiniciar el servidor y hay que volver a crearlas. Persistirlas en disco
> queda pendiente para una etapa futura.
>
> Historial de decisiones de diseño para el movimiento/monta (Etapa 1):
> 1. Se probo mover al jugador solo mirando alrededor (sin WASD, sin sentarse).
> 2. Se probo tele-transportar al jugador cada tick para simular que "viajaba"
>    con la cabeza — funcionaba, pero era pesado para el servidor y peleaba
>    contra los intentos de movimiento del propio jugador.
> 3. Se probo montarlo en un cerdo con silla (Steerable) — pero un cerdo
>    montado en Minecraft vanilla SOLO se mueve si el jinete lleva en la mano
>    una "zanahoria en un palo"; sin ese item, WASD no le hace nada.
> 4. Se probo un caballo domado y ensillado (si responde a WASD sin item
>    extra) — funcionaba, pero quedaba visualmente desconectado del bloque de
>    lana (flotaba muy por encima del tablero).
> 5. Version actual: el bloque de lana vuelve a ser el protagonista visual, el
>    jugador se sienta justo encima (asiento invisible), y las teclas WASD se
>    leen con **ProtocolLib** interceptando el paquete de red que el cliente
>    envia siempre que estas montado en cualquier entidad — funciona sin
>    importar si esa entidad es "Steerable" o no.
>
> Nota sobre el paquete de input y la version de Minecraft: desde 1.21.4,
> Mojang cambio por completo el formato de este paquete (paso de dos floats
> sueltos a un record anidado de 7 booleanos). ProtocolLib no siempre lo
> desempaqueta solo, asi que `SnakeSteerPacketListener` prueba varios
> formatos en cascada (floats -> booleans -> reflexion cruda sobre el
> paquete NMS) para funcionar sin importar cual de los dos formatos use tu
> servidor.
>
> Nota sobre la version de Paper: el proyecto se quedo fijado en 1.20.4 en el
> `pom.xml` porque es la unica version que confirme que resuelve bien desde
> este entorno de desarrollo, pero el plugin ya se probo funcionando en
> servidores reales bastante mas nuevos (ver nota anterior). Si quieres subir
> tambien la version de Paper del `pom.xml`, dime la version exacta que usas
> y confirmamos juntos las coordenadas Maven correctas (o revisa
> https://papermc.io/downloads / su documentacion).
