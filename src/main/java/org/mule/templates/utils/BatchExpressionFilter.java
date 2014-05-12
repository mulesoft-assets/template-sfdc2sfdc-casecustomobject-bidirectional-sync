package org.mule.templates.utils;

import java.util.Map;

public class BatchExpressionFilter {
	public static boolean evaluate(Map<String, Object> payload, Map<String, Object> caseInTargetInstance, String sourceSystem,
			String integrationUser, String batchStep) {

		boolean result = true;
		result = batchStep.equals(sourceSystem)
				|| (caseInTargetInstance == null ? false : ((String) caseInTargetInstance.get("LastModifiedDate"))
						.compareTo((String) payload
								.get("LastModifiedDate")) >= 0 || !integrationUser.equals(payload.get("LastModifiedById")));

		// // boolean result = batchStep.equals(sourceSystem);
		// if (result == false) {
		// System.err.println("sourceSystem " + sourceSystem);
		// System.err.println("batchStep " + batchStep);
		// System.err.println("caseInTargetInstance ");
		// System.err.println("LastModifiedDate " + payload.get("LastModifiedDate"));
		// System.err.println("user " + payload.get("LastModifiedById"));
		// }
		return result;
	}
}
