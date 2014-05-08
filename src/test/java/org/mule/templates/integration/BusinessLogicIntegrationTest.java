package org.mule.templates.integration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mule.MessageExchangePattern;
import org.mule.api.MuleEvent;
import org.mule.processor.chain.SubflowInterceptingChainLifecycleWrapper;
import org.mule.streaming.ConsumerIterator;
import org.mule.templates.builders.ObjectBuilder;

import com.mulesoft.module.batch.BatchTestHelper;
import com.sforce.soap.partner.SaveResult;

/**
 * The objective of this class is to validate the correct behavior of the flows for this Mule Template that make calls to external systems.
 */
public class BusinessLogicIntegrationTest extends AbstractTemplateTestCase {

	protected static final int TIMEOUT = 60;
	private static final Logger log = Logger.getLogger(BusinessLogicIntegrationTest.class);
	private BatchTestHelper helper;
	Map<String, Object> caseA = null;
	Map<String, Object> caseB = null;

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
		stopFlowSchedulers(POLL_FLOW_A);
		stopFlowSchedulers(POLL_FLOW_B);

		registerListeners();
		helper = new BatchTestHelper(muleContext);
		createCases();
	}

	@After
	public void tearDown() throws Exception {

		stopFlowSchedulers(POLL_FLOW_A);
		stopFlowSchedulers(POLL_FLOW_B);
		deleteCases();
	}

	@Test
	@SuppressWarnings({ "unchecked" })
	public void testMainFlow() throws Exception {
		System.err.println("mainflow %%%%%%%%%%%%%%%%%");

		// run Pooler A
		runSchedulersOnce(POLL_FLOW_A);
		waitForPollAToRun();
		helper.awaitJobTermination(TIMEOUT_SEC * 1000, 500);
		helper.assertJobWasSuccessful();

		System.err.println("case A " + this.caseA);
		System.err.println("case B " + this.caseB);

		// check the data in B
		SubflowInterceptingChainLifecycleWrapper subflow = getSubFlow("queryCaseInBFlow");
		subflow.initialise();
		Map<String, Object> caseB = new HashMap<String, Object>();
		caseB.put("CaseId__c", this.caseA.get("Id"));
		MuleEvent event = subflow.process(getTestEvent(caseB, MessageExchangePattern.REQUEST_RESPONSE));
		ConsumerIterator<Object> result = (ConsumerIterator<Object>) event.getMessage().getPayload();
		System.err.println("queryCaseInBFlow result: ");
		while (result.hasNext()) {
			Object o = result.next();
			System.err.println(o.getClass());
			System.err.println(o);
		}

		// run Pooler B
		runSchedulersOnce(POLL_FLOW_B);
		waitForPollBToRun();
		helper.awaitJobTermination(TIMEOUT_SEC * 1000, 500);
		helper.assertJobWasSuccessful();

		// check the data in A
		subflow = getSubFlow("queryCaseInAFlow");
		subflow.initialise();
		Map<String, Object> caseA = new HashMap<String, Object>();
		caseA.put("ExtId__c", this.caseB.get("Id"));
		event = subflow.process(getTestEvent(caseA, MessageExchangePattern.REQUEST_RESPONSE));
		result = (ConsumerIterator<Object>) event.getMessage().getPayload();
		System.err.println("queryCaseInAFlow result: ");
		while (result.hasNext()) {
			Object o = result.next();
			System.err.println(o.getClass());
			System.err.println(o);
		}

		System.err.println("mainflow end %%%%%%%%%%%%%%%%%");
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void createCases() throws Exception {
		SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("createCaseInAFlow");
		flow.initialise();
		caseA = createCase();
		List casesA = new ArrayList();
		casesA.add(caseA);

		MuleEvent event = flow.process(getTestEvent(casesA, MessageExchangePattern.REQUEST_RESPONSE));
		List<SaveResult> result = (List<SaveResult>) event.getMessage().getPayload();
		log.error("createCaseInAFlow result: " + result.get(0));
		caseA.put("Id", result.get(0).getId());

		flow = getSubFlow("createCaseInBFlow");
		flow.initialise();
		caseB = createCase__c();
		List casesB = new ArrayList();
		casesB.add(caseB);

		event = flow.process(getTestEvent(casesB, MessageExchangePattern.REQUEST_RESPONSE));
		result = (List<SaveResult>) event.getMessage().getPayload();
		log.error("createCaseInBFlow result: " + result.get(0));
		caseB.put("Id", result.get(0).getId());

		System.err.println("Created Case A " + caseA);
		System.err.println("Created Case__c B " + caseB);
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

		List casesB = new ArrayList();
		casesB.add(caseB.get("Id"));

		flow = getSubFlow("deleteCaseFromBFlow");
		event = flow.process(getTestEvent(casesB, MessageExchangePattern.REQUEST_RESPONSE));
		result = event.getMessage().getPayload();
		System.err.println("createCaseInBFlow result: " + result);
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
