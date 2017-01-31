package com.sos.jitl.reporting.plugin;

import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sos.exception.InvalidDataException;
import com.sos.exception.NoResponseException;
import com.sos.scheduler.engine.kernel.plugin.AbstractPlugin;
import com.sos.scheduler.engine.kernel.scheduler.SchedulerXmlCommandExecutor;
import com.sos.scheduler.engine.kernel.variable.VariableSet;

import sos.util.SOSString;
import sos.xml.SOSXMLXPath;

public class ReportingPlugin extends AbstractPlugin {

	private static final Logger LOGGER = LoggerFactory.getLogger(ReportingPlugin.class);

	public static final String DUMMY_COMMAND = "<show_state subsystems=\"folder\" what=\"folders cluster no_subfolders\" path=\"/any/path/that/does/not/exists\" />";
	public static final String HIBERNATE_DEFAULT_FILE_NAME_SCHEDULER = "hibernate.cfg.xml";
	public static final String HIBERNATE_DEFAULT_FILE_NAME_REPORTING = "reporting.hibernate.cfg.xml";

	public static final String SCHEDULER_PARAM_PROXY_URL = "sos.proxy_url";
	public static final String SCHEDULER_PARAM_HIBERNATE_SCHEDULER = "sos.hibernate_configuration_scheduler";
	public static final String SCHEDULER_PARAM_HIBERNATE_REPORTING = "sos.hibernate_configuration_reporting";

	private SchedulerXmlCommandExecutor xmlCommandExecutor;
	private VariableSet variableSet;

	private ExecutorService threadPool = Executors.newFixedThreadPool(1);
	private IReportingEventHandler eventHandler;
	private PluginSettings settings;

	private String schedulerParamProxyUrl;
	private String schedulerParamHibernateScheduler;
	private String schedulerParamHibernateReporting;

	private ThreadLocal<Boolean> hasErrorOnPrepare = new ThreadLocal<>();

	public ReportingPlugin(SchedulerXmlCommandExecutor executor, VariableSet variables) {
		this.xmlCommandExecutor = executor;
		this.variableSet = variables;
	}

	public void executeOnPrepare(IReportingEventHandler handler) {
		String method = "executeOnPrepare";

		eventHandler = handler;
		readSchedulerVariables();

		Runnable thread = new Runnable() {
			@Override
			public void run() {
				try {
					init();
					eventHandler.onPrepare(xmlCommandExecutor, variableSet, settings);
					hasErrorOnPrepare.set(false);
				} catch (Exception e) {
					LOGGER.error(String.format("%s: %s", method, e.toString()), e);
					hasErrorOnPrepare.set(true);
				}
			}
		};
		threadPool.submit(thread);

		super.onPrepare();
	}

	public void executeOnActivate() {
		String method = "executeOnActivate";

		Runnable thread = new Runnable() {
			@Override
			public void run() {
				try {
					if (hasErrorOnPrepare != null && hasErrorOnPrepare.get()) {
						LOGGER.warn(String.format("%s: skip due executeOnPrepare errors", method));
					} else {
						eventHandler.onActivate();
					}
				} catch (Exception e) {
					LOGGER.error(String.format("%s: %s", method, e.toString()), e);
				}
			}
		};
		threadPool.submit(thread);

		super.onActivate();
	}

	public void executeClose() {
		String method = "executeClose";

		eventHandler.close();
		try {
			threadPool.shutdownNow();
			boolean shutdown = threadPool.awaitTermination(1L, TimeUnit.SECONDS);
			if (shutdown) {
				LOGGER.debug(String.format("%s: thread has been shut down correctly", method));
			} else {
				LOGGER.debug(String.format(
						"%s: thread has ended due to timeout on shutdown. doesn�t wait for answer from thread",
						method));
			}
		} catch (InterruptedException e) {
			LOGGER.error(String.format("%s: %s", method, e.toString()), e);
		}
		super.close();
	}

	private void init() throws Exception {
		String method = "init";
		settings = new PluginSettings();
		for (int i = 0; i < 120; i++) {
			try {
				Thread.sleep(1000);
				settings.setSchedulerAnswerXml(executeXML(DUMMY_COMMAND));
				if (!SOSString.isEmpty(settings.getSchedulerAnswerXml())) {
					SOSXMLXPath xpath = new SOSXMLXPath(new StringBuffer(settings.getSchedulerAnswerXml()));
					settings.setSchedulerAnswerXpath(xpath);
					String state = getSchedulerAnswer("/spooler/answer/state/@state");
					if ("running,waiting_for_activation,paused".contains(state)) {
						break;
					}
				}
			} catch (Exception e) {
				LOGGER.error(String.format("%s: %s", method, e.toString()), e);
			}
		}
		if (SOSString.isEmpty(settings.getSchedulerAnswerXml())) {
			throw new NoResponseException(String.format("%s: missing JobScheduler answer", method));
		}
		LOGGER.debug(String.format("%s: xml=%s", method, settings.getSchedulerAnswerXml()));

		settings.setSchedulerXml(Paths.get(getSchedulerAnswer("/spooler/answer/state/@config_file")));
		if (settings.getSchedulerXml() == null || !Files.exists(settings.getSchedulerXml())) {
			throw new FileNotFoundException(
					String.format("not found settings.xml file %s", settings.getSchedulerXml()));
		}
		settings.setConfigDirectory(settings.getSchedulerXml().getParent());

		settings.setHibernateConfigurationScheduler(getHibernateConfigurationScheduler(settings.getConfigDirectory()));
		settings.setHibernateConfigurationReporting(getHibernateConfigurationReporting(settings.getConfigDirectory(),
				settings.getHibernateConfigurationScheduler()));

		LOGGER.debug(String.format("%s: hibernateScheduler=%s, hibernateReporting=%s", method,
				settings.getHibernateConfigurationScheduler(), settings.getHibernateConfigurationReporting()));

		settings.setLiveDirectory(settings.getConfigDirectory().resolve("live"));
		settings.setSchedulerId(getSchedulerAnswer("/spooler/answer/state/@spooler_id"));
		settings.setHostname(getSchedulerAnswer("/spooler/answer/state/@host"));
		settings.setTimezone(getSchedulerAnswer("/spooler/answer/state/@time_zone"));
		settings.setHttpPort(getSchedulerAnswer("/spooler/answer/state/@http_port", "40444"));

		if (SOSString.isEmpty(settings.getSchedulerId())) {
			throw new Exception(String.format("%s: missing @spooler_id in the scheduler answer", method));
		}
		if (SOSString.isEmpty(settings.getHostname())) {
			throw new Exception(String.format("%s: missing @host in the scheduler answer", method));
		}
		if (SOSString.isEmpty(settings.getHttpPort())) {
			throw new Exception(String.format("%s: missing @http_port in the scheduler answer", method));
		}
		try {
			settings.setMasterUrl(getMasterUrl(settings.getSchedulerAnswerXpath()));
		} catch (Exception e) {
			throw new InvalidDataException(
					String.format("%s: couldn't determine JobScheduler http url %s", method, e.toString()), e);
		}
	}

