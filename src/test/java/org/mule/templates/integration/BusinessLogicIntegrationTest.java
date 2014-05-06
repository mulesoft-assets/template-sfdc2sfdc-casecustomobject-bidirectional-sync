package org.mule.templates.integration;

import java.util.Map;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mule.MessageExchangePattern;
import org.mule.api.MuleEvent;
import org.mule.construct.Flow;
import org.mule.processor.chain.SubflowInterceptingChainLifecycleWrapper;
import org.mule.tck.junit4.rule.DynamicPort;
import org.mule.templates.builders.ObjectBuilder;

import com.mulesoft.module.batch.BatchTestHelper;

/**
 * The objective of this class is to validate the correct behavior of the flows
 * for this Mule Template that make calls to external systems.
 */
public class BusinessLogicIntegrationTest extends AbstractTemplateTestCase {

	private static Logger log = Logger.getLogger(BusinessLogicIntegrationTest.class);

	Map<String, Object> user = null;
	private BatchTestHelper helper;

	@Rule
	public DynamicPort port = new DynamicPort("http.port");

	@Before
	public void setUp() throws Exception {
		helper = new BatchTestHelper(muleContext);
		createUsersInDB();
	}

	@After
	public void tearDown() throws Exception {
		deleteUserFromDB();
		// delete users from salesforce
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testMainFlow() throws Exception {
		Flow flow = getFlow("mainFlow");
		MuleEvent event = flow.process(getTestEvent("", MessageExchangePattern.REQUEST_RESPONSE));

		helper.awaitJobTermination(120 * 1000, 500);
		helper.assertJobWasSuccessful();

		SubflowInterceptingChainLifecycleWrapper subflow = getSubFlow("querySalesforce");
		subflow.initialise();

		event = subflow.process(getTestEvent(user, MessageExchangePattern.REQUEST_RESPONSE));
		Map<String, Object> result = (Map<String, Object>) event.getMessage().getPayload();
		log.info("querySalesforce result: " + result);

		Assert.assertNotNull(result);
		Assert.assertEquals("There should be matching user in Salesforce now", user.get("email"), result.get("Email"));
	}

	private void createUsersInDB() throws Exception {
		SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("insertUserDB");
		flow.initialise();
		user = createDbUser();

		MuleEvent event = flow.process(getTestEvent(user, MessageExchangePattern.REQUEST_RESPONSE));
		Object result = event.getMessage().getPayload();
		log.info("insertUserDB result: " + result);
	}

	private void deleteUserFromDB() throws Exception {
		SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("deleteUserDB");
		flow.initialise();

		MuleEvent event = flow.process(getTestEvent(user, MessageExchangePattern.REQUEST_RESPONSE));
		Object result = event.getMessage().getPayload();
		log.info("deleteUserDB result: " + result);
	}

	private Map<String, Object> createDbUser() {
		String name = buildUniqueName(8);
		return ObjectBuilder.aUser()
				.with("firstname", name)
				.with("lastname", name)
				.with("email", name + "@fakeemail.com")
				.build();
	}

	private String buildUniqueName(int length) {
		String name = RandomStringUtils.randomAlphabetic(length).toLowerCase();
		return name;
	}

}
