package org.mule.templates.integration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mule.MessageExchangePattern;
import org.mule.api.MuleEvent;
import org.mule.processor.chain.SubflowInterceptingChainLifecycleWrapper;
import org.mule.tck.junit4.rule.DynamicPort;
import org.mule.templates.builders.ObjectBuilder;

import com.mulesoft.module.batch.BatchTestHelper;

/**
 * The objective of this class is to validate the correct behavior of the flows for this Mule Template that make calls to external systems.
 */
public class BusinessLogicIntegrationTest extends AbstractTemplateTestCase {

	protected static final int TIMEOUT = 60;
	private static final Logger log = Logger.getLogger(BusinessLogicIntegrationTest.class);
	private static final String POLL_FLOW_NAME = "triggerFlow";
	private BatchTestHelper helper;
	Map<String, Object> caseA = null;
	Map<String, Object> caseB = null;

	@Rule
	public DynamicPort port = new DynamicPort("http.port");

	@BeforeClass
	public static void init() {
		System.setProperty("page.size", "1000");
		System.setProperty("poll.frequencyMillis", "10000");
		System.setProperty("poll.startDelayMillis", "20000");
		System.setProperty("watermark.default.expression",
				"#[groovy: new Date(System.currentTimeMillis() - 10000).format(\"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'\", TimeZone.getTimeZone('UTC'))]");
	}

	@Before
	public void setUp() throws Exception {
		stopFlowSchedulers(POLL_FLOW_NAME);
		registerListeners();
		helper = new BatchTestHelper(muleContext);

		createCases();
	}

	@After
	public void tearDown() throws Exception {
		stopFlowSchedulers(POLL_FLOW_NAME);
		deleteCases();
	}

	@Test
	public void testMainFlow() throws Exception {
		// Run poll and wait for it to run
		runSchedulersOnce(POLL_FLOW_NAME);
		waitForPollToRun();

		// Wait for the batch job executed by the poll flow to finish
		helper.awaitJobTermination(TIMEOUT_SEC * 1000, 500);
		helper.assertJobWasSuccessful();

		// Prepare payload
		final Map<String, Object> userToRetrieveMail = new HashMap<String, Object>();
		log.info("userToRetrieveMail: " + userToRetrieveMail);

		// Execute selectUserFromDB sublow
		SubflowInterceptingChainLifecycleWrapper selectUserFromDBFlow = getSubFlow("selectUserFromDB");
		final MuleEvent event = selectUserFromDBFlow.process(getTestEvent(userToRetrieveMail, MessageExchangePattern.REQUEST_RESPONSE));
		final List<Map<String, Object>> payload = (List<Map<String, Object>>) event.getMessage().getPayload();

		// print result
		for (Map<String, Object> usr : payload)
			log.info("selectUserFromDB response: " + usr);

		// User previously created in Salesforce should be present in database
		Assert.assertEquals("The user should have been sync", 1, payload.size());
	}

	private void createCases() throws Exception {
		SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("createCaseInAFlow");
		flow.initialise();
		caseA = createCase();

		MuleEvent event = flow.process(getTestEvent(caseA, MessageExchangePattern.REQUEST_RESPONSE));
		Object result = event.getMessage().getPayload();
		log.info("createCaseInAFlow result: " + result);

		flow = getSubFlow("createCaseInBFlow");
		flow.initialise();
		caseB = createCase();

		event = flow.process(getTestEvent(caseA, MessageExchangePattern.REQUEST_RESPONSE));
		result = event.getMessage().getPayload();
		log.info("createCaseInBFlow result: " + result);
	}

	private void deleteCases() throws Exception {
		SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("deleteCaseFromAFlow");
		flow.initialise();

		MuleEvent event = flow.process(getTestEvent(caseA, MessageExchangePattern.REQUEST_RESPONSE));
		Object result = event.getMessage().getPayload();
		log.info("deleteCaseFromAFlow result: " + result);

		event = flow.process(getTestEvent(caseA, MessageExchangePattern.REQUEST_RESPONSE));
		result = event.getMessage().getPayload();
		log.info("createCaseInBFlow result: " + result);
	}

	private Map<String, Object> createCase() {
		String name = buildUniqueString(8);
		return ObjectBuilder.aCase()
				// .with("Id", name)
				.with("Subject", name)
				.with("Description", name)
				.with("Priority", "Low")
				.with("Status", "New")
				.with("Origin", "Phone")
				.with("AccountId", null)
				.with("ContactId", null)
				.with("ExtId__c", null)
				.build();
	}

	private Map<String, Object> createCase__c() {
		String name = buildUniqueString(8);
		return ObjectBuilder.aCase()
				// .with("Id", name)
				.with("Subject__c", name)
				.with("Description__c", name)
				.with("Priority__c", "Low")
				.with("Status__c", "New")
				.with("Origin__c", "Phone")
				.with("Account__c", null)
				.with("Contact__c", null)
				.with("CaseId__c", null)
				.build();
	}

	private String buildUniqueString(int length) {
		String name = RandomStringUtils.randomAlphabetic(length).toLowerCase();
		return name;
	}

}
