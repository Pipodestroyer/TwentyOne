# Twenty One Project
## Información
Juego hecho en 14 dias como proyecto para Programación Orientada a Objetos de la Universidad Santo Tomás.
- Daniel Rodriguez
- Juan Galindo.

## Introducción
### ¿Qué es?
TOP es un juego multijugador competitivo uno versus uno donde ambos jugadores se enfrentan siguiendo las reglas de 21, pero con un plot twist, ambos jugadores tendrán cartas especiales que pueden usar para cambiar el juego para tener distintas condiciones de victoria y todas las cartas con números son únicas entonces no se pueden repetir. Ambos jugadores empiezan con la misma cantidad de fichas y se apostaran dichas fichas, el juego termina cuando un jugador se quiebra (no tiene más fichas). Como en 21, se inicia jugando a acercarse o tener 21 sin pasarse.

### ¿Cómo se juega?
En rondas, al inicio de cada ronda ambos jugadores reciben dos cartas especiales y dos cartas de numero, el primer numero oculto para el oponente. En el turno de cada jugador pueden usar sus cartas especiales todas las veces que quieran y terminan su turno al comer un numero o plantarse. Comer un numero tiene una probabilidad de también comer una carta especial. 
Todas las cartas especiales están ocultas para el oponente hasta que se usen. Las rondas terminan cuando ambos jugadores se plantan y no usan alguna carta especial, revelando todos sus numeros escondidos y definiendo el ganador. El perdedor empieza de primeras la siguiente ronda. En empates, empieza el perdedor de la anterior ronda.

### ¿Cómo se define el ganador?
Se define el ganador y el perdedor de cada ronda dependiendo del numero que se esta jugando (tipicamente 21) con la siguiente estructura despues de sumar los números de sus cartas:
- Un jugador esta más cerca que el otro del numero que se esta jugando, entonces gana.
- Solo un jugador se pasa del numero que se esta jugando, entonces pierde.
- Ambos jugadores se pasan del numero que se esta jugando, entonces gana el quien menos se haya pasado.
- Ambos jugadores tienen el mismo numero total, entonces es un empate, nadie gana ni pierde fichas.

Al final de cada ronda, se limpia la mesa quitando las cartas especiales y numeros presentes, el perdedor paga con sus fichas la apuesta, dependiendo del juez, la apuesta puede ir al ganador o al mismo juez. 

## Cartas especiales
### Tipos
Hay 2 tipos de cartas especiales que cambian la forma de como se usan en el juego.
- Continuas: Se colocan en el tablero y mientras sigan ahí sus efectos serán aplicados.
- Instantáneas: Se usan sin colocarse en el tablero y aplican el efecto una vez.

### Diccionario
A continuación, se mostraran las 25 cartas especiales presentes en el juego divididas por su tipo, si no les gusta leer saltad esta parte.

- Continuas:
  - Bless (Bendicion): Previene que pierdas tus fichas si al perderlas te hubieras quebrado.
  - Go for 17 (Jugar a 17): Cambia el numero que los jugadores quieren llegar a 17. 
  - Go for 24 (Jugar a 24): Cambia el numero que los jugadores quieren llegar a 24. 
  - Go for 27 (Jugar a 27): Cambia el numero que los jugadores quieren llegar a 27. 
  - One-Up: Sube la apuesta por 1. 
  - Two-Up: Sube la apuesta por 2. 
  - Shield (Escudo): Baja la apuesta por 1.
  - Shield-Plus (Escudo+): Baja la apuesta por 2. 
  - Bloodshed: Sube la apuesta por 1 y comes una carta especial. 

- Instantáneas:
  - 2-Card: Comes el numero 2 si esta disponible.
  - 3-Card: Comes el numero 3 si esta disponible. 
  - 4-Card: Comes el numero 4 si esta disponible. 
  - 5-Card: Comes el numero 5 si esta disponible. 
  - 6-Card: Comes el numero 6 si esta disponible. 
  - 7-Card: Comes el numero 7 si esta disponible. 
  - Destroy (Destruir): Destruyes la carta especial continua más reciente de la mesa colocada por el oponente. 
  - Friendship (Amistad): Todos los jugadores comen 2 cartas especiales. 
  - Reincarnation (Reincarnación): Destruyes la carta especial continua más reciente de la mesa colocada por el oponente depues comes una carta especial. 
  - Refresh (Reinicio): Empiezas fresco con dos nuevas cartas, devolviendo a la piscina de números las que tenias. 
  - Remove (Remover): Remueves el ultimo numero que tiene el oponente, devolviéndolo a la piscina de números. 
  - Return (Devolver): Devuelves el ultimo numero que tenias, devolviéndolo a la piscina de números. 
  - Exchange (Intercambio): Intercambia los dos ultimos numeros de los jugadores. 
  - Disservice: Haces comer un número al oponente.
  - Perfect Draw: Comes un número disponible de la piscina que más te acerca al numero que se esta jugando. 
  - Hush: Comes un número disponible y queda oculto para al oponente. 

## Configuraciones
### Jueces
Cada juez cambia como se manejan las fichas apostadas, cambiando las prioridades de los jugadores en cada nueva partida. Los jueces tendrá sus reglas y cuantas fichas adicionales les da a los jugadores:
- El anfitrión: El ganador se queda con las fichas apostadas. No da fichas.
- El banco: El perdedor da las fichas apostadas al juez. +6 fichas.
- La mafia: Después de la primera ronda, el ganador le da una ficha al juez y se queda con las demás. +2 fichas.

### Apuestas
La cantidad de apuestas cambia como ambos jugadores pueden subir o bajar la apuesta a su discreción.
- Una apuesta: Ambos jugadores están atadas a la misma apuesta. Si la suben o la bajan ambos sufrirán sus consecuencias.
- Dos apuestas. Cada jugador esta atado a su apuesta. Jugadores suben la apuesta del oponente mientras que bajan su propia apuesta.

### Fichas
La cantidad de fichas iniciales define la longitud promedio de cada partida.
- 5 fichas: Partidas rápidas que duran alrededor de 3 minutos.
- 7 o 9 fichas: Partidas normales que duran alrededor de 6 minutos.
- 11 fichas: Partidas largas que duran alrededor de 9 minutos.

### Modo aleatorio
Cada configuración se puede activar el modo aleatorio, que elige una configuración aleatoria de las opciones que los jugadores quieran.
- Jueces y apuestas: Ambos deben de tener al menos 3 opciones seleccionadas. Ejemplo: 1 juez, 2 opciones de apuestas; 2 jueces, 1 opción de apuesta.
- Fichas: Debe de tener al menos 2 opciones seleccionadas. Se recomienda para principiantes jugar con 7 o 9 fichas.

