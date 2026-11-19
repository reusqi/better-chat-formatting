package com.betterchatformatting.core.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable sequence of {@link Segment}s. Adjacent segments with identical styling are
 * merged on construction, and empty segments are dropped.
 */
public final class StyledText {
	public static final StyledText EMPTY = new StyledText(List.of());

	private final List<Segment> segments;
	private String plain; // lazily computed, benign data race (String is immutable)

	private StyledText(List<Segment> segments) {
		this.segments = segments;
	}

	public static StyledText of(List<Segment> raw) {
		List<Segment> merged = new ArrayList<>(raw.size());
		for (Segment s : raw) {
			if (s.text().isEmpty()) {
				continue;
			}
			int last = merged.size() - 1;
			if (last >= 0 && merged.get(last).sameStyle(s)) {
				merged.set(last, merged.get(last).withText(merged.get(last).text() + s.text()));
			} else {
				merged.add(s);
			}
		}
		return new StyledText(Collections.unmodifiableList(merged));
	}

	public static StyledText ofPlain(String text) {
		return of(List.of(Segment.plain(text)));
	}

	public List<Segment> segments() {
		return segments;
	}

	public boolean isEmpty() {
		return segments.isEmpty();
	}

	/** The visible text with all styling removed. */
	public String plainText() {
		String p = plain;
		if (p == null) {
			StringBuilder sb = new StringBuilder();
			for (Segment s : segments) {
				sb.append(s.text());
			}
			plain = p = sb.toString();
		}
		return p;
	}

	/**
	 * Applies {@code color} to the visible range {@code [start, end)}, splitting segments where
	 * needed. Link segments are left untouched so links keep their own look.
	 */
	public StyledText withColorRange(int start, int end, int color) {
		if (start >= end) {
			return this;
		}
		List<Segment> out = new ArrayList<>(segments.size() + 2);
		int pos = 0;
		for (Segment s : segments) {
			int segStart = pos;
			int segEnd = pos + s.text().length();
			pos = segEnd;
			if (s.isLink() || segEnd <= start || segStart >= end) {
				out.add(s);
				continue;
			}
			int from = Math.max(start, segStart) - segStart;
			int to = Math.min(end, segEnd) - segStart;
			String t = s.text();
			if (from > 0) {
				out.add(s.withText(t.substring(0, from)));
			}
			out.add(s.withText(t.substring(from, to)).withColor(color));
			if (to < t.length()) {
				out.add(s.withText(t.substring(to)));
			}
		}
		return of(out);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof StyledText st && segments.equals(st.segments);
	}

	@Override
	public int hashCode() {
		return segments.hashCode();
	}

	@Override
	public String toString() {
		return segments.toString();
	}
}
