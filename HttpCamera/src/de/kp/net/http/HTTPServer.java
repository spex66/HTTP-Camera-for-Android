package de.kp.net.http;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import android.content.Context;

public class HTTPServer implements Runnable {

	private static String SERVER_PORT = "8080";

	// indicator to determine whether the
	// server has stopped or not
	private boolean stopped = false;

	private ServerSocket serverSocket;

	private Context context;

	public HTTPServer(Context context) throws IOException {
		int port = Integer.parseInt(SERVER_PORT); // The port to listen
		serverSocket = new ServerSocket(port); // Create a socket to listen on
												// the given port
		this.context = context;
	}

	public void run() {

		while (this.stopped == false) {

			Socket client;
			try {
				
				client = serverSocket.accept();
				ClientRequestThread clientRequest = new ClientRequestThread(client, context);
				clientRequest.start(); // thread starts
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} // Wait for a connection

		}

	}

}