	private String getSchedulerAnswer(String xpath) throws Exception {
		return getSchedulerAnswer(xpath, null);
	}

	private String getSchedulerAnswer(String xpath, String defaultValue) throws Exception {
		if (defaultValue == null) {
			return settings.getSchedulerAnswerXpath().selectSingleNodeValue(xpath);
		} else {
			return settings.getSchedulerAnswerXpath().selectSingleNodeValue(xpath, defaultValue);
		}
	}

	private Path getHibernateConfigurationScheduler(Path configDirectory) throws Exception {
		String method = "getHibernateConfigurationScheduler";
		Path file = null;
		if (SOSString.isEmpty(this.schedulerParamHibernateScheduler)) {
			LOGGER.debug(
					String.format("%s: not found scheduler variable '%s'. search for default schedulerHibernate %s",
							method, SCHEDULER_PARAM_HIBERNATE_SCHEDULER, HIBERNATE_DEFAULT_FILE_NAME_SCHEDULER));
			file = configDirectory.resolve(HIBERNATE_DEFAULT_FILE_NAME_SCHEDULER);
		} else {
			LOGGER.debug(String.format("%s: found scheduler variable '%s'=%s", method,
					SCHEDULER_PARAM_HIBERNATE_SCHEDULER, this.schedulerParamHibernateScheduler));
			file = Paths.get(this.schedulerParamHibernateScheduler);
		}
		if (!Files.exists(file)) {
			throw new FileNotFoundException(
					String.format("%s: not found hibernateScheduler file %s", method, file.toString()));
		}
		return file;
	}

	private Path getHibernateConfigurationReporting(Path configDirectory, Path hibernateScheduler) throws Exception {
		String method = "getHibernateConfigurationReporting";

		Path file = null;
		if (SOSString.isEmpty(this.schedulerParamHibernateReporting)) {
			LOGGER.debug(
					String.format("%s: not found scheduler variable '%s'. search for default reportingHibernate %s",
							method, SCHEDULER_PARAM_HIBERNATE_REPORTING, HIBERNATE_DEFAULT_FILE_NAME_REPORTING));
			file = configDirectory.resolve(HIBERNATE_DEFAULT_FILE_NAME_REPORTING);

			if (!Files.exists(file)) {
				LOGGER.debug(String.format(
						"%s: not foud default reportingHibernate %s. set reportingHibernate = schedulerHibernate",
						method, SCHEDULER_PARAM_HIBERNATE_REPORTING));
				file = hibernateScheduler;
			}
		} else {
			LOGGER.debug(String.format("%s: found scheduler variable '%s'=%s", method,
					SCHEDULER_PARAM_HIBERNATE_REPORTING, this.schedulerParamHibernateReporting));
			file = Paths.get(this.schedulerParamHibernateReporting);

			if (!Files.exists(file)) {
				throw new FileNotFoundException(
						String.format("%s: not found hibernateReporting file %s", method, file.toString()));
			}
		}
		return file;
	}

	private String executeXML(String xmlCommand) {
		if (xmlCommandExecutor != null) {
			return xmlCommandExecutor.executeXml(xmlCommand);
		} else {
			LOGGER.error("xmlCommandExecutor is null");
		}
		return null;
	}

	private void readSchedulerVariables() {
		this.schedulerParamProxyUrl = getSchedulerVariable(SCHEDULER_PARAM_PROXY_URL);
		this.schedulerParamHibernateScheduler = getSchedulerVariable(SCHEDULER_PARAM_HIBERNATE_SCHEDULER);
		this.schedulerParamHibernateReporting = getSchedulerVariable(SCHEDULER_PARAM_HIBERNATE_REPORTING);
	}

	private String getSchedulerVariable(String name) {
		if (variableSet.apply(name) != null && !variableSet.apply(name).isEmpty()) {
			return variableSet.apply(name);
		}
		return null;
	}

	private String getMasterUrl(SOSXMLXPath xPath) throws Exception {
		if (schedulerParamProxyUrl != null) {
			return schedulerParamProxyUrl;
		}

		StringBuilder sb = new StringBuilder();
		sb.append("http://");
		sb.append(InetAddress.getLocalHost().getCanonicalHostName().toLowerCase());
		sb.append(":");
		sb.append(settings.getHttpPort());
		return sb.toString();
	}
}