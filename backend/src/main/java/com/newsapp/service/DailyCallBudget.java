package com.newsapp.service;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Caps real NewsAPI calls per UTC day. In memory, so a restart resets the count. */
@Component
public class DailyCallBudget {

    private final int limit;
    private final Clock clock;

    private LocalDate day;
    private int used;

    @Autowired
    public DailyCallBudget(@Value("${newsapi.daily-budget}") int limit) {
        this(limit, Clock.systemUTC());
    }

    DailyCallBudget(int limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
        this.day = LocalDate.now(clock);
    }

    /** Takes one call from today's budget; false if it is used up. */
    public synchronized boolean tryConsume() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) {
            day = today;
            used = 0;
        }
        if (used >= limit) {
            return false;
        }
        used++;
        return true;
    }
}
