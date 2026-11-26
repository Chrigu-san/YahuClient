package net.yahuclient.mod.packs;

import java.util.List;

import net.yahuclient.mod.packs.Modrinth.Hit;
import net.yahuclient.mod.packs.Modrinth.Kind;
import net.yahuclient.mod.packs.Modrinth.Sort;

/** What the Packs page is showing: the kind, the search text, the order and the results of the last search. */
public final class PackBrowser {
	public Kind kind = Kind.RESOURCE_PACKS;
	public Sort sort = Sort.POPULAR;
	public String query = "";

	private volatile List<Hit> hits = List.of();
	private volatile String status = "Loading...";
	private boolean started;
	private int generation;

	public List<Hit> hits() {
		if (!started) {
			search();
		}
		return hits;
	}

	public String status() {
		return status;
	}

	/** Runs the search again; an older answer arriving late is ignored. */
	public void search() {
		started = true;
		int mine = ++generation;
		hits = List.of();
		status = "Searching Modrinth...";
		Modrinth.search(kind, query, sort).whenComplete((list, error) -> {
			if (mine != generation) {
				return;
			}
			if (error != null) {
				Throwable cause = error.getCause() != null ? error.getCause() : error;
				status = "Could not reach Modrinth: " + cause.getMessage();
			} else {
				hits = list;
				status = list.isEmpty() ? "Nothing found. Try another word." : "";
			}
		});
	}
}
