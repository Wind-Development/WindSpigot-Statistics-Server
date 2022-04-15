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

		/*
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
		*/
	}

	private ServerSocket serverSocket;

	private static AtomicInteger servers = new AtomicInteger(0);;

	private void run() throws IOException {
		serverSocket = new ServerSocket(500);
		Scanner scanner = new Scanner(System.in);
		
		System.out.println("Started WindSpigot statistics server.");

		// Scan for console input
		Runnable consoleRunnable = (() -> {
			System.out.println("Initialized console scanner.");
			while (true) {
				String command = scanner.nextLine();
				if (command.equalsIgnoreCase("servers")) {
					System.out.println("There are " + servers.get() + " servers running WindSpigot.");
				} else if (command.equalsIgnoreCase("stop")) {
					System.out.println("Stopping...");
					scanner.close();
					System.exit(0);
				}
			}
		});

		new Thread(consoleRunnable).start();
		
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
		
		new Thread(repeatingDisplayRunnable).start();

		// Handle new connections on its own thread 
		while (true) {
			this.startConnection(serverSocket.accept());
		}
	}
	
	ConcurrentMap<Socket, Integer> keepAliveTimeOutTime = new ConcurrentHashMap<>();
	ConcurrentMap<Socket, Boolean> shouldCloseConnection = new ConcurrentHashMap<>();


	public void startConnection(Socket clientSocket) {
		// Initializes a new connection from a client
		Runnable runnable = (() -> {
			BufferedReader in;
			try {
				System.out.println("New connection established.");
				in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

				String inputLine;
				
				Runnable keepAliveRunnable = (() -> {
					
					shouldCloseConnection.put(clientSocket, false);
					keepAliveTimeOutTime.put(clientSocket, 100);
					
					while (true) {
						try {
							TimeUnit.SECONDS.sleep(1);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						if (keepAliveTimeOutTime.get(clientSocket) != null) {
							
							if (keepAliveTimeOutTime.get(clientSocket) == 0) {
								shouldCloseConnection.put(clientSocket, true);
								servers.decrementAndGet();
								break;
							}
							
							keepAliveTimeOutTime.put(clientSocket, keepAliveTimeOutTime.get(clientSocket) - 1);
						}
					}
				});
				
				new Thread(keepAliveRunnable).start();

				while ((inputLine = in.readLine()) != null) {
					// Exit connection
					if (shouldCloseConnection.get(clientSocket) != null) {
						if (shouldCloseConnection.get(clientSocket)) {
							
							shouldCloseConnection.remove(clientSocket);
							keepAliveTimeOutTime.remove(clientSocket);
							
							System.out.println("There is one removed server.");
							
							break;
						}
					}
					if (inputLine.equalsIgnoreCase(".")) {
						break;
						
						// Register a new server
					} else if (inputLine.equalsIgnoreCase("new server")) {
						servers.incrementAndGet();
						
						// Remove a server
					} else if (inputLine.equalsIgnoreCase("removed server")) {
						servers.decrementAndGet();
						shouldCloseConnection.remove(clientSocket);
						keepAliveTimeOutTime.remove(clientSocket);
						break;
						
						// Remove servers that are not sending keep alives
					} else if (inputLine.equalsIgnoreCase("keep alive packet")) {
						keepAliveTimeOutTime.put(clientSocket, 100);
					}
					System.out.println("There is one " + inputLine + ".");
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
		new Thread(runnable).start();
	}
}
