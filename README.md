#Test task for Data Monsters

## Usage
### Start game server
```
sbt run
```
### Connect game server
First player
```
telnet localhost 6600
```
and second one
```
telnet localhost 6600
```

Type SPACE and ENTER when see "3"


##Requirements
Use Scala/Akka.

##User stories
1. Player connects game server by use of telnet.
1.1. It's assumed that player's terminal uses UTF-8 encoding
1. After the connection is established the game greets the player with message "Привет! Попробую найти тебе противника"
1.1. hereinafter all messages is ended by "\n"
1. The server chooses other player among already connected players waiting for a peer competitor
1. After the peer found the server notifies both players by the message "Противник найден. Нажмите пробел, когда увидите цифру 3"
1. In randomized interval (2sec < T < 4sec) the server sends messages "1", "2" or "3" in random order to players
1. After "3" is sent, the game waits till one of the player sends space symbol.
1. The first player who sent a space symbol is a winner. 
1.1. The winner receives a message "Вы нажали пробел первым и победили".
1.1. The looser receives a message "Вы не успели и проиграли".
1.1. Both are disconnected from the game.
1. If a player sends space symbol before the "3" was sent to him he becomes a looser
1.1. A message "Ваш противник поспешил и вы выйграли" is sent to the winner
1.1. A message "Вы поспешили и проиграли" is sent to the looser
1.1. Both are disconnected from the game.

##Assumption
1. Message from a player perspective is a number of symbols ended by newline character, so to send SPACE a player must type SPACE and ENTER from newline
1. The game ignores any strings except SPACE (" ")

##TODO
1. Actors have internal states that is not good. Fix it.
1. Tests
