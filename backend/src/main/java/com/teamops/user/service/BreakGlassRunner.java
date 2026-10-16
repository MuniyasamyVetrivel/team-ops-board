package com.teamops.user.service;

import java.io.Console;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import com.teamops.common.exception.ApiException;
import com.teamops.user.service.BreakGlassService.Operator;
import com.teamops.user.service.BreakGlassService.Result;

import lombok.RequiredArgsConstructor;

/**
 * The break-glass command-line tool (scripts/break-glass.ps1 and .sh start it). It runs only when the backend jar is
 * started with {@code --break-glass=<command>} and without the web server, needs a person at an interactive terminal
 * on the server (the password is typed there, never passed as an argument), asks for the email to be typed again,
 * does one thing and exits.
 *
 * <pre>
 * --break-glass=create-super-admin --employee-id=... --email=... --first-name=... [--last-name=...] [--department=IT]
 *                                  [--generate-password]
 * --break-glass=reset-password     --email=...                                                  [--generate-password]
 * </pre>
 */
@Component
@Order(20)
@ConditionalOnProperty(name = "break-glass")
@RequiredArgsConstructor
class BreakGlassRunner implements ApplicationRunner {

	static final String CREATE = "create-super-admin";

	static final String RESET = "reset-password";

	private final BreakGlassService breakGlass;

	private final ApplicationContext context;

	/** The person at the server's terminal. */
	interface Terminal {

		String readLine(String prompt);

		char[] readPassword(String prompt);

		void println(String line);

		/** The JVM's console, or null when there is no interactive terminal (a service, a pipe, a remote call). */
		static Terminal system() {
			Console console = System.console();
			if (console == null) {
				return null;
			}
			return new Terminal() {
				@Override
				public String readLine(String prompt) {
					return console.readLine("%s", prompt);
				}

				@Override
				public char[] readPassword(String prompt) {
					return console.readPassword("%s", prompt);
				}

				@Override
				public void println(String line) {
					console.writer().println(line);
					console.flush();
				}
			};
		}

	}

	@Override
	public void run(ApplicationArguments args) {
		int code = execute(args, Terminal.system(), operator());
		System.exit(SpringApplication.exit((ConfigurableApplicationContext) context, () -> code));
	}

	/** Returns the process exit code: 0 done, 1 refused or failed, 2 wrong usage or environment. */
	int execute(ApplicationArguments args, Terminal terminal, Operator operator) {
		if (context instanceof WebServerApplicationContext || context instanceof WebApplicationContext) {
			System.err.println("Break-glass refuses to run inside the web server. Use scripts/break-glass.ps1 or "
					+ "scripts/break-glass.sh on the server.");
			return 2;
		}
		if (terminal == null) {
			System.err.println("Break-glass needs a person at an interactive terminal on the server.");
			return 2;
		}
		String command = option(args, "break-glass");
		String email = option(args, "email");
		if ((!CREATE.equals(command) && !RESET.equals(command)) || email == null) {
			terminal.println("Usage: --break-glass=" + CREATE + " --employee-id=... --email=... --first-name=... "
					+ "[--last-name=...] [--department=IT] [--generate-password]");
			terminal.println("       --break-glass=" + RESET + " --email=... [--generate-password]");
			return 2;
		}
		String firstName = option(args, "first-name");
		String employeeId = option(args, "employee-id");
		if (CREATE.equals(command) && (firstName == null || employeeId == null)) {
			terminal.println("--employee-id and --first-name are required to create a Super Admin.");
			return 2;
		}

		terminal.println("");
		terminal.println("BREAK-GLASS: " + (CREATE.equals(command) ? "create a new Super Admin " + email
				: "reset the password of Super Admin " + email + " (and re-enable the account if disabled)"));
		terminal.println("This is recorded in the audit log as " + operator.osUser() + "@" + operator.host() + ".");
		String confirm = terminal.readLine("Type the email again to continue: ");
		if (confirm == null || !confirm.trim().equalsIgnoreCase(email.trim())) {
			terminal.println("Cancelled: the email did not match.");
			return 1;
		}

		boolean generate = args.containsOption("generate-password");
		String password;
		if (generate) {
			password = TemporaryPasswords.generate();
		}
		else {
			char[] first = terminal.readPassword("Temporary password (" + BreakGlassService.MIN_PASSWORD_LENGTH
					+ "+ characters, letters and numbers): ");
			char[] second = terminal.readPassword("Type it again: ");
			if (first == null || second == null || !Arrays.equals(first, second)) {
				terminal.println("Cancelled: the passwords did not match.");
				return 1;
			}
			password = new String(first);
			Arrays.fill(first, ' ');
			Arrays.fill(second, ' ');
		}

		try {
			Result result = CREATE.equals(command)
					? breakGlass.createSuperAdmin(employeeId, email, firstName, option(args, "last-name"),
							option(args, "department"), password, operator)
					: breakGlass.resetSuperAdminPassword(email, password, operator);
			terminal.println("");
			terminal.println((CREATE.equals(command) ? "Created Super Admin " : "Reset the password of ")
					+ result.email() + (result.reEnabled() ? " and re-enabled the account" : "") + ".");
			if (generate) {
				terminal.println("Temporary password (shown once): " + password);
			}
			terminal.println("It must be changed at the next sign-in. Every session of this account was signed out.");
			terminal.println("Recorded in the audit log (BREAK_GLASS_USED). Follow the checklist in "
					+ "docs/backup-and-restore.md.");
			return 0;
		}
		catch (ApiException ex) {
			terminal.println("Refused: " + ex.getMessage());
			return 1;
		}
	}

	private static String option(ApplicationArguments args, String name) {
		List<String> values = args.getOptionValues(name);
		if (values == null || values.isEmpty() || values.get(0) == null || values.get(0).isBlank()) {
			return null;
		}
		return values.get(0).trim();
	}

	private static Operator operator() {
		String user = System.getProperty("user.name", "unknown");
		String host;
		try {
			host = InetAddress.getLocalHost().getHostName();
		}
		catch (Exception ex) {
			host = System.getenv().getOrDefault("COMPUTERNAME", System.getenv().getOrDefault("HOSTNAME", "unknown"));
		}
		return new Operator(user, host);
	}

}
