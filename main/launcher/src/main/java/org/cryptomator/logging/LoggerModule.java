package org.cryptomator.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.helpers.NOPAppender;
import ch.qos.logback.core.hook.DelayingShutdownHook;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.util.Duration;
import dagger.Module;
import dagger.Provides;
import org.cryptomator.common.Environment;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.nio.file.Path;

@Module
public class LoggerModule {

	private static final String UPGRADE_FILENAME = "upgrade.log";
	private static final String LOGFILE_NAME = "cryptomator0.log";
	private static final String LOGFILE_ROLLING_PATTERN = "cryptomator%i.log";
	private static final int LOGFILE_ROLLING_MIN = 1;
	private static final int LOGFILE_ROLLING_MAX = 9;
	private static final double SHUTDOWN_DELAY_MS = 100;
	private static final Level ROOT_LOG_LEVEL = Level.INFO;
	private static final String LOG_PATTERN = "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n";

	@Provides
	@Singleton
	LoggerContext provideLoggerContext() {
		ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
		if (loggerFactory instanceof LoggerContext) {
			return (LoggerContext) loggerFactory;
		} else {
			throw new IllegalStateException("SLF4J not bound to Logback.");
		}
	}

	@Provides
	@Singleton
	PatternLayoutEncoder provideLayoutEncoder(LoggerContext context) {
		PatternLayoutEncoder ple = new PatternLayoutEncoder();
		ple.setPattern(LOG_PATTERN);
		ple.setContext(context);
		ple.start();
		return ple;
	}

	@Provides
	@Singleton
	@Named("stdoutAppender")
	Appender<ILoggingEvent> provideStdoutAppender(LoggerContext context, PatternLayoutEncoder encoder) {
		ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<>();
		appender.setContext(context);
		appender.setEncoder(encoder);
		appender.start();
		return appender;
	}

	@Provides
	@Singleton
	@Named("fileAppender")
	Appender<ILoggingEvent> provideFileAppender(LoggerContext context, PatternLayoutEncoder encoder, Environment environment) {
		if (environment.getLogDir().isPresent()) {
			Path logDir = environment.getLogDir().get();
			RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
			appender.setContext(context);
			appender.setFile(logDir.resolve(LOGFILE_NAME).toString());
			appender.setEncoder(encoder);
			LaunchBasedTriggeringPolicy triggeringPolicy = new LaunchBasedTriggeringPolicy();
			triggeringPolicy.setContext(context);
			triggeringPolicy.start();
			appender.setTriggeringPolicy(triggeringPolicy);
			FixedWindowRollingPolicy rollingPolicy = new FixedWindowRollingPolicy();
			rollingPolicy.setContext(context);
			rollingPolicy.setFileNamePattern(logDir.resolve(LOGFILE_ROLLING_PATTERN).toString());
			rollingPolicy.setMinIndex(LOGFILE_ROLLING_MIN);
			rollingPolicy.setMaxIndex(LOGFILE_ROLLING_MAX);
			rollingPolicy.setParent(appender);
			rollingPolicy.start();
			appender.setRollingPolicy(rollingPolicy);
			appender.start();
			return appender;
		} else {
			NOPAppender appender = new NOPAppender<>();
			appender.setContext(context);
			return appender;
		}
	}

	@Provides
	@Singleton
	@Named("upgradeAppender")
	Appender<ILoggingEvent> provideUpgradeAppender(LoggerContext context, PatternLayoutEncoder encoder, Environment environment) {
		if (environment.getLogDir().isPresent()) {
			FileAppender<ILoggingEvent> appender = new FileAppender<>();
			appender.setFile(environment.getLogDir().get().resolve(UPGRADE_FILENAME).toString());
			appender.setContext(context);
			appender.setEncoder(encoder);
			appender.start();
			return appender;
		} else {
			NOPAppender appender = new NOPAppender<>();
			appender.setContext(context);
			return appender;
		}
	}

	@Provides
	@Singleton
	@Named("initLogging")
	Runnable provideLogbackInitializer(LoggerContext context, //
										 @Named("stdoutAppender") Appender<ILoggingEvent> stdout, //
										 @Named("upgradeAppender") Appender<ILoggingEvent> upgrade, //
										 @Named("fileAppender") Appender<ILoggingEvent> file) {
		return () -> {
			context.reset();

			// configure root logger:
			Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
			root.setLevel(ROOT_LOG_LEVEL);
			root.addAppender(stdout);
			root.addAppender(file);

			// configure root logger:
			Logger uprades = context.getLogger("org.cryptomator.ui.model");
			uprades.setLevel(Level.DEBUG);
			uprades.addAppender(stdout);
			uprades.addAppender(upgrade);

			// add shutdown hook
			DelayingShutdownHook shutdownHook = new DelayingShutdownHook();
			shutdownHook.setContext(context);
			shutdownHook.setDelay(Duration.buildByMilliseconds(SHUTDOWN_DELAY_MS));
		};
	}


}
