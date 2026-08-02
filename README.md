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
- Al pasar sobre la comida (un item de comida real tirado en el suelo — manzana,
  zanahoria, pan, etc. al azar), la serpiente **crece**: se anade un nuevo
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
  Etapa 1). Las arenas se delimitan marcando dos esquinas al estilo
  WorldEdit — solo registran DONDE puede aparecer la serpiente (y, en la
  Etapa 3, la comida); no construyen nada, cada quien decora/delimita su
  arena a mano. **Las arenas se guardan en disco** (`arenas.yml`) y se
  recargan solas al reiniciar el servidor.
  Todo el flujo tiene menus (GUI), no solo comandos de texto:
  - `/snake arena` (o `/snake arena menu`) abre un **panel de
    creacion/administracion**: marcar esquina 1/2 con un clic (en tu
    posicion actual), crear (te pide el nombre por chat), ver la lista, o
    eliminar una.
  - `/snake join` (sin nombre) abre un **listado de arenas existentes**
    para elegir con un clic.
  - Elegir una arena (desde el listado o con `/snake join <nombre>`) abre el
    menu de **modo** (un jugador / multijugador) y despues el de **color**
    de lana — en modo multijugador, los colores que ya este usando otro
    jugador activo en esa misma arena aparecen bloqueados.
  La deteccion de choques ya funciona: antes de mover la cabeza se revisa si
  el bloque de destino es aire; si no lo es (algo que el jugador construyo,
  o mas adelante la cola propia/ajena), la partida termina ahi mismo. El
  punto de aparicion es aleatorio dentro de la arena, verificando que haya
  espacio libre por delante para no chocar nada mas entrar.
- [x] **Etapa 3 — Aparicion de comida, puntos y mecanicas del juego.**
  Cada arena tiene su propia comida (un item real tirado en el suelo — manzana,
  zanahoria, pan, carne, etc. al azar, brillante para que se note bien),
  compartida por todos los jugadores que esten jugando ahi a la vez — el
  primero que la alcance se la come. Al comer, suma un punto (aviso por
  action bar, para no llenar el chat) y aparece una comida nueva en otro
  sitio libre de la misma arena. La comida se limpia del mundo automaticamente
  en cuanto ya no queda nadie jugando en esa arena (antes se quedaba
  abandonada para siempre — bug corregido). La busqueda de sitio libre para
  la comida tambien se reforzo: antes probaba un numero limitado de
  posiciones al azar y se rendia, lo que hacia que en partidas con varios
  jugadores (arena mas ocupada) a veces simplemente no apareciera comida
  nueva; ahora escanea toda la arena y solo falla si esta completamente llena.
- [x] **Etapa 4 — Mecanica de movimiento y crecimiento de la cola.**
  La cola ahora es real: sigue exactamente el recorrido de la cabeza y
  crece en 1 cada vez que se come. La deteccion de choques (el mismo
  chequeo "bloque no-aire" de la Etapa 2) ya cubre solo con eso la colision
  contra la propia cola y contra la de otros jugadores en la misma arena,
  sin logica aparte — con un caso especial: moverse justo a la casilla que
  la punta de la propia cola esta por dejar libre en ese mismo instante SI
  se permite (regla estandar del Snake clasico), para que dar una vuelta
  cerrada del largo exacto de la serpiente no cuente como choque injusto.
  Al chocar, el mensaje final incluye la puntuacion.

### Analisis de flujo de juego (un jugador / multijugador)

Tras probar las etapas anteriores, se reviso con calma que pasa exactamente
del principio al fin de una partida en cada modalidad, y se resolvieron
varios puntos que no estaban definidos:

- **Choque de frente**: si dos serpientes de la misma arena planean moverse a
  la misma casilla vacia en el mismo instante (o se cruzan, cada una entrando
  donde estaba la otra), se resuelve ANTES de mover a nadie: gana la
  serpiente mas larga; si hay empate exacto de tamaño, pierden todas las
  implicadas en ese choque. Sin esto, ganaba quien se procesara primero por
  puro orden interno — arbitrario e injusto.
- **Aviso a toda la arena**: cuando alguien choca, ya no se entera solo el
  (mensaje privado con su puntuacion) — el resto de jugadores de esa arena
  reciben un aviso tambien.
- **Regreso al salir/morir**: al terminar la partida (por chocar o con
  `/snake leave`), el jugador vuelve exactamente al sitio donde estaba parado
  antes de entrar a la arena — no se queda flotando dentro de ella.
- **Marcador lateral en vivo**: mientras juegas, un scoreboard en la barra
  lateral muestra la puntuacion de todos los jugadores activos en tu misma
  arena, actualizado al unirse, salir o comer alguien.

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
3. Ejecuta `/snake arena` (o `/snake arena menu`): se abre el **panel de
   creacion**. Parate en una esquina de la zona donde quieras el campo de
   juego y haz clic en "Marcar esquina 1" (se marca en tu posicion actual);
   ve hasta la esquina opuesta, vuelve a abrir el panel y haz clic en
   "Marcar esquina 2". Una vez marcadas ambas, haz clic en "Crear arena": se
   te pedira escribir el nombre por el **chat** (o escribir "cancelar" para
   abortar). Solo registra el rectangulo (y lo guarda en disco), no toca
   ningun bloque. Si quieres delimitar visualmente la zona (paredes,
   decoracion, lo que sea), construyelo tu mismo con normalidad — esos
   bloques funcionaran igual como obstaculos para la serpiente.
4. Cualquier jugador ejecuta `/snake join` (sin nombre): se abre un
   **listado de arenas existentes** para elegir una con un clic. (Tambien
   puedes ir directo con `/snake join <nombre>` si ya sabes el nombre.)
5. Al elegir arena, se abre un menu para elegir **Un jugador** o
   **Multijugador**, y despues otro para elegir el **color** de lana de su
   serpiente (en modo multijugador, los colores ya usados por otros
   jugadores activos en esa arena aparecen con un bloque de barrera, no
   seleccionables). Al elegir color, aparece sentado sobre la cabeza de su
   serpiente, en un punto aleatorio libre dentro de la arena. Camara libre,
   se controla con **W / A / S / D**.
6. Si la cabeza choca contra algo solido (o, en etapas futuras, contra una
   cola), la partida termina automaticamente y se avisa al jugador.
7. `/snake leave` para levantarse y detener la partida manualmente.
8. Desde el panel (`/snake arena menu`), el boton "Eliminar arena" abre el
   mismo listado pero en modo eliminar (un clic la borra, tambien en disco).
   El boton "Ver arenas" abre el listado normal (para unirte). El comando de
   texto `/snake arena list` sigue disponible como atajo rapido.
9. Reinicia el servidor y comprueba que las arenas siguen ahi con
   `/snake arena list` — se cargan solas desde `arenas.yml` al arrancar.

> Nota: borrar una arena (`arena delete`) no interrumpe a la fuerza ninguna
> partida que ya estuviera en curso ahi — simplemente deja de aparecer en la
> lista/menu para unirse. Interrumpir partidas activas al borrar su arena
> queda pendiente para una etapa futura si hace falta.
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
