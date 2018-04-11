package com.nedap.university;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import client.Client;

public class Main {

    private static boolean keepAlive = true;
    private static boolean running = false;

    // have to find Pi without IP address
    // See the website for the hostname of the server
     private static String serverAddress = "192.168.1.1";    //PI
//    private static String serverAddress = "localhost";

    // Challenge server port
    private static int serverPort = 8002;
    
    private Main() {}

    public static void main(String[] args) {
        running = true;
        initShutdownHook();
        
        Client client = null;
        InetAddress host = null;
        
		try {
			host = InetAddress.getByName(serverAddress);
			client = new Client(host, serverPort);
		} catch (UnknownHostException e) {
			System.out.println("Invalid hostname!");
			System.exit(0);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
        
        while (keepAlive && !client.isFinished()) {
            try {
                // do useful stuff
				Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                keepAlive = false;
            }
        }

        System.out.println("Stopped");
        running = false;
    }

    private static void initShutdownHook() {
        final Thread shutdownThread = new Thread() {
            @Override
            public void run() {
                keepAlive = false;
                while (running) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        };
        Runtime.getRuntime().addShutdownHook(shutdownThread);
    }
}
