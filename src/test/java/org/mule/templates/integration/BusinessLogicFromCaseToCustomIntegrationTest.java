/**
 * Mule Anypoint Template
 *
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 */

package org.mule.templates.integration;

import java.util.ArrayList; 
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.mule.api.lifecycle.InitialisationException;
import org.mule.processor.chain.InterceptingChainLifecycleWrapper;
import org.mule.processor.chain.SubflowInterceptingChainLifecycleWrapper;
import org.mule.streaming.ConsumerIterator;
import org.mule.templates.builders.ObjectBuilder;

import com.mulesoft.module.batch.BatchTestHelper;
import com.sforce.soap.partner.SaveResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * The objective of this class is to validate the correct behavior of the flows for this Mule Template that make calls to external systems.
 */
public class BusinessLogicFromCaseToCustomIntegrationTest extends AbstractTemplateTestCase {

	private static final Logger log = Logger.getLogger(BusinessLogicFromCaseToCustomIntegrationTest.class);
	private static final int TIMEOUT_MILLIS = 60;
	
	private static final String ANYPOINT_TEMPLATE_NAME = "sfdc2sfdc-case2custom-bidirectional-sync";
	private static final String A_INBOUND_FLOW_NAME = "triggerSyncFromAFlow";
	private static final String B_INBOUND_FLOW_NAME = "triggerSyncFromBFlow";
	
	Map<String, Object> caseA = null;
	Map<String, Object> caseB = null;
	
	private BatchTestHelper batchTestHelper;
	
	
	private InterceptingChainLifecycleWrapper queryCaseInBFlow;
	private SubflowInterceptingChainLifecycleWrapper createCaseInBFlow;
	private SubflowInterceptingChainLifecycleWrapper deleteCaseFromAFlow;
	private SubflowInterceptingChainLifecycleWrapper createCaseInAFlow;
	private SubflowInterceptingChainLifecycleWrapper deleteCaseFromBFlow;

	@BeforeClass
	public static void beforeTestClass() {
		System.setProperty("page.size", "1000");

		// Set polling frequency to 10 seconds
		System.setProperty("polling.frequency", "10000");

		// Set default water-mark expression to current time
		System.clearProperty("watermark.default.expression");
		DateTime now = new DateTime(DateTimeZone.UTC);
		DateTimeFormatter dateFormat = DateTimeFormat
				.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		System.setProperty("watermark.default.expression",
				now.toString(dateFormat));
	}
	
	@Before
	public void setUp() throws Exception {
		stopAutomaticPollTriggering();
		getAndInitializeFlows();
		
		batchTestHelper = new BatchTestHelper(muleContext);
		
		// Create test Case in instance A
		createCaseA();
	}

	@After
	public void tearDown() throws Exception {
		deleteCases();
	}

	private void stopAutomaticPollTriggering() throws MuleException {
		stopFlowSchedulers(A_INBOUND_FLOW_NAME);
		stopFlowSchedulers(B_INBOUND_FLOW_NAME);
	}

	private void getAndInitializeFlows() throws InitialisationException {
		queryCaseInBFlow = getSubFlow("queryCaseInBFlow");
		queryCaseInBFlow.initialise();
		
		createCaseInBFlow = getSubFlow("createCaseInBFlow");
		createCaseInBFlow.initialise();
		
		deleteCaseFromAFlow = getSubFlow("deleteCaseFromAFlow");
		deleteCaseFromAFlow.initialise();
		
		deleteCaseFromBFlow = getSubFlow("deleteCaseFromBFlow");
		deleteCaseFromBFlow.initialise();
		
		createCaseInAFlow = getSubFlow("createCaseInAFlow");
		createCaseInAFlow.initialise();
	}

	private void executeWaitAndAssertBatchJob(String flowConstructName)
			throws Exception {

		// Execute synchronization
		runSchedulersOnce(flowConstructName);

		// Wait for the batch job execution to finish
		batchTestHelper.awaitJobTermination(TIMEOUT_MILLIS * 1000, 500);
		batchTestHelper.assertJobWasSuccessful();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void whenCreatingACaseInAANewCustomObbectIsCreatedInB() throws Exception {
		// Execution
		executeWaitAndAssertBatchJob(A_INBOUND_FLOW_NAME);

		// Get the data from B instance
		Map<String, Object> caseB = new HashMap<String, Object>();
		this.caseB = caseB;
		final Object caseIdInA = this.caseA.get("Id");
		caseB.put("CaseId__c", caseIdInA);
		MuleEvent event = queryCaseInBFlow.process(getTestEvent(caseB, MessageExchangePattern.REQUEST_RESPONSE));
		ConsumerIterator<Object> queryResult = (ConsumerIterator<Object>) event.getMessage().getPayload();
		Map<String, Object> customObject = (Map<String, Object>) queryResult.next();  
		
		assertNotNull(customObject);
		assertEquals("The Id is not the right one: ", caseIdInA, customObject.get("CaseId__c"));
		
		this.caseB.put("Id", customObject.get("Id"));
		
		assertEquals("The Subject is not the right one: ", this.caseA.get("Subject"), customObject.get("Subject__c"));
		assertEquals("The Type is not the right one: ", "Case__c", customObject.get("type"));
	}

	private Map<String, Object> createCaseA() throws Exception {
		caseA = createCase();
		List<Map<String, Object>> casesA = new ArrayList<Map<String, Object>>();
		casesA.add(caseA);

		MuleEvent event = createCaseInAFlow.process(getTestEvent(casesA, MessageExchangePattern.REQUEST_RESPONSE));
		@SuppressWarnings("unchecked")
		List<SaveResult> result = ((List<SaveResult>) event.getMessage().getPayload());
		log.info("Create test Case in A result: " + result.get(0));
		
		caseA.put("Id", result.get(0).getId());
		log.info("Created Case in A: " + caseA);
		
		return caseA;
	}

	private void deleteCases() throws Exception {
		MuleEvent event = null;
		Object result = null;
		
		if (caseA != null) {
			List<Object> casesA = new ArrayList<Object>();
			casesA.add(caseA.get("Id"));
			
			event = deleteCaseFromAFlow.process(getTestEvent(casesA, MessageExchangePattern.REQUEST_RESPONSE));
			result = event.getMessage().getPayload();
			log.info("Delete Case from A result: " + result);
		}

		if (caseB != null) {
			List<Object> casesB = new ArrayList<Object>();
			casesB.add(caseB.get("Id"));
			
			deleteCaseFromBFlow = getSubFlow("deleteCaseFromBFlow");
			event = deleteCaseFromBFlow.process(getTestEvent(casesB, MessageExchangePattern.REQUEST_RESPONSE));
			result = event.getMessage().getPayload();
			log.info("Delete Case from B result: " + result);
		}
	}

	private Map<String, Object> createCase() {
		String name = buildUniqueName();
		return ObjectBuilder.aCase()
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

	private String buildUniqueName() {
		return ANYPOINT_TEMPLATE_NAME + "-" + System.currentTimeMillis() + "Case";
	}

}
