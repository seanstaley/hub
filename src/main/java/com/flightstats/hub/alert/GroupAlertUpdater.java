package com.flightstats.hub.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightstats.hub.group.Group;
import com.flightstats.hub.group.GroupService;
import com.flightstats.hub.group.GroupStatus;
import com.flightstats.hub.model.ContentKey;
import com.google.common.base.Optional;
import org.joda.time.Minutes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.concurrent.Callable;

public class GroupAlertUpdater implements Callable<AlertStatus> {

    private final static Logger logger = LoggerFactory.getLogger(GroupAlertUpdater.class);

    private static final ObjectMapper mapper = new ObjectMapper();

    private final AlertConfig alertConfig;
    private GroupService groupService;
    private final AlertStatus alertStatus;

    public GroupAlertUpdater(AlertConfig alertConfig, AlertStatus alertStatus, GroupService groupService) {
        this.alertConfig = alertConfig;
        this.groupService = groupService;
        if (alertStatus == null) {
            alertStatus = AlertStatus.builder()
                    .name(alertConfig.getName())
                    .alert(false)
                    .period(AlertStatus.MINUTE)
                    .history(new LinkedList<>())
                    .build();
        }
        this.alertStatus = alertStatus;
    }

    @Override
    public AlertStatus call() throws Exception {
        alertStatus.getHistory().clear();
        Optional<Group> groupOptional = groupService.getGroup(alertConfig.getName());
        if (!groupOptional.isPresent()) {
            return alertStatus;
        }
        Group group = groupOptional.get();
        GroupStatus groupStatus = groupService.getGroupStatus(group);
        ContentKey channelLatest = groupStatus.getChannelLatest();
        if (channelLatest == null) {
            return alertStatus;
        }
        addHistory(channelLatest, group);
        ContentKey lastCompleted = groupStatus.getLastCompleted();
        addHistory(lastCompleted, group);

        Minutes minutes = Minutes.minutesBetween(lastCompleted.getTime(), channelLatest.getTime());
        if (minutes.getMinutes() >= alertConfig.getTimeWindowMinutes()) {
            if (!alertStatus.isAlert()) {
                alertStatus.setAlert(true);
                AlertSender.sendAlert(alertConfig, alertStatus, minutes.getMinutes());
            }
        } else {
            alertStatus.setAlert(false);
        }
        return alertStatus;
    }

    private void addHistory(ContentKey contentKey, Group group) {
        AlertStatusHistory history = AlertStatusHistory.builder()
                .href(group.getChannelUrl() + "/" + contentKey.toUrl())
                .items(0)
                .build();
        alertStatus.getHistory().add(history);
    }

}
