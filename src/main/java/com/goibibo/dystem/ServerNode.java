package com.goibibo.dystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.AlreadyBoundException;
import java.rmi.ConnectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class ServerNode extends UnicastRemoteObject implements Node {

    private String host;        //Host on which the server is running
    private String nodeName;    //Unique identifier for the server node
    private String master = ""; //maintains the current master node name
    private boolean active;     //tells whether node is active or incactive(observer)

    private static final int delay = 5000;                      //delay in milliseconds before scheduler is to be executed
    private static final int messageToMasterInterval = 1000;    //time in milliseconds between successive task executions of scheduler

    private boolean heartbeatFromMaster = false;    //true if communication is successful from master
    private boolean noMasterFound = true;           //true if master is not elected for distributed system

    private Set<String> slaves = new HashSet<String>();     //maintains a list of all slaves connected to master(only populated for master ServerNode)

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerNode.class);     //instantiate logger

    //Constructor for ServerNode class
    public ServerNode(String nodeName, String host) throws RemoteException {
        this.nodeName = nodeName;
        this.host = host;

        Random rand = new Random();             //logic to decide the ServerNode as active/inactive.
        int value = rand.nextInt(10);    //take any random value between [0, 9)
        this.active = value % 2 == 0;           //active if divisible by 0, else inactive

        LOGGER.info(active ? "I'm ACTIVE" : "I'm INACTIVE");

        Timer timer = new Timer();

        // A scheduler to:
        // 1. check for dead slaves
        // 2. sending heartbeat communication messages to Master
        // 3. keep looking for Master until it is not found
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkForDeadSlaves();       //check for dead slaves from registry lookups

                if (master != null && !nodeName.equals(master)) {
                    try {
                        sendCommunicationToMaster("Message from " + nodeName);      //send heartbeat communication to master
                    } catch (RemoteException | NotBoundException e) {
                    }

                    huntForMaster();        //keep looking for master and elect itself incase master not found
                }
            }
        }, delay, messageToMasterInterval);

        LOGGER.info(nodeName + " prepared");
    }

    /**
     * This function check for dead slaves.
     * <p>
     * It reads all the slave nodes from the master slave set and
     * tries to find those slaves in registry. If ConnectionException occurs,
     * then that slave can be marked as dead and removed from master's slave set.
     */
    private void checkForDeadSlaves() {
        if (nodeName.equals(master)) {
            try {
                LOGGER.info("Slaves size-: " + slaves.size());

                Registry reg = LocateRegistry.getRegistry(host);
                Iterator<String> itr = slaves.iterator();
                while (itr.hasNext()) {
                    String n = itr.next();
                    try {
                        Node node = (Node) reg.lookup(n);
                        node.getMaster();
                    } catch (NotBoundException | ConnectException e) {
                        LOGGER.warn(n + " is dead");
                        slaves.remove(n);
                        try {
                            reg.unbind(n);
                        } catch (NotBoundException e1) {

                        }
                    }
                }
            } catch (RemoteException e) {
            }
        }
    }

    /**
     * This function is responsible for election of master.
     * <p>
     * First it locates all the nodes in registry and try to read master information
     * from them. If no master is found, then try to elect each `active` node as
     * master one by one, until gets success or iteration completes.
     * Also, if above approach results in failure, then tries to elect itself as master,
     * if the current node is `active`.
     */
    private void huntForMaster() {
        if (noMasterFound && !nodeName.equals(master) && !heartbeatFromMaster) {
            try {
                LOGGER.info("[" + nodeName + "]" + "Figuring out master");
                Registry reg = LocateRegistry.getRegistry(host);

                for (String name : reg.list()) {
                    try {
                        if (!name.equals(nodeName)) {
                            Node node = (Node) reg.lookup(name);
                            if (node.getMaster().length() > 0) {
                                master = node.getMaster();
                                noMasterFound = false;
                            } else {
                                if (node.getState()) {
                                    String response = node.startElection(nodeName); //Message broadcasted by current node to all others to elect itself as new master
                                    if (response.length() > 0) {
                                        noMasterFound = false;
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (NotBoundException | ConnectException e) {
                        try {
                            LOGGER.error("[" + nodeName + "] is not reachable");
                            reg.unbind(name);
                        } catch (NotBoundException er) {
                            //suppress
                        }
                    }
                }

                if (noMasterFound && active) {
                    LOGGER.info("[" + nodeName + "]: No master found, electing myself as leader");
                    startElection(nodeName);
                    noMasterFound = false;
                }
            } catch (RemoteException e) {
            }
        } else if (heartbeatFromMaster)
            heartbeatFromMaster = false;
    }

    /**
     * This function sends communication to master and expects a response from it,
     * so that it can detect master failure.
     *
     * @param message Message to be sent to master node
     * @throws RemoteException   When remote communication fails to other node
     * @throws NotBoundException When lookup node is not bound to registry anymore
     */
    private void sendCommunicationToMaster(String message) throws RemoteException, NotBoundException {
        Registry reg = LocateRegistry.getRegistry(host);
        try {
            Node masterNode = (Node) reg.lookup(master);
            String response = masterNode.receiveCommunication(nodeName, message);
            LOGGER.info("Master " + master + " responded to heartbeat - " + response);

            heartbeatFromMaster = true;
        } catch (NotBoundException | ConnectException e) {
            LOGGER.warn("Master com.goibibo.Node " + master + " not in quorum now!");
            master = "";
            noMasterFound = true;
            reg.unbind(master);
        }
    }

    /**
     * A function to broadcast master election event to other nodes in attempt to elect itself as master.
     *
     * @param senderName Name of the election broadcast sender
     * @return Return a non-empty repsonse to show success
     * @throws RemoteException
     */
    @Override
    public String startElection(String senderName) throws RemoteException {
        String response = "";
        try {
            master = nodeName;

            Registry registry = LocateRegistry.getRegistry(host);
            for (String node : registry.list()) {
                if (!node.equals(nodeName)) {
                    try {
                        Node electionNode = (Node) registry.lookup(node);
                        electionNode.setMaster(master);
                    } catch (NotBoundException e) {
                        try {
                            LOGGER.warn("Master com.goibibo.Node " + node + " not in quorum now!");
                            master = "";
                            noMasterFound = true;
                            registry.unbind(node);
                        } catch (NotBoundException | ConnectException er) {
                        }
                    }
                }
            }

            response = "Leader election done";
        } catch (RemoteException e) {
        }

        return response;
    }

    /**
     * A function to get the master node name.
     *
     * @return Mater node name
     * @throws RemoteException
     */
    @Override
    public String getMaster() throws RemoteException {
        return master;
    }

    /**
     * A function to set/update the master name for a node.
     *
     * @param newMaster Name of new master to be set
     * @throws RemoteException
     */
    @Override
    public void setMaster(String newMaster) throws RemoteException {
        master = newMaster;
        LOGGER.info(newMaster + " is the new master.");
    }

    /**
     * A function explicitely for the master node to receive communication from
     * various slave nodes in the registry and add them in slaves set.
     *
     * @param senderName Name of the communication sender slave node
     * @param message    Message sent by sender slave node
     * @return Message with appropriate response to slave
     * @throws RemoteException
     */
    @Override
    public String receiveCommunication(String senderName, String message) throws RemoteException {
        if (master.equals(nodeName)) {
            LOGGER.info(senderName + " to " + master + ": " + message);
            slaves.add(senderName);
            LOGGER.info("Successfull communication with " + senderName);
            return "Message received";
        }

        return "[" + master + "]: I'm not master now";
    }

    /**
     * A function to get the current state of the node - `active` or `inactive`(observer).
     *
     * @return true if node is `active` / false if node `inactive`(observer)
     * @throws RemoteException
     */
    @Override
    public boolean getState() throws RemoteException {
        return active;
    }

    //Main method to run the server node
    public static void main(String[] args) throws RemoteException, AlreadyBoundException {
        Random random = new Random();
        long nameId = random.nextLong();    //generate random long to make unique node names
        String name = "com.goibibo.Node-" + nameId;
        String host = "localhost";

        ServerNode node = new ServerNode(name, host);   //Creating new instance of ServerNode

        Registry reg = LocateRegistry.getRegistry(host);    //Finding the registry for host
        reg.bind(name, node);   //Binding created node to registry
    }
}
