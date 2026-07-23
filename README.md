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
- La serpiente se mueve sola, casilla a casilla, sobre una rejilla horizontal fija
  (una "Y" concreta del mundo).
- El jugador la dirige simplemente **mirando** hacia el Norte, Sur, Este u Oeste;
  no necesita moverse fisicamente para controlarla.
- Al pasar sobre un bloque de comida, la serpiente **crece**: se anade un nuevo
  bloque a la cola, que sigue el recorrido exacto que hizo la cabeza (igual que en
  el Snake clasico).
- Chocar contra la cola propia, la de otro jugador, o contra el borde del campo de
  juego, termina la partida de ese jugador.

## Etapas de desarrollo

El desarrollo esta dividido en 4 etapas, tal y como se planifico:

- [x] **Etapa 1 — Movimiento del jugador y la cabeza de la serpiente.**
  Estructura base del proyecto (Maven + Paper API), y logica de movimiento de la
  cabeza sobre la rejilla, controlada por la direccion hacia la que mira el
  jugador. Incluye un comando temporal de pruebas: `/snakedebug start|stop`.
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
3. Ejecuta `/snakedebug start`: aparecera un bloque de lana bajo tus pies, que
   empezara a moverse solo.
4. Mira hacia el Norte, Sur, Este u Oeste para cambiar su direccion.
5. Ejecuta `/snakedebug stop` para detenerla y eliminarla.

> Nota: en la Etapa 1 la serpiente no tiene todavia campo de juego delimitado,
> comida, ni cola — solo se prueba el movimiento de la cabeza. El resto llega en
> las siguientes etapas.
