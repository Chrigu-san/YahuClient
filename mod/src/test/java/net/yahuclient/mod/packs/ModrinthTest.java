package net.yahuclient.mod.packs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModrinthTest {
	private static final String VERSIONS = """
			[
			 {"game_versions":["1.21"],"files":[{"url":"https://cdn/old.zip","filename":"old.zip","primary":true,"hashes":{"sha512":"aa"}}]},
			 {"game_versions":["26.3","26.2"],"files":[
			   {"url":"https://cdn/extra.zip","filename":"extra.zip","primary":false,"hashes":{"sha512":"bb"}},
			   {"url":"https://cdn/main.zip","filename":"main.zip","primary":true,"hashes":{"sha512":"cc"}}]}
			]""";

	@Test
	void searchAsksForTheKindAndTheGameVersion() {
		String url = Modrinth.searchUrl(Modrinth.Kind.SHADERS, "bsl lights", Modrinth.Sort.POPULAR, "26.3", 0);
		assertTrue(url.contains("query=bsl+lights"));
		assertTrue(url.contains("project_type%3Ashader"));
		assertTrue(url.contains("versions%3A26.3"));
		assertTrue(url.contains("index=downloads"));
		assertFalse(Modrinth.searchUrl(Modrinth.Kind.RESOURCE_PACKS, "", Modrinth.Sort.NEWEST, null, 0).contains("versions"));
	}

	@Test
	void parsesTheHits() {
		var hits = Modrinth.parseHits("{\"hits\":[{\"project_id\":\"abc\",\"title\":\"Fresh\",\"author\":\"me\",\"description\":\"nice\",\"downloads\":12}]}");
		assertEquals(1, hits.size());
		assertEquals("abc", hits.get(0).id());
		assertEquals(12, hits.get(0).downloads());
	}

	@Test
	void picksTheMainFileOfAVersionMadeForThisGame() {
		var pick = Modrinth.pick(VERSIONS, "26.3");
		assertEquals("main.zip", pick.name());
		assertEquals("cc", pick.sha512());
		assertTrue(pick.matchesGame());
	}

	@Test
	void fallsBackToTheFirstVersionAndSaysSo() {
		var pick = Modrinth.pick(VERSIONS, "27.0");
		assertEquals("old.zip", pick.name());
		assertFalse(pick.matchesGame());
		assertNull(Modrinth.pick("[]", "26.3"));
	}

	@Test
	void fileNamesStayInTheirFolder() {
		assertEquals("pack.zip", Modrinth.safeName(""));
		assertEquals(".._x.zip".replaceAll("^\\.+", ""), Modrinth.safeName("../x.zip").replace("_", "_"));
		assertFalse(Modrinth.safeName("..\\..\\evil.zip").contains("\\"));
		assertFalse(Modrinth.safeName("a/b.zip").contains("/"));
	}
}
