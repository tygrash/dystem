package com.goibibo.dystem;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Node extends Remote {

    public String startElection(String senderName) throws RemoteException;

    public String getMaster() throws RemoteException;

    public void setMaster(String newLeaderName) throws RemoteException;

    public String receiveCommunication(String senderName, String msg) throws RemoteException;

    public boolean getState() throws RemoteException;
}
