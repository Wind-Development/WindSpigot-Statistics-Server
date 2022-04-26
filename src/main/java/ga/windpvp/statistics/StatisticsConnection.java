package ga.windpvp.statistics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class StatisticsConnection {

	/**
	 * The pool for connections to use
	 */
	private static Executor connectionPool = Executors.newCachedThreadPool();

	public volatile int keepAliveTimeOutTime = 100;
	public volatile int players = 0;
	
	private volatile boolean hasDeregistered = false;
	
	public void closeConnection() {
		// Deregister
		Statistics.connectionList.remove(this);
		Statistics.servers.decrementAndGet();
		
		// Prevent statistic from decrementing twice
		hasDeregistered = true;
	}

	public void startConnection(Socket clientSocket) {
		// Initializes a new connection from a client
		Runnable connectionRunnable = (() -> {
			BufferedReader in;
			try {
				// Log to console
				Logger.log("New connection established.");
				// The connected client's input
				in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

				String inputLine;

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
						if (keepAliveTimeOutTime == 0 && !hasDeregistered) {
							closeConnection();
						} else if (hasDeregistered) {
							break;
						}

						// Decrement client's keepalive count down
						keepAliveTimeOutTime--;

					}
				});

				// Start keep alive thread
				connectionPool.execute(keepAliveRunnable);

				boolean newServerLock = false;
				boolean removedServerLock = false;

				// Handle client messages
				while ((inputLine = in.readLine()) != null) {

					// Exit connection if keep alive has expired
					if (hasDeregistered) {
						break;
					}

					// Exit connection if told to do so by the client
					if (inputLine.equalsIgnoreCase(".")) {
						break;

						// Register a new server
					} else if (inputLine.equalsIgnoreCase("new server")) {
						if (!newServerLock) {
							newServerLock = true;
							Statistics.servers.incrementAndGet();
						}

						// Remove a server
					} else if (inputLine.equalsIgnoreCase("removed server")) {
						if (!removedServerLock && newServerLock && !hasDeregistered) {
							
							// Prevent the client from removing the server multiple times
							removedServerLock = true;
							
							closeConnection();

							// Close the connection
							break;
						} else if (hasDeregistered) {
							break;
						}

						// Update keepalive status
					} else if (inputLine.equalsIgnoreCase("keep alive packet")) {
						keepAliveTimeOutTime = 100;
					} else if (inputLine.toLowerCase().contains("player count packet ")) {
						try {
							// Get the player count in the string
							players = Integer.valueOf(inputLine.toLowerCase().replace("player count packet ", ""));

						} catch (NumberFormatException e) {
							continue;
						}
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
