package com.flightstats.hub.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;

import java.util.SortedSet;

public interface Traces {
    static final Traces NOOP = new NoOpTraces();

    void add(Trace trace);

    void add(Object... objects);

    void add(String string, SortedSet sortedSet);

    void setStart(long start);

    long getStart();

    void logSlow(long millis, Logger logger);

    void output(ObjectNode root);

    void log(Logger logger);

    static Traces getTraces(boolean trace) {
        return trace ? new TracesImpl() : Traces.NOOP;
    }

}
