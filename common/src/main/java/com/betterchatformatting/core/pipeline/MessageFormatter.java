package com.betterchatformatting.core.pipeline;

import com.betterchatformatting.core.highlight.KeywordMatcher;
import com.betterchatformatting.core.links.UrlDetector;
import com.betterchatformatting.core.markdown.MarkdownParser;
import com.betterchatformatting.core.text.StyledText;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the raw text of one incoming message into styled segments:
 * <ol>
 *   <li>detect URLs (they become opaque link segments, so markdown never mangles them)</li>
 *   <li>render markdown around them</li>
 *   <li>find highlight keywords in the <em>rendered</em> text and color them</li>
 * </ol>
 * Pure function of its inputs: the loader calls it once per message and caches the result.
 */
public final class MessageFormatter {

	/** Which features are on. {@code keywords} may be null or empty. */
	public record Options(boolean markdown, boolean links, KeywordMatcher keywords) {
		public static final Options OFF = new Options(false, false, null);
	}

	/**
	 * @param text    the styled message
	 * @param matches highlight matches, positions relative to {@code text.plainText()}
	 */
	public record Result(StyledText text, List<KeywordMatcher.Match> matches) {
		/** Sound id of the first matching rule that has one, or null. */
		public String firstSound() {
			for (KeywordMatcher.Match m : matches) {
				if (m.rule().sound() != null) {
					return m.rule().sound();
				}
			}
			return null;
		}

		public boolean highlighted() {
			return !matches.isEmpty();
		}
	}

	private MessageFormatter() {
	}

	public static Result format(String body, int baseFlags, int baseColor, Options options) {
		if (body == null || body.isEmpty()) {
			return new Result(StyledText.EMPTY, List.of());
		}
		List<MarkdownParser.Literal> literals = List.of();
		if (options.links()) {
			List<UrlDetector.Match> urls = UrlDetector.find(body);
			if (!urls.isEmpty()) {
				literals = new ArrayList<>(urls.size());
				for (UrlDetector.Match m : urls) {
					literals.add(new MarkdownParser.Literal(m.start(), m.end(), m.url()));
				}
			}
		}
		StyledText styled = MarkdownParser.parse(body, baseFlags, baseColor, literals, options.markdown());

		KeywordMatcher km = options.keywords();
		if (km == null || km.isEmpty()) {
			return new Result(styled, List.of());
		}
		List<KeywordMatcher.Match> matches = km.findAll(styled.plainText());
		for (KeywordMatcher.Match m : matches) {
			styled = styled.withColorRange(m.start(), m.end(), m.rule().color());
		}
		return new Result(styled, matches);
	}
}
