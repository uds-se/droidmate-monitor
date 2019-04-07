package org.droidmate.monitor;

import java.util.Arrays;
import java.util.List;

class ApiPolicyId {
	private String method;
	private List<String> uriList;

	ApiPolicyId(String method, String... uris) {
		this.method = method;
		this.uriList = Arrays.asList(uris);

		assert this.method != null;
	}

	boolean affects(String methodName, List<String> uriList) {
		boolean equal = this.method.equals(methodName.replaceAll("\\s+", ""));

		StringBuilder b = new StringBuilder();
		for (String uri : uriList)
			b.append(uri);
		String apiList = b.toString();

		for (String restrictedUri : this.uriList) {
			equal &= apiList.contains(restrictedUri);
		}

		return equal;
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof ApiPolicyId) &&
				((ApiPolicyId) other).method.equals(this.method) &&
				((ApiPolicyId) other).uriList.equals(this.uriList);
	}
}