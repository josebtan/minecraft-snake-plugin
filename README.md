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
  con las teclas WASD (leidas via ProtocolLib). Incluye un comando temporal de
  pruebas: `/snakedebug start|stop`.
- [ ] **Etapa 2 — Comandos y creacion del campo de juego.**
  Sistema de comandos definitivo (`/snake ...`) y delimitacion de una zona de
  juego (arena) donde la serpiente puede moverse, con paredes/limites.
- [ ] **Etapa 3 — Aparicion de comida, puntos y mecanicas del juego.**
  Generacion aleatoria de comida dentro del campo, sistema de puntuacion, y
  condiciones de fin de partida (colision con el borde).
- [ ] **Etapa 4 — Mecanica de movimiento y crecimiento de la cola.**
  La cola sigue exactamente el recorrido de la cabeza y crece al comer,
  incluyendo la deteccion de colision contra la propia cola / la de otros
  jugadores.

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

## Probar la Etapa 1

1. Compila el plugin y colocalo en `plugins/` de un servidor Paper, junto con
   **ProtocolLib** (ver seccion de dependencias mas arriba — imprescindible).
2. Inicia el servidor y entra con un jugador.
3. Ejecuta `/snakedebug start`: apareceras sentado justo encima de un bloque
   de lana de color. Tu camara es libre, puedes mirar a tu alrededor con
   normalidad.
4. Usa **W / A / S / D**: cada tecla mueve la serpiente en una direccion
   distinta de la rejilla. Te desplazaras junto con la cabeza automaticamente
   al ir sentado sobre ella.
5. Ejecuta `/snakedebug stop` para levantarte y detener la partida.

> Nota: en la Etapa 1 la serpiente no tiene todavia campo de juego delimitado,
> comida, ni cola — solo se prueba el movimiento de la cabeza (y del jugador
> sentado sobre ella). El resto llega en las siguientes etapas.
>
> Historial de decisiones de diseño para el movimiento/monta:
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
> Nota sobre la version de Paper: el proyecto se quedo fijado en 1.20.4 porque
> es la unica version que confirme que resuelve bien desde este entorno de
> desarrollo. PaperMC parece haber cambiado su esquema de versionado en algun
> momento posterior (sus tags de GitHub saltan de "1.21.11" a "26.1.2"), asi
> que si quieres apuntar el plugin a una version de servidor mas reciente,
> dime la version exacta que usas y confirmamos juntos las coordenadas Maven
> correctas (o revisa https://papermc.io/downloads / su documentacion).
