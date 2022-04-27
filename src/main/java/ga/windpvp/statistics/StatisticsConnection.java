package ga.windpvp.statistics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class StatisticsConnection {

	/**
	 * The pool for connections to use
	 */
	private static Executor connectionPool = Executors.newFixedThreadPool(10000);
	
	/**
	 * The time out for the keep alive in seconds
	 */
	public volatile int keepAliveTimeOutTime = 180;
	
	/**
	 * The server's players
	 */
	public volatile int players = 0;
	
	/**
	 * Whether this connection has been unregistered
	 */
	private volatile boolean hasUnregistered = false;
	
	boolean newServerLock = false;
	boolean removedServerLock = false;
	
	/**
	 * Starts the keep alive handler
	 */
	private void startKeepAliveHandler() {
		// Handle keepalives
		Runnable keepAliveRunnable = (() -> {
			
			while (true) {

				// Check for keepalive expiry every second
				try {
					TimeUnit.SECONDS.sleep(1);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				// See if the keepalive is expired
				if (keepAliveTimeOutTime == 0 && !hasUnregistered) {
					closeConnection();
				} else if (hasUnregistered) {
					break;
				}

				// Decrement client's keepalive count down
				keepAliveTimeOutTime--;

			}
		});

		// Start keep alive thread
		connectionPool.execute(keepAliveRunnable);
	}
	
	/**
	 * Closes the connection
	 */
	public void closeConnection() {
		// Deregister
		Statistics.connectionList.remove(this);
		if (newServerLock) {
			Statistics.servers.decrementAndGet();
		}
		// Prevent statistic from decrementing twice
		hasUnregistered = true;
	}

	/**
	 * Starts the connection
	 * @param clientSocket The client's socket
	 */
	public void startConnection(Socket clientSocket) {
		// Initializes a new connection from a client
		Runnable connectionRunnable = (() -> {
			try {
				// Log to console
				Logger.log("New connection established.");
				// The connected client's input
				BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				
				// Output for communication with client 
	            PrintWriter output = new PrintWriter(clientSocket.getOutputStream(),true);
				
				// Handle keep alives
				startKeepAliveHandler();

				// Handle client messages
				String clientInput;
				while ((clientInput = in.readLine()) != null) {

					// Exit connection if keep alive has expired
					if (hasUnregistered) {
						break;
					}

					// Exit connection if told to do so by the client
					if (clientInput.equalsIgnoreCase(".")) {
						break;

						// Register a new server
					} else if (clientInput.equalsIgnoreCase("new server")) {
						if (!newServerLock) {
							newServerLock = true;
							Statistics.servers.incrementAndGet();
						}

						// Remove a server
					} else if (clientInput.equalsIgnoreCase("removed server")) {
						if (!removedServerLock && newServerLock && !hasUnregistered) {
							
							// Prevent the client from removing the server multiple times
							removedServerLock = true;
							
							closeConnection();

							// Close the connection
							break;
						} else if (hasUnregistered) {
							break;
						}

						// Update keepalive status
					} else if (clientInput.equalsIgnoreCase("keep alive packet")) {
						keepAliveTimeOutTime = 100;
					} else if (clientInput.toLowerCase().contains("player count packet ")) {
						try {
							// Get the player count in the string
							players = Integer.valueOf(clientInput.toLowerCase().replace("player count packet ", ""));

						} catch (NumberFormatException e) {
							continue;
						}
						// Returns info to the client
					} else if (clientInput.equalsIgnoreCase("query data")) {
						output.println("servers " + Statistics.servers.get() + ",players " + Statistics.players.get());
						// Example result: servers 10, players 50
						Logger.log("The API has been accessed, output: " +"servers " + Statistics.servers.get() + ",players " + Statistics.players.get());
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

		// Start the connection with the client on its own thread
		connectionPool.execute(connectionRunnable);
	}

}
