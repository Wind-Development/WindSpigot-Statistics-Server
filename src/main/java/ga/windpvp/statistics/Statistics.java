package ga.windpvp.statistics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import ga.windpvp.statistics.client.TestClient;

public class Statistics {
	
	/**
	 * The amount of online WindSpigot servers there are
	 */
	private static AtomicInteger servers = new AtomicInteger(0);
	
	/**
	 * The total player count
	 */
	private static AtomicInteger players = new AtomicInteger(0);;
	
	/**
	 * A map to keep track of keep alives and their expiry time
	 */
	ConcurrentMap<Socket, Integer> keepAliveTimeOutTime = new ConcurrentHashMap<>();
	
	/**
	 * A map to signal connection closing
	 */
	ConcurrentMap<Socket, Boolean> shouldCloseConnection = new ConcurrentHashMap<>();
	
	/**
	 * A map to track the player count on each server
	 */
	ConcurrentMap<Socket, Integer> playerCountMap = new ConcurrentHashMap<>();

	/**
	 * The socket of the server
	 */
	private ServerSocket serverSocket;
	
	
	public static void main(String[] args) {
		// Runnable for the statistics server
		Runnable statisticsRunnable = (() -> {
			try {
				new Statistics().run();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

		// Start the server on its own thread (We do this so that we can test the server with clients)
		new Thread(statisticsRunnable).start();

		// Testing purposes (dummy clients)

		
		try {
			Thread.sleep(10);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}

		Runnable clientRunnable = (() -> {
			TestClient client = new TestClient();
			try {
				client.startConnection("localhost", 500);
				client.sendMessage("new server");
				System.out.println(servers.get());

				Runnable keepAliveRunnable = (() -> {
					while (true) {
						try {
							client.sendMessage("keep alive packet");
							System.out.println("Keep alive sent");
							client.sendMessage("player count packet 5".toLowerCase());
							System.out.println("Player count sent");
						} catch (IOException e) {
							e.printStackTrace();
						}
						
						try {
							TimeUnit.SECONDS.sleep(30);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				});
				
				new Thread(keepAliveRunnable).start();

				client.stopConnection();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

		new Thread(clientRunnable).start();
		new Thread(clientRunnable).start();
		new Thread(clientRunnable).start();
		
	}
	
	private void runConsoleInputTask() {
		// Scans for console input
		Runnable consoleRunnable = (() -> {
			// Input scanner
			Scanner scanner = new Scanner(System.in);

			// Log to console
			System.out.println("Initialized console scanner.");
			// Continuously scan
			while (true) {
				// The input command string
				String command = scanner.nextLine();
				
				// Handle command
				if (command.equalsIgnoreCase("servers")) {
					System.out.println("There are " + servers.get() + " servers running WindSpigot.");
				} else if (command.equalsIgnoreCase("stop")) {
					System.out.println("Stopping...");
					scanner.close();
					System.exit(0);
				} else if (command.equalsIgnoreCase("players")) {
					System.out.println("There are " + players.get() + " players on WindSpigot servers.");
				}
			}
		});

		// Start scanning
		new Thread(consoleRunnable).start();
	}
	
	private void runConsoleLogTask() {
		// Logs the amount of servers running WindSpigot every so often
				Runnable repeatingDisplayRunnable = (() -> {
					while (true) {
						try {
							Thread.sleep(1000000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						System.out.println("There are " + servers.get() + " servers running WindSpigot.");
					}
				});
				
				// Start the server count logger
				new Thread(repeatingDisplayRunnable).start();
	}
	
	private void runPlayerCountUpdateTask() {
		// Updates the total player count every so often
		Runnable repeatingPlayerCountRunnable = (() -> {
			while (true) {
				
				try {
					TimeUnit.SECONDS.sleep(5);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
				// Reset the player count
				players.set(0);
				
				System.out.println("Updating player count");
				
				// Update the player count
				for (Socket socket : playerCountMap.keySet()) {
					
					System.out.println("Updating player count");
					
					Integer count = playerCountMap.get(socket);
					
					if (count != null) {
						players.addAndGet(count);
					}
					
				}
			}
		});
				
		// Start Updating
		new Thread(repeatingPlayerCountRunnable).start();
	}


	private void run() throws IOException {
		serverSocket = new ServerSocket(500);
		
		System.out.println("Started WindSpigot statistics server.");

		runConsoleInputTask();
		runConsoleLogTask();	
		runPlayerCountUpdateTask();

		// Handle new connections on its own thread so the server can process multiple clients
		while (true) {
			this.startConnection(serverSocket.accept());
		}
	}

	private void startConnection(Socket clientSocket) {
		// Initializes a new connection from a client
		Runnable runnable = (() -> {
			BufferedReader in;
			try {
				// Log to console
				System.out.println("New connection established.");
				// The connected client's input
				in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

				String inputLine;
				
				// Handle keepalives 
				Runnable keepAliveRunnable = (() -> {
					
					// Register this client
					shouldCloseConnection.put(clientSocket, false);
					keepAliveTimeOutTime.put(clientSocket, 100);
					
					while (true) {
						
						// Check for keepalive expiry every second
						try {
							TimeUnit.SECONDS.sleep(1);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						
						// Make sure the client is registered
						if (keepAliveTimeOutTime.get(clientSocket) != null) {
							
							// See if the keepalive is expired
							if (keepAliveTimeOutTime.get(clientSocket) == 0) {
								// Close the connection and decrement server count
								shouldCloseConnection.put(clientSocket, true);
								servers.decrementAndGet();
								break;
							}
							
							// Decrement client's keepalive count down
							keepAliveTimeOutTime.put(clientSocket, keepAliveTimeOutTime.get(clientSocket) - 1);
						} else {
							break;
						}
					}
				});
				
				// Start keep alive thread
				new Thread(keepAliveRunnable).start();
				
				boolean newServerLock = false;
				boolean removedServerLock = false;

				// Handle client messages
				while ((inputLine = in.readLine()) != null) {
					
					// Exit connection if keep alive has expired
					if (shouldCloseConnection.get(clientSocket) != null) {
						if (shouldCloseConnection.get(clientSocket)) {
							
							// Unregister client
							shouldCloseConnection.remove(clientSocket);
							keepAliveTimeOutTime.remove(clientSocket);
							
							// Log to console
							System.out.println("There is one removed server.");
							
							// Close connection
							break;
						}
					}
					// Exit connection if told to do so by the client
					if (inputLine.equalsIgnoreCase(".")) {
						break;
						
					// Register a new server
					} else if (inputLine.equalsIgnoreCase("new server")) {
						if (!newServerLock) {
							newServerLock = true;
							servers.incrementAndGet();
						}
						
					// Remove a server
					} else if (inputLine.equalsIgnoreCase("removed server")) {
						if (!removedServerLock && newServerLock) {
							removedServerLock = true;
							servers.decrementAndGet();
							
							// Unregister this client
							shouldCloseConnection.remove(clientSocket);
							keepAliveTimeOutTime.remove(clientSocket);
							
							// Close the connection
							break;
						}
			
					// Update keepalive status
					} else if (inputLine.equalsIgnoreCase("keep alive packet")) {
						keepAliveTimeOutTime.put(clientSocket, 100);
					} else if (inputLine.contains("player count packet ")) {
						System.out.println("Packet received");
						try {
							// Get the player count in the string
							int players = Integer.valueOf(inputLine.replace("player count packet ", ""));
							
							// Log it in the map
							playerCountMap.put(clientSocket, players);
							System.out.println("Packet received");
						} catch (NumberFormatException e) {
							continue;
						}
					}
					System.out.println(inputLine);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
		
		// Start the connection with the client on its own thread
		new Thread(runnable).start();
	}
}
