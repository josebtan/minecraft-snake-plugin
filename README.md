# SnakePlugin

Plugin de Minecraft (API **Paper**, Java 17) que recrea el clasico juego de **Snake**
dentro del servidor, pensado para multijugador: cada jugador controla su propia
serpiente, representada con **bloques de lana de colores** para poder distinguir
facilmente "cual serpiente es de quien".

No hace falta saber nada de desarrollo de plugins para usar este repositorio: el
proyecto se ira construyendo etapa por etapa, cada una en su propio commit /
pull request, con explicaciones incluidas en el codigo (comentarios en español).

## ¿Como se juega? (idea general)

- Cada jugador tiene una serpiente cuya **cabeza** es un bloque de lana de un color
  distinto (rojo, azul, verde, etc.).
- El jugador **viaja junto a su cabeza**: flota anclado justo encima de ella, por
  encima del tablero, y se desplaza automaticamente con cada movimiento de la
  serpiente (como si la estuviera "cabalgando" desde el aire).
- La camara del jugador queda **fija en vista cenital** (mirando hacia abajo) y
  **bloqueada**: no se puede rotar ni cambiar hasta que termine la partida.
  ⚠️ Aviso tecnico: Minecraft no permite forzar la vista en tercera persona (F5)
  desde el servidor, esa tecla es exclusiva del cliente. Lo que si se bloquea es
  la direccion de la camara (mirando siempre hacia abajo), consiguiendo el efecto
  de "vista de pajaro" tipico de un Snake visto desde arriba.
- La serpiente se mueve sola, casilla a casilla, sobre una rejilla horizontal fija
  (una "Y" concreta del mundo), y el jugador la dirige con las **teclas de
  movimiento (WASD)**: W = Norte, S = Sur, A = Oeste, D = Este.
- Al pasar sobre un bloque de comida, la serpiente **crece**: se anade un nuevo
  bloque a la cola, que sigue el recorrido exacto que hizo la cabeza (igual que en
  el Snake clasico).
- Chocar contra la cola propia, la de otro jugador, o contra el borde del campo de
  juego, termina la partida de ese jugador.

## Etapas de desarrollo

El desarrollo esta dividido en 4 etapas, tal y como se planifico:

- [x] **Etapa 1 — Movimiento del jugador y la cabeza de la serpiente.**
  Estructura base del proyecto (Maven + Paper API). El jugador viaja flotando
  justo encima de la cabeza de su serpiente (se mueve con ella en cada paso),
  con la camara fija en vista cenital y bloqueada durante toda la partida,
  controlando el movimiento con las teclas WASD. Incluye un comando temporal de
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

1. Compila el plugin y colocalo en `plugins/` de un servidor Paper 1.20.x.
2. Inicia el servidor y entra con un jugador.
3. Ejecuta `/snakedebug start`: apareceras flotando en el aire, por encima de un
   bloque de lana de color, con la camara mirando hacia abajo (fija, no la puedes
   mover con el raton ni desplazar caminando).
4. Usa **W / A / S / D**: cada tecla mueve la serpiente en una direccion distinta
   de la rejilla. Te desplazaras junto con la cabeza automaticamente.
5. Ejecuta `/snakedebug stop` para liberarte, detener la partida y recuperar el
   control normal de tu camara y tu movimiento.

> Nota: en la Etapa 1 la serpiente no tiene todavia campo de juego delimitado,
> comida, ni cola — solo se prueba el movimiento de la cabeza (y del jugador
> viajando junto a ella). El resto llega en las siguientes etapas.
