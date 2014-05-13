package org.mule.templates.integration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mule.MessageExchangePattern;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.processor.chain.InterceptingChainLifecycleWrapper;
import org.mule.processor.chain.SubflowInterceptingChainLifecycleWrapper;
import org.mule.templates.builders.ObjectBuilder;
import org.mule.transport.NullPayload;

import com.mulesoft.module.batch.BatchTestHelper;
import com.sforce.soap.partner.SaveResult;

/**
 * The objective of this class is to validate the correct behavior of the flows for this Mule Template that make calls to external systems.
 */
public class BusinessLogicIntegrationTest extends AbstractTemplateTestCase {

	private static final Logger log = Logger.getLogger(BusinessLogicIntegrationTest.class);

	private static final String A_INBOUND_FLOW_NAME = "triggerSyncFromAFlow";
	private static final String B_INBOUND_FLOW_NAME = "triggerSyncFromBFlow";
	private static final int TIMEOUT_MILLIS = 60;

	private BatchTestHelper batchTestHelper;

	Map<String, Object> caseA = null;
	Map<String, Object> caseB = null;

	@BeforeClass
	public static void beforeTestClass() {
		System.setProperty("mule.env", "test");
		System.setProperty("page.size", "1000");

		// Set polling frequency to 10 seconds
		System.setProperty("polling.frequency", "10000");

		// Set default water-mark expression to current time
		System.clearProperty("watermark.default.expression");
		DateTime now = new DateTime(DateTimeZone.UTC);
		// now = now.minus(1000 * 60 * 10);
		DateTimeFormatter dateFormat = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		System.setProperty("watermark.default.expression", now.toString(dateFormat));
	}

	@Before
	public void setUp() throws Exception {
		stopAutomaticPollTriggering();
		batchTestHelper = new BatchTestHelper(muleContext);
	}

	@After
	public void tearDown() throws Exception {
	}

	private void stopAutomaticPollTriggering() throws MuleException {
		stopFlowSchedulers(A_INBOUND_FLOW_NAME);
		stopFlowSchedulers(B_INBOUND_FLOW_NAME);
	}

	private void executeWaitAndAssertBatchJob(String flowConstructName) throws Exception {
		// Execute synchronization
		runSchedulersOnce(flowConstructName);

		// Wait for the batch job execution to finish
		batchTestHelper.awaitJobTermination(TIMEOUT_MILLIS * 1000, 500);
		batchTestHelper.assertJobWasSuccessful();
	}

	protected Map<String, Object> invokeRetrieveFlow(InterceptingChainLifecycleWrapper flow, Map<String, Object> payload) throws Exception {
		MuleEvent event = flow.process(getTestEvent(payload, MessageExchangePattern.REQUEST_RESPONSE));
		Object resultPayload = event.getMessage().getPayload();
		return resultPayload instanceof NullPayload ? null : (Map<String, Object>) resultPayload;
	}

	@Test
	@SuppressWarnings({ "unchecked" })
	public void testPollA() throws Exception {
		System.err.println("mainflow A");
		createCaseA();

		// Execution
		executeWaitAndAssertBatchJob(A_INBOUND_FLOW_NAME);
		Thread.sleep(10000);

		executeWaitAndAssertBatchJob(B_INBOUND_FLOW_NAME);
		Thread.sleep(10000);

		// check the data in A
		SubflowInterceptingChainLifecycleWrapper subflow = getSubFlow("queryCaseInAFlow");
		subflow.initialise();
		Map<String, Object> caseA = new HashMap<String, Object>();
		caseA.put("Id", this.caseB.get("Id"));

		// deleteCases();
		System.err.println("mainflow A end");
	}

	// @Test
	// @SuppressWarnings({ "unchecked" })
	// public void testPollB() throws Exception {
	// createCaseB();
	//
	// // run Pooler B
	// runSchedulersOnce(POLL_FLOW_B);
	// waitForPollBToRun();
	// helper.awaitJobTermination(TIMEOUT_SEC * 1000, DELAY);
	// helper.assertJobWasSuccessful();
	//
	// // check the data in A
	// SubflowInterceptingChainLifecycleWrapper subflow = getSubFlow("queryCaseInAFlow");
	// subflow.initialise();
	// Map<String, Object> caseA = new HashMap<String, Object>();
	// caseA.put("ExtId__c", this.caseB.get("Id"));
	// System.err.println("quering A where ExtId__c = " + caseA.get("ExtId__c"));
	//
	// MuleEvent event = subflow.process(getTestEvent(caseA, MessageExchangePattern.REQUEST_RESPONSE));
	// ConsumerIterator<Object> result = (ConsumerIterator<Object>) event.getMessage().getPayload();
	// System.err.println("queryCaseInAFlow result: ");
	// while (result.hasNext()) {
	// Object o = result.next();
	// System.err.println(o.getClass());
	// System.err.println(o);
	// }
	//
	// }

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void createCaseA() throws Exception {
		SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("createCaseInAFlow");
		flow.initialise();
		caseA = createCase();
		List casesA = new ArrayList();
		casesA.add(caseA);

		MuleEvent event = flow.process(getTestEvent(casesA, MessageExchangePattern.REQUEST_RESPONSE));
		List<SaveResult> result = (List<SaveResult>) event.getMessage().getPayload();
		log.error("createCaseInAFlow result: " + result.get(0));
		caseA.put("Id", result.get(0).getId());

		System.err.println("Created Case A " + caseA);
		System.err.println("Created Case__c B " + caseB);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void createCaseB() throws Exception {
		SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("createCaseInBFlow");
		flow.initialise();
		caseB = createCase__c();
		List casesB = new ArrayList();
		casesB.add(caseB);

		MuleEvent event = flow.process(getTestEvent(casesB, MessageExchangePattern.REQUEST_RESPONSE));
		List<SaveResult> result = (List<SaveResult>) event.getMessage().getPayload();
		log.error("createCaseInBFlow result: " + result.get(0));
		caseB.put("Id", result.get(0).getId());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void deleteCases() throws Exception {
		SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("deleteCaseFromAFlow");
		flow.initialise();
		List casesA = new ArrayList();
		casesA.add(caseA.get("Id"));

		MuleEvent event = flow.process(getTestEvent(casesA, MessageExchangePattern.REQUEST_RESPONSE));
		Object result = event.getMessage().getPayload();
		System.err.println("deleteCaseFromAFlow result: " + result);

		// List casesB = new ArrayList();
		// casesB.add(caseB.get("Id"));
		//
		// flow = getSubFlow("deleteCaseFromBFlow");
		// event = flow.process(getTestEvent(casesB, MessageExchangePattern.REQUEST_RESPONSE));
		// result = event.getMessage().getPayload();
		// System.err.println("createCaseInBFlow result: " + result);
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
