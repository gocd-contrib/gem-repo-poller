package com.tw.go.plugin;

import com.google.gson.GsonBuilder;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import org.apache.commons.validator.routines.UrlValidator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.util.Arrays.asList;

@Extension
public class GoPluginImpl implements GoPlugin {
	public static final String EXTENSION_NAME = "package-repository";
	private static final List<String> goSupportedVersions = asList("1.0");

	public static final String REQUEST_REPOSITORY_CONFIGURATION = "repository-configuration";
	public static final String REQUEST_PACKAGE_CONFIGURATION = "package-configuration";
	public static final String REQUEST_VALIDATE_REPOSITORY_CONFIGURATION = "validate-repository-configuration";
	public static final String REQUEST_VALIDATE_PACKAGE_CONFIGURATION = "validate-package-configuration";
	public static final String REQUEST_CHECK_REPOSITORY_CONNECTION = "check-repository-connection";
	public static final String REQUEST_CHECK_PACKAGE_CONNECTION = "check-package-connection";
	public static final String REQUEST_LATEST_REVISION = "latest-revision";
	public static final String REQUEST_LATEST_REVISION_SINCE = "latest-revision-since";

	private static final String DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
	public static final int SUCCESS_RESPONSE_CODE = 200;

	private static Logger LOGGER = Logger.getLoggerFor(GoPluginImpl.class);

	@Override
	public void initializeGoApplicationAccessor(GoApplicationAccessor goApplicationAccessor) {
		// ignore
	}

	@Override
	public GoPluginApiResponse handle(GoPluginApiRequest goPluginApiRequest) {
		if (goPluginApiRequest.requestName().equals(REQUEST_REPOSITORY_CONFIGURATION)) {
			return handleRepositoryConfiguration();
		} else if (goPluginApiRequest.requestName().equals(REQUEST_PACKAGE_CONFIGURATION)) {
			return handlePackageConfiguration();
		} else if (goPluginApiRequest.requestName().equals(REQUEST_VALIDATE_REPOSITORY_CONFIGURATION)) {
			return handleRepositoryValidation(goPluginApiRequest);
		} else if (goPluginApiRequest.requestName().equals(REQUEST_VALIDATE_PACKAGE_CONFIGURATION)) {
			return handlePackageValidation();
		} else if (goPluginApiRequest.requestName().equals(REQUEST_CHECK_REPOSITORY_CONNECTION)) {
			return handleRepositoryCheckConnection(goPluginApiRequest);
		} else if (goPluginApiRequest.requestName().equals(REQUEST_CHECK_PACKAGE_CONNECTION)) {
			return handlePackageCheckConnection(goPluginApiRequest);
		} else if (goPluginApiRequest.requestName().equals(REQUEST_LATEST_REVISION)) {
			return handleGetLatestRevision(goPluginApiRequest);
		} else if (goPluginApiRequest.requestName().equals(REQUEST_LATEST_REVISION_SINCE)) {
			return handleLatestRevisionSince(goPluginApiRequest);
		}
		return null;
	}

	@Override
	public GoPluginIdentifier pluginIdentifier() {
		return new GoPluginIdentifier(EXTENSION_NAME, goSupportedVersions);
	}

	private GoPluginApiResponse handleRepositoryConfiguration() {
		Map<String, Object> response = new HashMap<String, Object>();
		response.put("url", createField("URL", null, true, true, false, "1"));
		return renderJSON(SUCCESS_RESPONSE_CODE, response);
	}

	private GoPluginApiResponse handlePackageConfiguration() {
		Map<String, Object> response = new HashMap<String, Object>();
		response.put("gem", createField("Gem", null, true, true, false, "1"));
		return renderJSON(SUCCESS_RESPONSE_CODE, response);
	}

	private GoPluginApiResponse handleRepositoryValidation(GoPluginApiRequest goPluginApiRequest) {
		final Map<String, String> repositoryKeyValuePairs = keyValuePairs(goPluginApiRequest, "repository-configuration");

		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		validate(response, new FieldValidator() {
			@Override
			public void validate(Map<String, Object> fieldValidation) {
				if (!new UrlValidator().isValid(repositoryKeyValuePairs.get("url"))) {
					fieldValidation.put("key", "url");
					fieldValidation.put("message", "Invalid URL format");
				}
			}
		});
		return renderJSON(SUCCESS_RESPONSE_CODE, response);
	}

