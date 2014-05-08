package org.mule.templates.integration;

import java.io.File;
import java.io.FileInputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;

import org.junit.Rule;
import org.mule.MessageExchangePattern;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.config.MuleProperties;
import org.mule.api.schedule.Scheduler;
import org.mule.api.schedule.Schedulers;
import org.mule.construct.Flow;
import org.mule.context.notification.NotificationException;
import org.mule.processor.chain.SubflowInterceptingChainLifecycleWrapper;
import org.mule.tck.junit4.FunctionalTestCase;
import org.mule.tck.junit4.rule.DynamicPort;
import org.mule.tck.probe.PollingProber;
import org.mule.tck.probe.Prober;
import org.mule.templates.test.utils.ListenerProbe;
import org.mule.templates.test.utils.PipelineSynchronizeListener;
import org.mule.transport.NullPayload;

/**
 * This is the base test class for Anypoint Templates integration tests.
 * 
 * @author damiansima
 */
public class AbstractTemplateTestCase extends FunctionalTestCase {
	private static final String MAPPINGS_FOLDER_PATH = "./mappings";
	private static final String TEST_FLOWS_FOLDER_PATH = "./src/test/resources/flows/";
	private static final String MULE_DEPLOY_PROPERTIES_PATH = "./src/main/app/mule-deploy.properties";

	protected static final int TIMEOUT_SEC = 120;
	protected static final String POLL_FLOW_A = "triggerSyncFromAFlow";
	protected static final String POLL_FLOW_B = "triggerSyncFromBFlow";
	protected static final String TEMPLATE_NAME = "user-broadcast";

	protected final Prober pollProberA = new PollingProber(60000, 1000l);
	protected final Prober pollProberB = new PollingProber(60000, 1000l);
	protected final PipelineSynchronizeListener pipelineListenerA = new PipelineSynchronizeListener(POLL_FLOW_A);
	protected final PipelineSynchronizeListener pipelineListenerB = new PipelineSynchronizeListener(POLL_FLOW_B);

	@Rule
	public DynamicPort port = new DynamicPort("http.port");

	protected void startFlowSchedulers(String flowName) throws MuleException
	{
		final Collection<Scheduler> schedulers = muleContext.getRegistry().lookupScheduler(
				Schedulers.flowConstructPollingSchedulers(flowName));
		for (final Scheduler scheduler : schedulers)
		{
			scheduler.start();
		}
	}

	@Override
	protected String getConfigResources() {
		String resources = "";
		try {
			Properties props = new Properties();
			props.load(new FileInputStream(MULE_DEPLOY_PROPERTIES_PATH));
			resources = props.getProperty("config.resources");
		}
		catch (Exception e) {
			throw new IllegalStateException(
					"Could not find mule-deploy.properties file on classpath. Please add any of those files or override the getConfigResources() method to provide the resources by your own.");
		}

		return resources + getTestFlows();
	}

	protected String getTestFlows() {
		StringBuilder resources = new StringBuilder();

		File testFlowsFolder = new File(TEST_FLOWS_FOLDER_PATH);
		File[] listOfFiles = testFlowsFolder.listFiles();
		if (listOfFiles != null) {
			for (File f : listOfFiles) {
				if (f.isFile() && f.getName()
						.endsWith("xml")) {
					resources.append(",")
							.append(TEST_FLOWS_FOLDER_PATH)
							.append(f.getName());
				}
			}
			return resources.toString();
		}
		else {
			return "";
		}
	}

	@Override
	protected Properties getStartUpProperties() {
		Properties properties = new Properties(super.getStartUpProperties());

		String pathToResource = MAPPINGS_FOLDER_PATH;
		File graphFile = new File(pathToResource);

		properties.put(MuleProperties.APP_HOME_DIRECTORY_PROPERTY, graphFile.getAbsolutePath());
		return properties;
	}

	protected void registerListeners() throws NotificationException {
		muleContext.registerListener(pipelineListenerA);
		muleContext.registerListener(pipelineListenerB);
	}

	protected void waitForPollAToRun() {
		System.err.println("Waiting for poll A to run ones...");
		pollProberA.check(new ListenerProbe(pipelineListenerA));
		System.err.println("Poll flow A done");
	}

	protected void waitForPollBToRun() {
		System.err.println("Waiting for B poll to run ones...");
		pollProberB.check(new ListenerProbe(pipelineListenerB));
		System.err.println("Poll flow B done");
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Object> invokeRetrieveFlow(SubflowInterceptingChainLifecycleWrapper flow, Map<String, Object> payload)
			throws Exception {
		MuleEvent event = flow.process(getTestEvent(payload, MessageExchangePattern.REQUEST_RESPONSE));
		Object resultPayload = event.getMessage()
				.getPayload();

		if (resultPayload instanceof NullPayload) {
			return null;
		}
		else {
			return (Map<String, Object>) resultPayload;
		}
	}

	protected Flow getFlow(String flowName) {
		return (Flow) muleContext.getRegistry().lookupObject(flowName);
	}

}
