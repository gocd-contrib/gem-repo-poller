package com.tw.go.plugin;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class GoPluginImplTest {
	@Test
	public void test() throws Exception {
		Gem railsGem = new GoPluginImpl().runCommandAndParseOutput(new String[]{"gem", "list", "^rails$", "-a", "-r", "-s", "http://rubygems.org"});
		assertThat(railsGem.getName(), is("rails"));
	}
}