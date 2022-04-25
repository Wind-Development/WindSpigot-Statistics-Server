package ga.windpvp.statistics;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class Statistics {

	/**
	 * The amount of online WindSpigot servers there are
	 */
	public static AtomicInteger servers = new AtomicInteger(0);

	/**
	 * The total player count
	 */
	public static AtomicInteger players = new AtomicInteger(0);


	/**
	 * A list of statistic connections
	 */
	public static List<StatisticsConnection> connectionList = new CopyOnWriteArrayList<>();
	
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

		// Start the server on its own thread (We do this so that we can test the server
		// with clients)
		new Thread(statisticsRunnable).start();

		// Testing purposes (dummy clients)

		try {
			Thread.sleep(10);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}

		/*
		 * Runnable clientRunnable = (() -> { TestClient client = new TestClient(); try
		 * { client.startConnection("localhost", 500); client.sendMessage("new server");
		 * System.out.println(servers.get());
		 * 
		 * Runnable keepAliveRunnable = (() -> { while (true) { try {
		 * client.sendMessage("keep alive packet");
		 * System.out.println("Keep alive sent");
		 * client.sendMessage("player count packet 5".toLowerCase());
		 * System.out.println("Player count sent"); } catch (IOException e) {
		 * e.printStackTrace(); }
		 * 
		 * try { TimeUnit.SECONDS.sleep(30); } catch (InterruptedException e) {
		 * e.printStackTrace(); } } });
		 * 
		 * new Thread(keepAliveRunnable).start();
		 * 
		 * client.stopConnection(); } catch (IOException e) { e.printStackTrace(); } });
		 * 
		 * new Thread(clientRunnable).start(); new Thread(clientRunnable).start(); new
		 * Thread(clientRunnable).start();
		 */

	}

	

	private void run() throws IOException {
		serverSocket = new ServerSocket(500);

		Logger.log("Started WindSpigot statistics server.");

		new TaskManager().initTasks();

		// Handle new connections on its own thread so the server can process multiple
		// clients
		while (true) {
			this.startConnection(serverSocket.accept());
		}
	}

	private void startConnection(Socket clientSocket) {
		StatisticsConnection connection = new StatisticsConnection();
		connection.startConnection(clientSocket);
		connectionList.add(connection);
	}
}
