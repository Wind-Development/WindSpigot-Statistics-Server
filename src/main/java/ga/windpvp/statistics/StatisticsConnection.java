package ga.windpvp.statistics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

public class StatisticsConnection {


	public volatile int keepAliveTimeOutTime = 100;
	public volatile boolean shouldCloseConnection = false;
	
	public volatile int players = 0;

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
						if (keepAliveTimeOutTime == 0) {
							// Close the connection and decrement server count
							shouldCloseConnection = true;
							Statistics.servers.decrementAndGet();
							break;
						}

						// Decrement client's keepalive count down
						keepAliveTimeOutTime--;

					}
				});

				// Start keep alive thread
				new Thread(keepAliveRunnable).start();

				boolean newServerLock = false;
				boolean removedServerLock = false;

				// Handle client messages
				while ((inputLine = in.readLine()) != null) {

					// Exit connection if keep alive has expired
					if (shouldCloseConnection) {
						
						Statistics.connectionList.remove(this);

						// Log to console
						Logger.log("There is one removed server.");

						// Close connection
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
						if (!removedServerLock && newServerLock) {
							removedServerLock = true;
							Statistics.servers.decrementAndGet();

							// Close the connection
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
					Logger.log(inputLine);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

		// Start the connection with the client on its own thread
		new Thread(connectionRunnable).start();
	}

}
