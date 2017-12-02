# Dystem

Dystem is a example utility to invoke your own multiple Java RMI ServerNode and they will establish communication among themselves.

#### Features
- There are two set of nodes - ACTIVE and INACTIVE, which gets decided by random even/odd int generation logic.
- ACTIVE nodes can take part in a master election. INACTIVE onces can only observe.
- When a new ACTIVE node is bind to rmiregistry, then if no master is available, it elects itself as master. If master is available, then it gets attached to it as slave.
- When an INACTIVE node is bind to rmiregistry, then if no master is available, it will just observe and keep on searching for master in intervals, but doesn't elect itself as master. If master is available, this node attached to it as slave.
- When a slave becomes inactive, master will come to know about the event and it will show up on standard console output of master.
- When a master becomes inactive, all other ACTIVE nodes sense the event and try to elect themselves as leader and one who does it first will be master.

#### Steps to run
```
    $ git clone https://github.com/tygrash/dystem.git
    $ cd dystem
    $ brew install gradle
    $ ./gradlew clean build
    $ cd /src/main/java/com/goibibo/dystem
    $ mkdir classes
    $ javac -d [path to slf4j-api-1.7.25.jar] classes Node.java ServerNode.java
    $ cd classes
    $ rmiregistry &
    $ java -cp .:[path to slf4j-simple-1.6.1.jar]:[path to slf4j-api-1.7.25.jar] com.goibibo.dystem.ServerNode    (Run this command from various terminals/consoles and it will spawn up random active/inactive server nodes, and you can play with the combinations)

    P.S.: Sometimes dude to randomness, multiple ACTIVE or INACTIVE nodes will spawn, so be patient and wait for the different one to spawn to test your scenario.
```
