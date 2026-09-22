package com.newsapp.model;

import java.util.List;

/**
 * Claude's political-leaning assessment of one article, read in full.
 *
 * @param score     Claude's own stated confidence in {@code label}, between 0 and 1. This is the model's
 *                  self-assessment, not a calibrated statistic the way a trained classifier's probability would be.
 * @param explanation a short, human-readable explanation of the reasoning, grounded in the article's framing, word
 *                  choice, sourcing or omissions
 * @param excerpts  exact quotes from the article that illustrate the leaning (0-5; empty for neutral, wire-style
 *                  reporting). Every quote here has been checked against the article text it was drawn from, so it
 *                  is a real excerpt, not something the model paraphrased or invented.
 */
public record Leaning(Label label, double score, String explanation, List<Excerpt> excerpts) {

    public enum Label {
        LEFT,
        CENTER,
        RIGHT
    }

    /**
     * One quote backing the assessment.
     *
     * @param side   which way this particular quote leans; only LEFT or RIGHT is meaningful here (a CENTER-tagged
     *               excerpt doesn't support anything, so it's filtered out before this record is built)
     * @param reason one short sentence on why this quote signals that leaning
     */
    public record Excerpt(String quote, Label side, String reason) {}
}
