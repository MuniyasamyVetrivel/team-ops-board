package com.teamops.support;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Records every SQL statement the application sends (JPA, native and {@code JdbcTemplate} alike) by wrapping the
 * {@link DataSource}. Import {@link Config} into an integration test, {@link #start()} before a request and read
 * {@link #statements()} after it. Used to keep endpoints free of N+1 queries.
 */
public final class SqlCounter {

	private static final List<String> STATEMENTS = Collections.synchronizedList(new ArrayList<>());

	/** The statements with their bound values filled in, ready for {@code EXPLAIN}. */
	private static final List<String> RENDERED = Collections.synchronizedList(new ArrayList<>());

	private static volatile boolean recording;

	private SqlCounter() {
	}

	public static void start() {
		STATEMENTS.clear();
		RENDERED.clear();
		recording = true;
	}

	public static List<String> stop() {
		recording = false;
		return statements();
	}

	public static List<String> statements() {
		synchronized (STATEMENTS) {
			return List.copyOf(STATEMENTS);
		}
	}

	public static List<String> rendered() {
		synchronized (RENDERED) {
			return List.copyOf(RENDERED);
		}
	}

	/** How often the most repeated statement ran: more than a handful in one request is an N+1 smell. */
	public static long maxRepeats(List<String> statements) {
		return statements.stream()
			.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
			.values()
			.stream()
			.mapToLong(Long::longValue)
			.max()
			.orElse(0);
	}

	public static Map<String, Long> repeated(List<String> statements, long atLeast) {
		return statements.stream()
			.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
			.entrySet()
			.stream()
			.filter(e -> e.getValue() >= atLeast)
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	private static void record(Object sql) {
		if (recording && sql instanceof String text) {
			STATEMENTS.add(text.replaceAll("\\s+", " ").trim());
		}
	}

	private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
		try {
			return method.invoke(target, args);
		}
		catch (InvocationTargetException ex) {
			throw ex.getTargetException();
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(T target, Class<T> type, InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(SqlCounter.class.getClassLoader(), new Class<?>[] { type }, handler);
	}

	private static String literal(Object value) {
		if (value == null) {
			return "NULL";
		}
		if (value instanceof Number) {
			return value.toString();
		}
		if (value instanceof Boolean flag) {
			return flag ? "1" : "0";
		}
		return "'" + value.toString().replace("'", "''") + "'";
	}

	private static String render(String sql, SortedMap<Integer, Object> values) {
		StringBuilder out = new StringBuilder();
		int index = 1;
		boolean quoted = false;
		for (char c : sql.toCharArray()) {
			if (c == '\'') {
				quoted = !quoted;
			}
			if (c == '?' && !quoted) {
				out.append(literal(values.get(index++)));
			}
			else {
				out.append(c);
			}
		}
		return out.toString();
	}

	private static PreparedStatement prepared(PreparedStatement target, String sql) {
		SortedMap<Integer, Object> values = new TreeMap<>();
		return proxy(target, PreparedStatement.class, (p, method, args) -> {
			String name = method.getName();
			if (name.startsWith("set") && args != null && args.length >= 2 && args[0] instanceof Integer index) {
				values.put(index, name.equals("setNull") ? null : args[1]);
			}
			else if (name.equals("clearParameters")) {
				values.clear();
			}
			else if ((name.equals("executeQuery") || name.equals("execute")) && (args == null || args.length == 0)
					&& recording) {
				RENDERED.add(render(sql, values));
			}
			return invoke(target, method, args);
		});
	}

	private static Statement statement(Statement target) {
		return proxy(target, Statement.class, (p, method, args) -> {
			if (method.getName().startsWith("execute") && args != null && args.length > 0) {
				record(args[0]);
			}
			return invoke(target, method, args);
		});
	}

	private static Connection connection(Connection target) {
		return proxy(target, Connection.class, (p, method, args) -> {
			String name = method.getName();
			if ((name.equals("prepareStatement") || name.equals("prepareCall")) && args != null) {
				record(args[0]);
			}
			Object result = invoke(target, method, args);
			if (name.equals("prepareStatement") && result instanceof PreparedStatement ps) {
				return prepared(ps, (String) args[0]);
			}
			return name.equals("createStatement") ? statement((Statement) result) : result;
		});
	}

	static DataSource dataSource(DataSource target) {
		return proxy(target, DataSource.class, (p, method, args) -> {
			Object result = invoke(target, method, args);
			return method.getName().equals("getConnection") ? connection((Connection) result) : result;
		});
	}

	@TestConfiguration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		static BeanPostProcessor sqlCountingDataSource() {
			return new BeanPostProcessor() {
				@Override
				public Object postProcessAfterInitialization(Object bean, String beanName) {
					return bean instanceof DataSource dataSource ? dataSource(dataSource) : bean;
				}
			};
		}

	}

}