	private GoPluginApiResponse handlePackageValidation() {
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		return renderJSON(SUCCESS_RESPONSE_CODE, response);
	}

	private GoPluginApiResponse handleRepositoryCheckConnection(GoPluginApiRequest goPluginApiRequest) {
		Map<String, String> repositoryKeyValuePairs = keyValuePairs(goPluginApiRequest, "repository-configuration");

		Map<String, Object> response = new HashMap<String, Object>();
		ArrayList<String> messages = new ArrayList<String>();
		try {
			URL url = new URL(repositoryKeyValuePairs.get("url"));
			URLConnection connection = url.openConnection();
			connection.connect();
			response.put("status", "success");
			messages.add("Could connect to URL successfully");
		} catch (MalformedURLException e) {
			response.put("status", "failure");
			messages.add("Malformed URL");
		} catch (IOException e) {
			response.put("status", "failure");
			messages.add("Could not connect to URL");
		} catch (Exception e) {
			response.put("status", "failure");
			messages.add(e.getMessage());
		}

		response.put("messages", messages);
		return renderJSON(SUCCESS_RESPONSE_CODE, response);
	}

	private GoPluginApiResponse handlePackageCheckConnection(GoPluginApiRequest goPluginApiRequest) {
		Map<String, String> repositoryKeyValuePairs = keyValuePairs(goPluginApiRequest, "repository-configuration");
		Map<String, String> packageKeyValuePairs = keyValuePairs(goPluginApiRequest, "package-configuration");

		Map<String, Object> response = new HashMap<String, Object>();
		ArrayList<String> messages = new ArrayList<String>();

		String repositoryURL = repositoryKeyValuePairs.get("url");
		String packageName = packageKeyValuePairs.get("gem");
		try {
			Gem gem = runCommandAndParseOutput(getCommand(repositoryURL, packageName));
			if (gem != null) {
				response.put("status", "success");
				messages.add("Latest version: " + gem.getLatestVersion());
			} else {
				response.put("status", "failure");
				messages.add("Could not find any version of gem");
			}
		} catch (Exception e) {
			response.put("status", "failure");
			messages.add(e.getMessage());
		}

		response.put("messages", messages);
		return renderJSON(SUCCESS_RESPONSE_CODE, response);
	}

	private GoPluginApiResponse handleGetLatestRevision(GoPluginApiRequest goPluginApiRequest) {
		Map<String, String> repositoryKeyValuePairs = keyValuePairs(goPluginApiRequest, "repository-configuration");
		Map<String, String> packageKeyValuePairs = keyValuePairs(goPluginApiRequest, "package-configuration");

		String repositoryURL = repositoryKeyValuePairs.get("url");
		String packageName = packageKeyValuePairs.get("gem");
		try {
			Gem gem = runCommandAndParseOutput(getCommand(repositoryURL, packageName));
			if (gem != null) {
				Map<String, Object> revision = getRevision(gem.getLatestVersion(), null, null, new Date(), null, null);
				return renderJSON(SUCCESS_RESPONSE_CODE, revision);
			}
			return renderJSON(SUCCESS_RESPONSE_CODE, null);
		} catch (Exception e) {
			// add exception message
			return renderJSON(500, null);
		}
	}

	private GoPluginApiResponse handleLatestRevisionSince(GoPluginApiRequest goPluginApiRequest) {
		Map<String, String> repositoryKeyValuePairs = keyValuePairs(goPluginApiRequest, "repository-configuration");
		Map<String, String> packageKeyValuePairs = keyValuePairs(goPluginApiRequest, "package-configuration");
		Map<String, Object> previousRevisionMap = getMapFor(goPluginApiRequest, "previous-revision");
		String previousRevision = (String) previousRevisionMap.get("revision");

		String repositoryURL = repositoryKeyValuePairs.get("url");
		String packageName = packageKeyValuePairs.get("gem");
		try {
			Gem gem = runCommandAndParseOutput(getCommand(repositoryURL, packageName));
			if (gem != null && !gem.getLatestVersion().equals(previousRevision)) {
				Map<String, Object> revision = getRevision(gem.getLatestVersion(), null, null, new Date(), null, null);
				return renderJSON(SUCCESS_RESPONSE_CODE, revision);
			}
			return renderJSON(SUCCESS_RESPONSE_CODE, null);
		} catch (Exception e) {
			// add exception message
			return renderJSON(500, null);
		}
	}

