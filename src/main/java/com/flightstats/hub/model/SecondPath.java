package com.flightstats.hub.model;

import com.flightstats.hub.util.TimeUtil;
import com.google.common.base.Optional;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.io.Charsets;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;

/**
 * A SecondPath represents the end of a second period.
 * So any ContentKeys contained within that second come before the SecondPath.
 */
@Getter
@EqualsAndHashCode(of = "time")
public class SecondPath implements ContentPathKeys {
    private final static Logger logger = LoggerFactory.getLogger(SecondPath.class);

    public static final SecondPath NONE = new SecondPath(new DateTime(1, DateTimeZone.UTC));

    private final DateTime time;
    private final Collection<ContentKey> keys;

    public SecondPath(DateTime time, Collection<ContentKey> keys) {
        this.time = TimeUtil.Unit.SECONDS.round(time);
        this.keys = keys;
    }

    public SecondPath(DateTime time) {
        this(time, Collections.emptyList());
    }

    public SecondPath() {
        this(TimeUtil.now().minusSeconds(1));
    }

    @Override
    public byte[] toBytes() {
        return toUrl().getBytes(Charsets.UTF_8);
    }

    @Override
    public String toUrl() {
        return TimeUtil.seconds(time);
    }

    @Override
    public String toZk() {
        return "" + time.getMillis();
    }

    @Override
    public SecondPath fromZk(String value) {
        return new SecondPath(new DateTime(Long.parseLong(value), DateTimeZone.UTC));
    }

    @Override
    public int compareTo(ContentPath other) {
        if (other instanceof SecondPath || other instanceof MinutePath) {
            return time.compareTo(other.getTime());
        } else {
            DateTime endTime = getTime().plusSeconds(1);
            int diff = endTime.compareTo(other.getTime());
            if (diff == 0) {
                return -1;
            }
            return diff;
        }
    }

    public static SecondPath fromBytes(byte[] bytes) {
        return fromUrl(new String(bytes, Charsets.UTF_8)).get();
    }

    public static Optional<SecondPath> fromUrl(String key) {
        try {
            return Optional.of(new SecondPath(TimeUtil.seconds(key)));
        } catch (Exception e) {
            logger.info("unable to parse " + key + " " + e.getMessage());
            return Optional.absent();
        }
    }

    @Override
    public String toString() {
        return toUrl();
    }
}