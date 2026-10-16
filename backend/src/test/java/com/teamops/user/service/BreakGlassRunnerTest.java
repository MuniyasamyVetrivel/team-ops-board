package com.teamops.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

import com.teamops.common.exception.ApiException;
import com.teamops.user.service.BreakGlassService.Operator;
import com.teamops.user.service.BreakGlassService.Result;

/**
 * The break-glass command line: never inside the web server, only with a person at a terminal who types the email
 * again and the password twice (never an argument), and it reports refusals instead of crashing.
 */
class BreakGlassRunnerTest {

	private static final Operator OPERATOR = new Operator("ops", "server-1");

	private final BreakGlassService service = mock(BreakGlassService.class);

	private final FakeTerminal terminal = new FakeTerminal();

	@Test
	void refusesToRunInsideTheWebApplication() {
		BreakGlassRunner runner = new BreakGlassRunner(service, mock(WebApplicationContext.class));
		assertThat(runner.execute(args("--break-glass=reset-password", "--email=a@b.c"), terminal, OPERATOR))
			.isEqualTo(2);
		verifyNoInteractions(service);
	}

	@Test
	void needsAnInteractiveTerminal() {
		assertThat(runner().execute(args("--break-glass=reset-password", "--email=a@b.c"), null, OPERATOR))
			.isEqualTo(2);
		verifyNoInteractions(service);
	}

	@Test
	void unknownCommandsAndMissingFieldsShowTheUsage() {
		assertThat(runner().execute(args("--break-glass=delete-everything", "--email=a@b.c"), terminal, OPERATOR))
			.isEqualTo(2);
		assertThat(runner().execute(args("--break-glass=create-super-admin", "--email=a@b.c"), terminal, OPERATOR))
			.isEqualTo(2);
		assertThat(terminal.output).anyMatch(line -> line.startsWith("Usage:"))
			.anyMatch(line -> line.contains("--employee-id and --first-name are required"));
		verifyNoInteractions(service);
	}

	@Test
	void theEmailMustBeTypedAgain() {
		terminal.lines.add("someone.else@b.c");
		assertThat(runner().execute(args("--break-glass=reset-password", "--email=rakesh@b.c"), terminal, OPERATOR))
			.isEqualTo(1);
		assertThat(terminal.output).contains("Cancelled: the email did not match.");
		verifyNoInteractions(service);
	}

	@Test
	void thePasswordMustBeTypedTwiceTheSame() {
		terminal.lines.add("rakesh@b.c");
		terminal.passwords.add("Recovery-2026-pass".toCharArray());
		terminal.passwords.add("Recovery-2026-typo".toCharArray());
		assertThat(runner().execute(args("--break-glass=reset-password", "--email=rakesh@b.c"), terminal, OPERATOR))
			.isEqualTo(1);
		verifyNoInteractions(service);
	}

	@Test
	void resetsASuperAdminPasswordTypedAtTheTerminal() {
		terminal.lines.add(" RAKESH@b.c ");
		terminal.passwords.add("Recovery-2026-pass".toCharArray());
		terminal.passwords.add("Recovery-2026-pass".toCharArray());
		when(service.resetSuperAdminPassword("rakesh@b.c", "Recovery-2026-pass", OPERATOR))
			.thenReturn(new Result(1L, "rakesh@b.c", true));

		assertThat(runner().execute(args("--break-glass=reset-password", "--email=rakesh@b.c"), terminal, OPERATOR))
			.isZero();
		assertThat(terminal.output).contains("Reset the password of rakesh@b.c and re-enabled the account.")
			.noneMatch(line -> line.contains("Recovery-2026-pass"));
	}

	@Test
	void createsASuperAdminWithAGeneratedPasswordShownOnce() {
		terminal.lines.add("it.admin@b.c");
		when(service.createSuperAdmin(eq("E-0002"), eq("it.admin@b.c"), eq("IT"), eq("Admin"), eq("OPS"), any(),
				eq(OPERATOR)))
			.thenReturn(new Result(5L, "it.admin@b.c", false));

		assertThat(runner().execute(args("--break-glass=create-super-admin", "--employee-id=E-0002", "--email=it.admin@b.c",
				"--first-name=IT",
				"--last-name=Admin", "--department=OPS", "--generate-password"), terminal, OPERATOR))
			.isZero();
		verify(service).createSuperAdmin(eq("E-0002"), eq("it.admin@b.c"), eq("IT"), eq("Admin"), eq("OPS"),
				argThat(p -> p.length() >= BreakGlassService.MIN_PASSWORD_LENGTH), eq(OPERATOR));
		assertThat(terminal.output).anyMatch(line -> line.startsWith("Temporary password (shown once): "));
		assertThat(terminal.passwordPrompts).isZero();
	}

	@Test
	void aRefusalIsReportedWithExitCodeOne() {
		terminal.lines.add("employee@b.c");
		when(service.resetSuperAdminPassword(eq("employee@b.c"), any(), eq(OPERATOR)))
			.thenThrow(ApiException.forbidden("NOT_SUPER_ADMIN", "Break-glass only resets Super Admin passwords"));
		assertThat(runner().execute(args("--break-glass=reset-password", "--email=employee@b.c", "--generate-password"),
				terminal, OPERATOR))
			.isEqualTo(1);
		assertThat(terminal.output).contains("Refused: Break-glass only resets Super Admin passwords");
	}

	private BreakGlassRunner runner() {
		return new BreakGlassRunner(service, mock(ApplicationContext.class));
	}

	private static DefaultApplicationArguments args(String... args) {
		return new DefaultApplicationArguments(args);
	}

	private static final class FakeTerminal implements BreakGlassRunner.Terminal {

		final Deque<String> lines = new ArrayDeque<>();

		final Deque<char[]> passwords = new ArrayDeque<>();

		final List<String> output = new ArrayList<>();

		int passwordPrompts;

		@Override
		public String readLine(String prompt) {
			return lines.poll();
		}

		@Override
		public char[] readPassword(String prompt) {
			passwordPrompts++;
			return passwords.poll();
		}

		@Override
		public void println(String line) {
			output.add(line);
		}

	}

}
