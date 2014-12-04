package com.tw.go.plugin;

import java.util.ArrayList;
import java.util.List;

public class Gem {
	private String name;
	private List<String> versions;

	public Gem(String name) {
		this.name = name;
		this.versions = new ArrayList<String>();
	}

	public String getName() {
		return name;
	}

	public List<String> getVersions() {
		return versions;
	}

	public String getLatestVersion() {
		return versions.get(0);
	}

	public void addVersion(String version) {
		versions.add(version);
	}
}
