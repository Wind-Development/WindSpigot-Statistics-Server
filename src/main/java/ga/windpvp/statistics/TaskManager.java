package ga.windpvp.statistics;

import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class TaskManager {
	
	public void initTasks() {
		runConsoleInputTask();
		runConsoleLogTask();
		runPlayerCountUpdateTask();
	}
	
	private void runConsoleInputTask() {
		// Scans for console input
		Runnable consoleRunnable = (() -> {
			// Input scanner
			Scanner scanner = new Scanner(System.in);

			// Log to console
			Logger.log("Initialized console scanner.");
			// Continuously scan
			while (true) {
				// The input command string
				String command = scanner.nextLine();

				// Handle command
				if (command.equalsIgnoreCase("servers")) {
					Logger.log("There are " + Statistics.servers.get() + " servers running WindSpigot.");
				} else if (command.equalsIgnoreCase("stop")) {
					Logger.log("Stopping...");
					scanner.close();
					System.exit(0);
				} else if (command.equalsIgnoreCase("players")) {
					Logger.log("There are " + Statistics.players.get() + " players on WindSpigot servers.");
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
				Logger.log("There are " + Statistics.servers.get() + " servers running WindSpigot.");
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
				Statistics.players.set(0);

				// Update the player count
				for (StatisticsConnection connection : Statistics.connectionList) {
					Statistics.players.addAndGet(connection.players);
				}
				
			}
		});

		// Start Updating
		new Thread(repeatingPlayerCountRunnable).start();
	}
}
