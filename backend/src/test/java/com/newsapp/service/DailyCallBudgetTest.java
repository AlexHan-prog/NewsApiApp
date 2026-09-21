package com.newsapp.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DailyCallBudgetTest {

    @Test
    void allowsCallsUpToTheLimitThenRefuses() {
        DailyCallBudget budget = new DailyCallBudget(2);

        assertTrue(budget.tryConsume());
        assertTrue(budget.tryConsume());
        assertFalse(budget.tryConsume());
    }

    @Test
    void resetsAtTheNextUtcDay() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-21T23:59:00Z"));
        DailyCallBudget budget = new DailyCallBudget(1, clock);

        assertTrue(budget.tryConsume());
        assertFalse(budget.tryConsume());

        clock.now = Instant.parse("2026-09-22T00:00:01Z");
        assertTrue(budget.tryConsume());
    }

    @Test
    void budgetsAreIndependentPerProvider() {
        DailyCallBudget newsApi = new DailyCallBudget(1);
        DailyCallBudget guardian = new DailyCallBudget(1);

        assertTrue(newsApi.tryConsume());
        assertFalse(newsApi.tryConsume());
        assertTrue(guardian.tryConsume());
    }

    private static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
