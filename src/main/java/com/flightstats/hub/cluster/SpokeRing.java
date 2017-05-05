package com.flightstats.hub.cluster;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightstats.hub.app.HubProperties;
import com.flightstats.hub.util.Hash;
import com.flightstats.hub.util.TimeInterval;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.*;

/**
 * A SpokeRing represents the state of a cluster over a period of time.
 * There must always be a start time.
 * The end time is only used if this cluster is no longer active.
 */
class SpokeRing implements Ring {

    private static final int OVERLAP_SECONDS = HubProperties.getProperty("spoke.ring.overlap.seconds", 1);

    private List<String> spokeNodes;
    private long rangeSize;
    private TimeInterval timeInterval;
    private DateTime startTime;

    SpokeRing(DateTime startTime, String... nodes) {
        this(startTime, null, nodes);
    }

    SpokeRing(DateTime startTime, DateTime endTime, String... nodes) {
        this.startTime = startTime;
        timeInterval = new TimeInterval(startTime, endTime);
        initialize(Arrays.asList(nodes));
    }

    SpokeRing(ClusterEvent clusterEvent) {
        setStartTime(clusterEvent);
        initialize(Collections.singletonList(clusterEvent.getName()));
    }

    SpokeRing(ClusterEvent clusterEvent, SpokeRing previousRing) {
        setStartTime(clusterEvent);
        previousRing.setEndTime(
                new DateTime(clusterEvent.getModifiedTime(), DateTimeZone.UTC)
                        .plusSeconds(OVERLAP_SECONDS));
        HashSet<String> nodes = new HashSet<>(previousRing.spokeNodes);
        if (clusterEvent.isAdded()) {
            nodes.add(clusterEvent.getName());
        } else {
            nodes.remove(clusterEvent.getName());
        }
        initialize(nodes);
    }

    private void setStartTime(ClusterEvent clusterEvent) {
        this.startTime = new DateTime(clusterEvent.getModifiedTime(), DateTimeZone.UTC);
        timeInterval = new TimeInterval(startTime, null);
    }

    private void initialize(Collection<String> nodes) {
        Map<Long, String> hashedNodes = new TreeMap<>();
        for (String node : nodes) {
            hashedNodes.put(Hash.hash(node), node);
        }
        rangeSize = Hash.getRangeSize(hashedNodes.size());
        spokeNodes = new ArrayList<>(hashedNodes.values());
    }

    void setEndTime(DateTime endTime) {
        timeInterval = new TimeInterval(startTime, endTime);
    }

    public Set<String> getServers(String channel) {
        if (spokeNodes.size() <= 3) {
            return new HashSet<>(spokeNodes);
        }
        long hash = Hash.hash(channel);
        int node = (int) (hash / rangeSize);
        if (hash < 0) {
            node = spokeNodes.size() + node - 1;
        }
        Set<String> found = new HashSet<>();
        int minimum = Math.min(3, spokeNodes.size());
        while (found.size() < minimum) {
            if (node == spokeNodes.size()) {
                node = 0;
            }
            found.add(spokeNodes.get(node));
            node++;

        }
        return found;
    }

    public Set<String> getServers(String channel, DateTime pointInTime) {
        if (timeInterval.contains(pointInTime)) {
            return getServers(channel);
        }
        return Collections.emptySet();
    }

    public Set<String> getServers(String channel, DateTime startTime, DateTime endTime) {
        if (timeInterval.overlaps(startTime, endTime)) {
            return getServers(channel);
        }
        return Collections.emptySet();
    }

    boolean endsBefore(DateTime endTime) {
        return !timeInterval.isAfter(endTime);
    }

    @Override
    public String toString() {
        return "SpokeRing{" +
                "startTime=" + startTime +
                ", spokeNodes=" + spokeNodes +
                ", timeInterval=" + timeInterval +
                ", rangeSize=" + rangeSize +
                '}';
    }

    public void status(ObjectNode root) {
        root.put("nodes", spokeNodes.toString());
        timeInterval.status(root);
    }
}
