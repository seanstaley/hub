package com.flightstats.hub.spoke;

import com.flightstats.hub.app.HubServices;
import com.flightstats.hub.config.S3Property;
import com.flightstats.hub.config.SpokeProperty;
import com.flightstats.hub.dao.ChannelService;
import com.flightstats.hub.dao.TtlEnforcer;
import com.flightstats.hub.model.ChannelConfig;
import com.flightstats.hub.util.Commander;
import com.flightstats.hub.util.TimeUtil;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Singleton
@Slf4j
public class ChannelTtlEnforcer {

    private final ChannelService channelService;
    private final String spokePath;

    @Inject
    public ChannelTtlEnforcer(ChannelService channelService,
                              SpokeProperty spokeProperty,
                              S3Property s3Property) {
        this.channelService = channelService;
        this.spokePath = spokeProperty.getPath(SpokeStore.WRITE);

        if (s3Property.isChannelEnforceTTL()) {
            HubServices.register(new ChannelTtlEnforcerService());
        }
    }

    private Consumer<ChannelConfig> handleCleanup() {
        return channel -> {
            if (channel.getTtlDays() > 0) {
                String channelPath = spokePath + "/" + channel.getDisplayName();
                DateTime channelTTL = TimeUtil.stable().minusDays((int) channel.getTtlDays());
                for (int i = 0; i < 3; i++) {
                    Commander.run(new String[]{"rm", "-rf", channelPath + "/" + TimeUtil.days(channelTTL.minusDays(i))}, 1);
                }
            }
        };
    }

    private class ChannelTtlEnforcerService extends AbstractScheduledService {
        @Override
        protected void runOneIteration() throws Exception {
            try {
                long start = System.currentTimeMillis();
                log.info("running channel cleanup");
                TtlEnforcer.enforce(spokePath, channelService, handleCleanup());
                log.info("completed channel cleanup {}", (System.currentTimeMillis() - start));
            } catch (Exception e) {
                log.info("issue cleaning up channels in spoke", e);
            }
        }

        @Override
        protected Scheduler scheduler() {
            return Scheduler.newFixedRateSchedule(0, 1, TimeUnit.DAYS);
        }

    }


}
