package com.rtzan.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import java.net.ServerSocket;
import java.net.Socket;


public class ProxyMultiThread {

    //~ ----------------------------------------------------------------------------------------------------------------
    //~ Methods 
    //~ ----------------------------------------------------------------------------------------------------------------

    public static void main(String[] args) {
        System.out.println("******************************************************************");
        boolean completed = false;
        long connection_index = 1;
        try {
            if (args.length != 3)
                throw new IllegalArgumentException("insufficient arguments");
            // and the local port that we listen for connections on
            String host = args[0];
            int remoteport = Integer.parseInt(args[1]);
            int localport = Integer.parseInt(args[2]);
            // Print a start-up message
            System.out.println("START proxy for " + host + ":" + remoteport + " on port " + localport);
            System.out.println("******************************************************************");
            try(ServerSocket server = new ServerSocket(localport)) {
                while (!completed) {
                    System.out.println("# WAITING FOR CONNECTIONS [" + connection_index + "]");
                    System.out.println("******************************************************************");

                    new ThreadProxy(connection_index, server.accept(), host, remoteport);

                    connection_index++;
                }
            }

        } catch (Exception e) {
            System.err.println(e);
            System.err.println("Usage: java ProxyMultiThread " +
                "<remotehost> <remoteport> <localport>");
        }
        System.out.println("******************************************************************");
    }
}

/**
 * Handles a socket connection to the proxy server from the client and uses 2 threads to proxy between server and client
 *
 * @author jcgonzalez.com
 */
class ThreadProxy extends Thread {

    //~ ----------------------------------------------------------------------------------------------------------------
    //~ Instance fields 
    //~ ----------------------------------------------------------------------------------------------------------------

    private final long index;
    private Socket clientSocket;
    private final String serverUrl;
    private final int serverPort;

    //~ ----------------------------------------------------------------------------------------------------------------
    //~ Constructors 
    //~ ----------------------------------------------------------------------------------------------------------------

    ThreadProxy(long index, Socket clientSocket, String serverUrl, int serverPort) {
        this.index = index;
        this.serverUrl = serverUrl;
        this.serverPort = serverPort;
        this.clientSocket = clientSocket;
        this.start();
    }

    //~ ----------------------------------------------------------------------------------------------------------------
    //~ Methods 
    //~ ----------------------------------------------------------------------------------------------------------------

    @Override
    public void run() {
        System.out.println("### START - SERVICING CLIENT [" + index + "] #");
        try {
            final byte[] request = new byte[1024];

            final InputStream inFromClient = clientSocket.getInputStream();
            final OutputStream outToClient = clientSocket.getOutputStream();

            doStuff(request, inFromClient, outToClient);

            outToClient.close();
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("### FINISH - SERVICING CLIENT [" + index + "] #");
    }

    private void doStuff(final byte[] request, final InputStream inFromClient, OutputStream outToClient) {
        // connects a socket to the server
        try(Socket server = new Socket(serverUrl, serverPort)) {

            byte[] reply = new byte[4096];

            // a new thread to manage streams from server to client (DOWNLOAD)
            final InputStream inFromServer = server.getInputStream();
            // a new thread for uploading to the server
            new Thread(() -> {
                int bytes_read;
                try(OutputStream outToServer = server.getOutputStream()) {
                    while ((bytes_read = inFromClient.read(request)) != -1) {
                        outToServer.write(request, 0, bytes_read);
                        outToServer.flush();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
            // current thread manages streams from server to client (DOWNLOAD)
            int bytes_read;
            try {
                while ((bytes_read = inFromServer.read(reply)) != -1) {
                    outToClient.write(reply, 0, bytes_read);
                    outToClient.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            PrintWriter out = new PrintWriter(new OutputStreamWriter(outToClient));
            out.flush();
            throw new RuntimeException(e);
        }
    }
}