	private String[] getCommand(String repositoryURL, String packageName) {
		return new String[]{"gem", "list", "^" + packageName + "$", "-a", "-r", "-s", repositoryURL};
	}

	Gem runCommandAndParseOutput(String[] commands) throws Exception {
		Runtime runtime = Runtime.getRuntime();
		Process process = runtime.exec(commands);

		BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
		List<String> outputLines = new ArrayList<String>();
		String line = null;
		while ((line = stdOut.readLine()) != null) {
			outputLines.add(line);
		}

		return parseOutput(outputLines);
	}

	private Gem parseOutput(List<String> output) {
		for (String line : output) {
			String trimmedLine = line.trim();
			if (!trimmedLine.isEmpty()) {
				String[] lineParts = trimmedLine.split(" \\(");
				Gem gem = new Gem(lineParts[0].trim());
				String versions = lineParts[1].substring(0, lineParts[1].length() - 1);
				String[] versionParts = versions.split(", ");
				for (String version : versionParts) {
					gem.addVersion(version.trim());
				}
				return gem;
			}
		}
		return null;
	}

	private void validate(List<Map<String, Object>> response, FieldValidator fieldValidator) {
		Map<String, Object> fieldValidation = new HashMap<String, Object>();
		fieldValidator.validate(fieldValidation);
		if (!fieldValidation.isEmpty()) {
			response.add(fieldValidation);
		}
	}

	private Map<String, Object> getRevision(String revision, String comment, String user, Date timestamp, String url, HashMap<String, String> data) {
		Map<String, Object> response = new HashMap<String, Object>();
		response.put("revision", revision);
		response.put("revisionComment", comment);
		response.put("user", user);
		response.put("timestamp", new SimpleDateFormat(DATE_PATTERN).format(timestamp));
		response.put("trackbackUrl", url);
		response.put("data", data);
		return response;
	}

	private Map<String, Object> getMapFor(GoPluginApiRequest goPluginApiRequest, String field) {
		Map<String, Object> map = (Map<String, Object>) new GsonBuilder().create().fromJson(goPluginApiRequest.requestBody(), Object.class);
		Map<String, Object> fieldProperties = (Map<String, Object>) map.get(field);
		return fieldProperties;
	}

	private Map<String, String> keyValuePairs(GoPluginApiRequest goPluginApiRequest, String mainKey) {
		Map<String, String> keyValuePairs = new HashMap<String, String>();
		Map<String, Object> map = (Map<String, Object>) new GsonBuilder().create().fromJson(goPluginApiRequest.requestBody(), Object.class);
		Map<String, Object> fieldsMap = (Map<String, Object>) map.get(mainKey);
		for (String field : fieldsMap.keySet()) {
			Map<String, Object> fieldProperties = (Map<String, Object>) fieldsMap.get(field);
			String value = (String) fieldProperties.get("value");
			keyValuePairs.put(field, value);
		}
		return keyValuePairs;
	}

	private Map<String, Object> createField(String displayName, String defaultValue, boolean isPartOfIdentity, boolean isRequired, boolean isSecure, String displayOrder) {
		Map<String, Object> fieldProperties = new HashMap<String, Object>();
		fieldProperties.put("display-name", displayName);
		fieldProperties.put("default-value", defaultValue);
		fieldProperties.put("part-of-identity", isPartOfIdentity);
		fieldProperties.put("required", isRequired);
		fieldProperties.put("secure", isSecure);
		fieldProperties.put("display-order", displayOrder);
		return fieldProperties;
	}

	private GoPluginApiResponse renderJSON(final int responseCode, Object response) {
		final String json = response == null ? null : new GsonBuilder().create().toJson(response);
		return new GoPluginApiResponse() {
			@Override
			public int responseCode() {
				return responseCode;
			}

			@Override
			public Map<String, String> responseHeaders() {
				return null;
			}

			@Override
			public String responseBody() {
				return json;
			}
		};
	}
}
