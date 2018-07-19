package com.sos.jitl.reporting.model.report;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

import org.hibernate.query.Query;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sos.hibernate.classes.SOSHibernate;
import com.sos.hibernate.classes.SOSHibernateSession;
import com.sos.hibernate.exceptions.SOSHibernateException;
import com.sos.hibernate.exceptions.SOSHibernateObjectOperationStaleStateException;
import com.sos.jitl.classes.event.JobSchedulerEvent.EventKey;
import com.sos.jitl.classes.event.JobSchedulerEvent.EventType;
import com.sos.jitl.classes.plugin.PluginMailer;
import com.sos.jitl.reporting.db.DBItemReportExecution;
import com.sos.jitl.reporting.db.DBItemReportTask;
import com.sos.jitl.reporting.db.DBItemReportTrigger;
import com.sos.jitl.reporting.db.DBItemReportVariable;
import com.sos.jitl.reporting.db.DBItemSchedulerHistory;
import com.sos.jitl.reporting.db.DBItemSchedulerHistoryOrderStepReporting;
import com.sos.jitl.reporting.db.DBLayerReporting;
import com.sos.jitl.reporting.exceptions.SOSReportingConcurrencyException;
import com.sos.jitl.reporting.exceptions.SOSReportingLockException;
import com.sos.jitl.reporting.helper.CounterSynchronize;
import com.sos.jitl.reporting.helper.EStartCauses;
import com.sos.jitl.reporting.helper.InventoryInfo;
import com.sos.jitl.reporting.helper.ReportUtil;
import com.sos.jitl.reporting.helper.TaskStarted;
import com.sos.jitl.reporting.job.report.FactJobOptions;
import com.sos.jitl.reporting.model.IReportingModel;
import com.sos.jitl.reporting.model.ReportingModel;
import com.sos.jitl.reporting.plugin.FactNotificationPlugin;

import sos.util.SOSString;

public class FactModel extends ReportingModel implements IReportingModel {

    public enum TaskSync {
        EVENT, UNCOMPLETED, PERIOD
    }

    public static final long RERUN_INTERVAL = 2;// in seconds
    public static final int MAX_RERUNS = 3;
    private static final Logger LOGGER = LoggerFactory.getLogger(FactModel.class);
    private static final boolean isDebugEnabled = LOGGER.isDebugEnabled();
    private static final String TABLE_REPORTING_VARIABLES_VARIABLE_PREFIX = "reporting_";
    private static final String LOCK_PREFIX = "locked";
    private static final String LOCK_DELIMITER = "#";
    private static final int MAX_LOCK_WAIT = 15;// in seconds
    private static final long MAX_LOCK_VERSION = 10_000_000;
    private FactJobOptions options;
    private JsonArray events;
    private SOSHibernateSession schedulerSession;
    private CounterSynchronize counterOrderSyncUncompleted;
    private CounterSynchronize counterOrderSync;
    private CounterSynchronize counterTaskSyncUncompleted;
    private CounterSynchronize counterTaskSyncEvent;
    private CounterSynchronize counterTaskSync;
    private CounterSynchronize counterTaskSyncNotFounded;
    private long counterTaskStartedEventsInserted;
    private boolean isChanged = false;
    private boolean isOrdersChanged = false;
    private boolean isTasksChanged = false;
    private boolean isLocked = false;
    private String lockCause = null;
    private int maxHistoryAge;
    private int maxUncompletedAge;
    private Long maxHistoryTasks;
    private Long waitInterval;
    private String schedulerId;
    private List<Long> uncompletedTaskHistoryIds;
    private List<Long> eventTaskClosedHistoryIds;
    private Map<Long, TaskStarted> eventTaskStartedHistoryIds;
    private Optional<Integer> largeResultFetchSizeReporting = Optional.empty();
    private Optional<Integer> largeResultFetchSizeScheduler = Optional.empty();
    private FactNotificationPlugin notificationPlugin;
    private HashMap<Long, DBItemReportTask> endedOrderTasks4notification;

    public FactModel(SOSHibernateSession reportingSess, SOSHibernateSession schedulerSess, FactJobOptions opt, JsonArray es) throws Exception {
        setReportingSession(reportingSess);
        schedulerSession = schedulerSess;
        options = opt;
        events = es;

        largeResultFetchSizeReporting = getFetchSize(options.large_result_fetch_size.value());
        largeResultFetchSizeScheduler = getFetchSize(options.large_result_fetch_size_scheduler.value());
        maxHistoryAge = ReportUtil.resolveAge2Minutes(options.max_history_age.getValue());
        maxHistoryTasks = new Long(options.max_history_tasks.value());
        maxUncompletedAge = ReportUtil.resolveAge2Minutes(options.max_uncompleted_age.getValue());
        waitInterval = new Long(options.wait_interval.value());
        schedulerId = options.current_scheduler_id.getValue();
        registerPlugin();
    }

    public void init(PluginMailer mailer, Path configDirectory) {
        pluginOnInit(mailer, configDirectory);
    }

    public void exit() {
        pluginOnExit();
    }

    @Override
    public void process() throws Exception {
        String method = "process";
        Date dateFrom = null;
        Date dateTo = ReportUtil.getCurrentDateTime();
        String dateToAsString = ReportUtil.getDateAsString(dateTo);
        Long dateToAsMinutes = ReportUtil.getDateAsMinutes(dateTo);
        Long dateToAsSeconds = ReportUtil.getDateAsSeconds(dateTo);
        DateTime start = new DateTime();
        initCounters();

        if (isDebugEnabled) {
            LOGGER.debug(String.format("[%s]execute_notification_plugin=%s", method, options.execute_notification_plugin.value()));
        }

        DBItemReportVariable reportingVariable = null;
        boolean finisched = false;
        try {
            isLocked = false;
            lockCause = null;
            reportingVariable = initSynchronizing(dateTo, dateToAsSeconds, dateToAsString);
            if (isLocked) {
                LOGGER.info(String.format("[%s to %s UTC][skip synchronizing][locked]%s", reportingVariable.getTextValue(), dateToAsString,
                        lockCause));
            } else {
                dateFrom = getDateFrom(reportingVariable, dateTo, dateToAsString);
                String dateFromAsString = ReportUtil.getDateAsString(dateFrom);
                if (doProcessing(dateFrom, dateTo)) {
                    analyzeEvents(events);
                    synchronizeTaskClosedEvents(schedulerId, dateToAsMinutes);

                    synchronizeUncompletedTasks(schedulerId, dateToAsMinutes);
                    synchronizeTimePeriodTasks(schedulerId, dateFrom, dateTo, dateToAsMinutes, dateFromAsString, dateToAsString);

                    synchronizeUncompletedOrders(schedulerId, dateToAsMinutes);
                    synchronizeTimePeriodOrders(schedulerId, dateFrom, dateTo, dateToAsMinutes, dateFromAsString, dateToAsString);

                    synchronizeNotFounded(dateToAsMinutes);
                    synchronizeTaskStartedEvents(schedulerId, dateToAsMinutes);

                    updateNotificationRest();

                    finishSynchronizing(reportingVariable, dateToAsString);
                    finisched = true;
                    setChangedSummary();
                    logSummary(dateFromAsString, dateToAsString, start);
                } else {
                    LOGGER.info(String.format("[%s to %s UTC][skip synchronizing]time diff in seconds < wait interval(%s s)", dateFromAsString,
                            dateToAsString, waitInterval));
                }
            }
        } catch (Exception e) {
            try {
                if (!finisched && reportingVariable != null && !SOSString.isEmpty(reportingVariable.getTextValue())) {
                    String oldDateFrom = getWithoutLocked(reportingVariable.getTextValue());
                    LOGGER.info(String.format("[%s][%s][%s]reset synchronizing on exception", method, reportingVariable.getTextValue(), oldDateFrom));
                    finishSynchronizing(reportingVariable, oldDateFrom);
                } else {
                    LOGGER.info(String.format("[%s][%s][%s][skip]reset synchronizing on exception", method, reportingVariable, finisched));
                }
            } catch (Throwable ee) {
                LOGGER.warn(String.format("error occured during reset synchronizing on exception: %s", method, ee.toString()));
            }
            Exception lae = SOSHibernate.findLockException(e);
            if (lae == null) {
                throw e;
            } else {
                throw new SOSReportingLockException(e);
            }
        }
    }

    private void finishSynchronizing(DBItemReportVariable reportingVariable, String dateTo) throws Exception {
        String method = "finishSynchronizing";
        try {
            if (isDebugEnabled) {
                LOGGER.debug(String.format("[%s]dateTo=%s", method, dateTo));
            }
            getDbLayer().getSession().beginTransaction();
            reportingVariable.setNumericValue(new Long(maxHistoryAge));
            reportingVariable.setTextValue(dateTo);
            getDbLayer().getSession().update(reportingVariable);
            if (reportingVariable.getLockVersion() > MAX_LOCK_VERSION) {
                getDbLayer().resetReportVariableLockVersion(reportingVariable.getName());
            }
            getDbLayer().getSession().commit();
        } catch (SOSHibernateObjectOperationStaleStateException e) {
            try {
                getDbLayer().getSession().rollback();
            } catch (Exception ex) {
                LOGGER.warn(String.format("[%s]%s", method, ex.toString()), ex);
            }
            LOGGER.warn(String.format("[%s]%s", method, e.toString()), e);
        } catch (Exception e) {
            try {
                getDbLayer().getSession().rollback();
            } catch (Exception ex) {
                LOGGER.warn(String.format("[%s]%s", method, ex.toString()), ex);
            }
            throw new Exception(String.format("[%s]%s", method, e.toString()), e);
        }
    }

    private String getSetLocked(Date dateFrom, String dateTo) throws Exception {
        return getSetLocked(ReportUtil.getDateAsString(dateFrom), dateTo);
    }

    private String getSetLocked(String dateFrom, String dateTo) throws Exception {
        return LOCK_PREFIX + LOCK_DELIMITER + dateFrom + LOCK_DELIMITER + dateTo;
    }

    private String getWithoutLocked(String val) {
        String[] arr = val.split(LOCK_DELIMITER);
        if (arr.length > 1) {
            return arr[1];
        }
        return val;
    }

    private boolean doProcessing(Date dateFrom, Date dateTo) {
        Long diff = ReportUtil.getDateAsSeconds(dateTo) - ReportUtil.getDateAsSeconds(dateFrom);
        if (diff < waitInterval) {
            if (isDebugEnabled) {
                LOGGER.debug(String.format("[doProcessing][skip]diff=%ss, waitInterval=%ss", diff, waitInterval));
            }
            return false;
        }
        return true;
    }

    private DBItemReportVariable initSynchronizing(Date dateTo, Long dateToAsSeconds, String dateToAsString) throws Exception {
        String method = "initSynchronizing";
        DBItemReportVariable variable = null;
        try {
            String name = getSchedulerVariableName();
            if (isDebugEnabled) {
                LOGGER.debug(String.format("[%s]name=%s", method, name));
            }
            getDbLayer().getSession().beginTransaction();
            variable = getDbLayer().getReportVariabe(name);
            if (variable == null) {
                Date dateFrom = ReportUtil.getDateTimeMinusMinutes(dateTo, new Long(maxHistoryAge));
                variable = getDbLayer().insertReportVariable(name, new Long(maxHistoryAge), getSetLocked(dateFrom, dateToAsString));
                if (isDebugEnabled) {
                    LOGGER.debug(String.format("[%s]dateFrom=%s (initial, %s-%s)", method, ReportUtil.getDateAsString(dateFrom), dateToAsString,
                            maxHistoryAge));
                }
            } else {
                String dateFromAsString = variable.getTextValue();
                if (isDebugEnabled) {
                    LOGGER.debug(String.format("[%s]storedDateFrom=%s, dateTo=%s, storedMaxAge=%s, storedLockVersion=%s", method, dateFromAsString,
                            dateToAsString, variable.getNumericValue(), variable.getLockVersion()));
                }
                Date dateFrom = null;
                if (SOSString.isEmpty(dateFromAsString)) {
                    dateFrom = ReportUtil.getDateTimeMinusMinutes(dateTo, new Long(maxHistoryAge));
                    dateFromAsString = ReportUtil.getDateAsString(dateFrom);
                } else {
                    if (dateFromAsString.startsWith(LOCK_PREFIX + LOCK_DELIMITER)) {
                        String[] arr = dateFromAsString.split(LOCK_DELIMITER);
                        dateFromAsString = arr[1];
                        dateFrom = ReportUtil.getDateFromString(dateFromAsString);
                        Date anotherDateTo = null;
                        if (arr.length > 2) {
                            anotherDateTo = ReportUtil.getDateFromString(arr[2]);
                        } else {
                            anotherDateTo = dateFrom;
                        }
                        Long anotherDateToAsSeconds = ReportUtil.getDateAsSeconds(anotherDateTo);
                        Long diff = dateToAsSeconds - anotherDateToAsSeconds;
                        if (diff >= MAX_LOCK_WAIT) {
                        } else {
                            if (isDebugEnabled) {
                                LOGGER.debug(String.format(
                                        "[%s][%s]set isLocked: diff=%ss(dateToAsSeconds=%s-anotherDateToAsSeconds=%s) >= MAX_LOCK_WAIT=%ss", method,
                                        dateFromAsString, diff, dateToAsSeconds, anotherDateToAsSeconds, MAX_LOCK_WAIT));
                            }
                            lockCause = String.format("diff=%ss between the current and the last request", diff);
                            isLocked = true;
                        }
                    } else {
                        dateFrom = ReportUtil.getDateFromString(dateFromAsString);
                    }
                }
                if (!isLocked) {
                    if (doProcessing(dateFrom, dateTo)) {
                        variable.setNumericValue(new Long(maxHistoryAge));
                        String lockedValue = getSetLocked(dateFromAsString, dateToAsString);
                        if (isDebugEnabled) {
                            LOGGER.debug(String.format("[%s]lockedValue=%s", method, lockedValue));
                        }
                        variable.setTextValue(lockedValue);
                        getDbLayer().getSession().update(variable);
                    }
                }
            }
            getDbLayer().getSession().commit();
            if (isDebugEnabled) {
                LOGGER.debug(String.format("[%s]dateFrom=%s, maxAge=%s, lockVersion=%s", method, variable.getTextValue(), variable.getNumericValue(),
                        variable.getLockVersion()));
            }
        } catch (SOSHibernateObjectOperationStaleStateException e) {
            try {
                getDbLayer().getSession().rollback();
            } catch (Exception ex) {
                LOGGER.warn(String.format("[%s]%s", method, ex.toString()), ex);
            }
            if (isDebugEnabled) {
                LOGGER.debug(String.format("[%s]set isLocked on SOSHibernateObjectOperationStaleStateException: %s", method, e.toString()));
            }
            lockCause = "locked by an another instance";
            isLocked = true;
        } catch (Exception e) {
            try {
                getDbLayer().getSession().rollback();
            } catch (Exception ex) {
                LOGGER.warn(String.format("[%s]%s", method, ex.toString()), ex);
            }
            throw new Exception(String.format("[%s]%s", method, e.toString()), e);
        }
        return variable;
    }

    private String getSchedulerVariableName() {
        String name = String.format("%s%s", TABLE_REPORTING_VARIABLES_VARIABLE_PREFIX, schedulerId);
        if (name.length() > 255) {
            name = name.substring(0, 255);
        }
        return name.toLowerCase();
    }

    private List<Long> getOrderSyncUncomplitedHistoryIds(String schedulerId) throws Exception {
        String method = "getOrderSyncUncomplitedHistoryIds";
        List<Long> historyIds = new ArrayList<Long>();
        try {
            getDbLayer().getSession().beginTransaction();
            List<Long> result = getDbLayer().getOrderSyncUncomplitedHistoryIds(largeResultFetchSizeReporting, schedulerId);
            getDbLayer().getSession().commit();
            for (int i = 0; i < result.size(); i++) {
                Long historyId = result.get(i);
                if (!historyIds.contains(historyId)) {
                    historyIds.add(historyId);
                }
            }
        } catch (Exception e) {
            try {
                getDbLayer().getSession().rollback();
            } catch (Exception ex) {
                LOGGER.warn(String.format("[%s]%s", method, ex.toString()), ex);
            }
            throw e;
        }
        return historyIds;
    }

    private void synchronizeUncompletedOrders(String schedulerId, Long dateToAsMinutes) throws Exception {
        String method = "synchronizeUncompletedOrders";
        if (schedulerId != null && !schedulerId.isEmpty()) {
            List<Long> historyIds = new ArrayList<Long>();

            int count = 0;
            boolean run = true;
            while (run) {
                count++;
                try {
                    historyIds = getOrderSyncUncomplitedHistoryIds(schedulerId);
                    run = false;
                } catch (Exception e) {
                    handleException(method, e, count);
                }
            }

            int size = historyIds.size();
            if (isDebugEnabled) {
                LOGGER.debug(String.format("[%s]found %s uncompleted orders in the reporting db", method, size));
            }
            if (size > 0) {
                if (size > SOSHibernate.LIMIT_IN_CLAUSE) {
                    LOGGER.info(String.format("[%s]%s uncompleted orders > as %s. do split...", method, size, SOSHibernate.LIMIT_IN_CLAUSE));
                    int counterTotal = 0;
                    int counterSkip = 0;
                    int counterInsertedTriggers = 0;
                    int counterUpdatedTriggers = 0;
                    int counterInsertedExecutions = 0;
                    int counterUpdatedExecutions = 0;
                    int counterInsertedTasks = 0;
                    int counterUpdatedTasks = 0;

                    for (int i = 0; i < size; i += SOSHibernate.LIMIT_IN_CLAUSE) {
                        List<Long> subList;
                        if (size > i + SOSHibernate.LIMIT_IN_CLAUSE) {
                            subList = historyIds.subList(i, (i + SOSHibernate.LIMIT_IN_CLAUSE));
                        } else {
                            subList = historyIds.subList(i, size);
                        }
                        Query<DBItemSchedulerHistoryOrderStepReporting> query = getDbLayer().getSchedulerHistoryOrderStepsQuery(schedulerSession,
                                largeResultFetchSizeScheduler, schedulerId, subList);
                        CounterSynchronize counter = synchronizeOrderHistory(method, query, dateToAsMinutes);
                        counterTotal += counter.getTotal();
                        counterSkip += counter.getSkip();
                        counterInsertedTriggers += counter.getInsertedTriggers();
                        counterUpdatedTriggers += counter.getUpdatedTriggers();
                        counterInsertedExecutions += counter.getInsertedExecutions();
                        counterUpdatedExecutions += counter.getUpdatedExecutions();
                        counterInsertedTasks += counter.getInsertedTasks();
                        counterUpdatedTasks += counter.getUpdatedTasks();

                    }
                    counterOrderSyncUncompleted.setTotal(counterTotal);
                    counterOrderSyncUncompleted.setSkip(counterSkip);
                    counterOrderSyncUncompleted.setInsertedTriggers(counterInsertedTriggers);
                    counterOrderSyncUncompleted.setUpdatedTriggers(counterUpdatedTriggers);
                    counterOrderSyncUncompleted.setInsertedExecutions(counterInsertedExecutions);
                    counterOrderSyncUncompleted.setUpdatedExecutions(counterUpdatedExecutions);
                    counterOrderSyncUncompleted.setInsertedTasks(counterInsertedTasks);
                    counterOrderSyncUncompleted.setUpdatedTasks(counterUpdatedTasks);
                } else {
                    Query<DBItemSchedulerHistoryOrderStepReporting> query = getDbLayer().getSchedulerHistoryOrderStepsQuery(schedulerSession,
                            largeResultFetchSizeScheduler, schedulerId, historyIds);
                    counterOrderSyncUncompleted = synchronizeOrderHistory(method, query, dateToAsMinutes);
                }
            }
        }
    }

    private List<Long> getTaskSyncUncomplitedHistoryIds(String schedulerId) throws Exception {
        String method = "getTaskSyncUncomplitedHistoryIds";
        List<Long> uncompletedIds = new ArrayList<Long>();
        try {
            getDbLayer().getSession().beginTransaction();
            List<Long> result = getDbLayer().getTaskSyncUncomplitedHistoryIds(largeResultFetchSizeReporting, schedulerId);
            getDbLayer().getSession().commit();
            for (int i = 0; i < result.size(); i++) {
                Long historyId = result.get(i);
                if (!uncompletedIds.contains(historyId)) {
                    uncompletedIds.add(historyId);
                }
            }
        } catch (Exception e) {
            try {
                getDbLayer().getSession().rollback();
            } catch (Exception ex) {
                LOGGER.warn(String.format("[%s]%s", method, ex.toString()), ex);
            }
            throw e;
        }
        return uncompletedIds;
    }

    private void synchronizeUncompletedTasks(String schedulerId, Long dateToAsMinutes) throws Exception {
        String method = "synchronizeUncompletedTasks";

        uncompletedTaskHistoryIds = new ArrayList<Long>();
        int count = 0;
        boolean run = true;
        while (run) {
            count++;
            try {
                uncompletedTaskHistoryIds = getTaskSyncUncomplitedHistoryIds(schedulerId);
                run = false;
            } catch (Exception e) {
                handleException(method, e, count);
            }
        }

        int size = uncompletedTaskHistoryIds.size();
        if (isDebugEnabled) {
            LOGGER.debug(String.format("[%s]found %s uncompleted tasks", method, size));
        }
        if (size > 0) {
            if (size > SOSHibernate.LIMIT_IN_CLAUSE) {
                LOGGER.info(String.format("[%s]%s uncompleted tasks > as %s. do split...", method, size, SOSHibernate.LIMIT_IN_CLAUSE));
                int counterTotal = 0;
                int counterSkip = 0;
                int counterInsertedTriggers = 0;
                int counterUpdatedTriggers = 0;
                int counterInsertedExecutions = 0;
                int counterUpdatedExecutions = 0;
                int counterInsertedTasks = 0;
                int counterUpdatedTasks = 0;

                for (int i = 0; i < size; i += SOSHibernate.LIMIT_IN_CLAUSE) {
                    List<Long> subList;
                    if (size > i + SOSHibernate.LIMIT_IN_CLAUSE) {
                        subList = uncompletedTaskHistoryIds.subList(i, (i + SOSHibernate.LIMIT_IN_CLAUSE));
                    } else {
                        subList = uncompletedTaskHistoryIds.subList(i, size);
                    }
                    Query<DBItemSchedulerHistory> query = getDbLayer().getSchedulerHistoryTasksQuery(schedulerSession, largeResultFetchSizeScheduler,
                            schedulerId, subList);
                    CounterSynchronize counter = synchronizeTaskHistory(method, query, TaskSync.UNCOMPLETED, schedulerId, dateToAsMinutes);
                    counterTotal += counter.getTotal();
                    counterSkip += counter.getSkip();
                    counterInsertedTriggers += counter.getInsertedTriggers();
                    counterUpdatedTriggers += counter.getUpdatedTriggers();
                    counterInsertedExecutions += counter.getInsertedExecutions();
                    counterUpdatedExecutions += counter.getUpdatedExecutions();
                    counterInsertedTasks += counter.getInsertedTasks();
                    counterUpdatedTasks += counter.getUpdatedTasks();
                }
                counterTaskSyncUncompleted.setTotal(counterTotal);
                counterTaskSyncUncompleted.setSkip(counterSkip);
                counterTaskSyncUncompleted.setInsertedTriggers(counterInsertedTriggers);
                counterTaskSyncUncompleted.setUpdatedTriggers(counterUpdatedTriggers);
                counterTaskSyncUncompleted.setInsertedExecutions(counterInsertedExecutions);
                counterTaskSyncUncompleted.setUpdatedExecutions(counterUpdatedExecutions);
                counterTaskSyncUncompleted.setInsertedTasks(counterInsertedTasks);
                counterTaskSyncUncompleted.setUpdatedTasks(counterUpdatedTasks);

            } else {
                Query<DBItemSchedulerHistory> query = getDbLayer().getSchedulerHistoryTasksQuery(schedulerSession, largeResultFetchSizeScheduler,
                        schedulerId, uncompletedTaskHistoryIds);
                counterTaskSyncUncompleted = synchronizeTaskHistory(method, query, TaskSync.UNCOMPLETED, schedulerId, dateToAsMinutes);
            }
        }
    }

    private void synchronizeTimePeriodOrders(String schedulerId, Date dateFrom, Date dateTo, Long dateToAsMinutes, String dateFromAsString,
            String dateToAsString) throws Exception {
        String method = "synchronizeTimePeriodOrders";
        if (isDebugEnabled) {
            LOGGER.debug(String.format("[%s][%s to %s UTC]", method, dateFromAsString, dateToAsString));
        }
        Query<DBItemSchedulerHistoryOrderStepReporting> query = getDbLayer().getSchedulerHistoryOrderStepsQuery(schedulerSession,
                largeResultFetchSizeScheduler, schedulerId, dateFrom, dateTo);
        counterOrderSync = synchronizeOrderHistory(method, query, dateToAsMinutes);
    }

    private void synchronizeTimePeriodTasks(String schedulerId, Date dateFrom, Date dateTo, Long dateToAsMinutes, String dateFromAsString,
            String dateToAsString) throws Exception {
        String method = "synchronizeTimePeriodTasks";
        if (isDebugEnabled) {
            LOGGER.debug(String.format("[%s][%s to %s UTC]", method, dateFromAsString, dateToAsString));
        }
        Query<DBItemSchedulerHistory> query = getDbLayer().getSchedulerHistoryTasksQuery(schedulerSession, largeResultFetchSizeScheduler, schedulerId,
                dateFrom, dateTo);
        counterTaskSync = synchronizeTaskHistory(method, query, TaskSync.PERIOD, schedulerId, dateToAsMinutes);
        eventTaskClosedHistoryIds = new ArrayList<Long>();
    }

    private void analyzeEvents(JsonArray events) throws Exception {
        String method = "analyzeEvents";
        eventTaskStartedHistoryIds = new HashMap<Long, TaskStarted>();
        eventTaskClosedHistoryIds = new ArrayList<Long>();
        if (events == null) {
            return;
        }
        try {
            for (int i = 0; i < events.size(); i++) {
                JsonObject jo = events.getJsonObject(i);
                String joType = jo.getString(EventKey.TYPE.name());

                if (joType.equals(EventType.TaskClosed.name())) {
                    JsonValue key = jo.get(EventKey.key.name());
                    if (key != null && key.getValueType().equals(ValueType.OBJECT)) {
                        try {
                            eventTaskClosedHistoryIds.add(Long.parseLong(((JsonObject) key).getString("taskId")));
                        } catch (Throwable e) {
                            LOGGER.error(String.format("[%s]can't get taskId from TaskClosed event: %s", method, e.toString()), e);
                        }
                    }
                } else if (joType.equals(EventType.TaskStarted.name())) {
                    try {
                        TaskStarted ts = new TaskStarted(jo);
                        if (ts.getHistoryId() != null) {
                            eventTaskStartedHistoryIds.put(ts.getHistoryId(), ts);
                        }
                    } catch (Throwable e) {
                        throw new Exception(String.format("TaskStarted event:%s", e.toString()), e);
                    }
                }
            }
        } catch (Throwable e) {
            LOGGER.error(String.format("[%s]exception on get taskId from TaskClosed/TaskStarted events: %s", method, e.toString()), e);
        }

        if (isDebugEnabled) {
            LOGGER.debug(String.format("[%s][TaskStarted=%s][TaskClosed=%s]", method, eventTaskStartedHistoryIds.size(), eventTaskClosedHistoryIds
                    .size()));
        }
    }

    private void synchronizeTaskStartedEvents(String schedulerId, Long dateToAsMinutes) throws Exception {
        String method = "synchronizeTaskStartedEvents";
        counterTaskStartedEventsInserted = 0;
        int size = eventTaskStartedHistoryIds.size();
        if (isDebugEnabled) {
            LOGGER.debug(String.format("[%s][TaskStarted=%s]", method, size));
        }
        if (size > 0) {
            List<Long> eventHistoryIds = eventTaskStartedHistoryIds.keySet().stream().collect(Collectors.toList());
            try {
                getDbLayer().getSession().beginTransaction();
                List<Long> dbHistoryIds = getDbLayer().getTasksHistoryIds(largeResultFetchSizeReporting, schedulerId, eventHistoryIds);
                for (int i = 0; i < eventHistoryIds.size(); i++) {
                    Long historyId = eventHistoryIds.get(i);
                    if (!dbHistoryIds.contains(historyId)) {
                        TaskStarted ts = eventTaskStartedHistoryIds.get(historyId);
                        if (isDebugEnabled) {
                            LOGGER.debug(String.format("[%s][%s]task.historyId=%s", method, ts.getJobPath(), ts.getHistoryId()));
                        }

                        List<Map<String, String>> infos = null;
                        try {
                            infos = getDbLayer().getInventoryJobInfoByJobName(schedulerId, options.current_scheduler_hostname.getValue(),
                                    options.current_scheduler_http_port.value(), ts.getJobPath());
                        } catch (Exception e) {
                            throw new Exception(String.format("error on getInventoryJobInfoByJobName: %s", e.toString()), e);
                        }
                        InventoryInfo inventoryInfo = getInventoryInfo(infos);
                        if (isDebugEnabled) {
                            LOGGER.debug(String.format("[%s][%s][inventory_info]title=%s, isRuntimeDefined=%s, isOrderJob=%s", method, ts
                                    .getJobPath(), inventoryInfo.getTitle(), inventoryInfo.getIsRuntimeDefined(), inventoryInfo.getIsOrderJob()));
                        }
                        if (!inventoryInfo.getIsOrderJob()) {
                            DBItemSchedulerHistory task = new DBItemSchedulerHistory();
                            task.setSpoolerId(schedulerId);
                            task.setId(ts.getHistoryId());
                            task.setClusterMemberId(null);
                            task.setSteps(new Integer(0));
                            task.setJobName(ts.getJobPath());
                            task.setStartTime(ts.getStartTime());
                            task.setEndTime(null);
                            task.setCause("unknown");
                            task.setExitCode(new Integer(0));
                            task.setError(new Boolean(false));
                            task.setErrorCode(null);
                            task.setErrorText(null);
                            task.setAgentUrl(null);
                            getDbLayer().insertTask(task, inventoryInfo, false, false);
                            counterTaskStartedEventsInserted++;
                        }
                    }
                }
                getDbLayer().getSession().commit();
            } catch (Exception e) {
                try {
                    getDbLayer().getSession().rollback();
                } catch (Exception ex) {
                    LOGGER.warn(String.format("[%s]%s", method, ex.toString()), ex);
                }
                LOGGER.error(String.format("[%s]%s", method, e.toString()), e);
            }
        }
    }

    private void synchronizeTaskClosedEvents(String schedulerId, Long dateToAsMinutes) throws Exception {
        String method = "synchronizeTaskClosedEvents";
        int size = eventTaskClosedHistoryIds.size();
        if (size > 0) {
            if (isDebugEnabled) {
                LOGGER.debug(String.format("[%s]process %s TaskClosed events", method, size));
            }
            if (size > SOSHibernate.LIMIT_IN_CLAUSE) {
                LOGGER.info(String.format("[%s]%s TaskClosed events > as %s. do split...", method, size, SOSHibernate.LIMIT_IN_CLAUSE));
                int counterTotal = 0;
                int counterSkip = 0;
                int counterInsertedTriggers = 0;
                int counterUpdatedTriggers = 0;
                int counterInsertedExecutions = 0;
                int counterUpdatedExecutions = 0;
                int counterInsertedTasks = 0;
                int counterUpdatedTasks = 0;

                for (int i = 0; i < size; i += SOSHibernate.LIMIT_IN_CLAUSE) {
                    List<Long> subList;
                    if (size > i + SOSHibernate.LIMIT_IN_CLAUSE) {
                        subList = eventTaskClosedHistoryIds.subList(i, (i + SOSHibernate.LIMIT_IN_CLAUSE));
                    } else {
                        subList = eventTaskClosedHistoryIds.subList(i, size);
                    }
                    Query<DBItemSchedulerHistory> query = getDbLayer().getSchedulerHistoryTasksQuery(schedulerSession, largeResultFetchSizeScheduler,
                            schedulerId, subList);
                    CounterSynchronize counter = synchronizeTaskHistory(method, query, TaskSync.EVENT, schedulerId, dateToAsMinutes);
                    counterTotal += counter.getTotal();
                    counterSkip += counter.getSkip();
                    counterInsertedTriggers += counter.getInsertedTriggers();
                    counterUpdatedTriggers += counter.getUpdatedTriggers();
                    counterInsertedExecutions += counter.getInsertedExecutions();
                    counterUpdatedExecutions += counter.getUpdatedExecutions();
                    counterInsertedTasks += counter.getInsertedTasks();
                    counterUpdatedTasks += counter.getUpdatedTasks();
                }
                counterTaskSyncEvent.setTotal(counterTotal);
                counterTaskSyncEvent.setSkip(counterSkip);
                counterTaskSyncEvent.setInsertedTriggers(counterInsertedTriggers);
                counterTaskSyncEvent.setUpdatedTriggers(counterUpdatedTriggers);
                counterTaskSyncEvent.setInsertedExecutions(counterInsertedExecutions);
                counterTaskSyncEvent.setUpdatedExecutions(counterUpdatedExecutions);
                counterTaskSyncEvent.setInsertedTasks(counterInsertedTasks);
                counterTaskSyncEvent.setUpdatedTasks(counterUpdatedTasks);

            } else {
                Query<DBItemSchedulerHistory> query = getDbLayer().getSchedulerHistoryTasksQuery(schedulerSession, largeResultFetchSizeScheduler,
                        schedulerId, eventTaskClosedHistoryIds);
                counterTaskSyncEvent = synchronizeTaskHistory(method, query, TaskSync.EVENT, schedulerId, dateToAsMinutes);
            }
        }
    }

    private boolean calculateIsSyncCompleted(Date startTime, Date endTime, Long dateToAsMinutes) {
        boolean completed = false;
        if (endTime == null) {
            if (startTime != null) {
                if (maxUncompletedAge > 0) {
                    Long startTimeMinutes = ReportUtil.getDateAsMinutes(startTime);
                    Long diffMinutes = dateToAsMinutes - startTimeMinutes;
                    if (diffMinutes > maxUncompletedAge) {
                        completed = true;
                    }
                }
            }
        } else {
            completed = true;
        }
        return completed;
    }

    private synchronized CounterSynchronize synchronizeTaskHistory(String caller, Query<DBItemSchedulerHistory> query, TaskSync taskSync,
            String schedulerId, Long dateToAsMinutes) throws Exception {
        String method = "synchronizeTaskHistory";
        List<DBItemSchedulerHistory> result = getSchedulerHistoryTasks(query);
        CounterSynchronize counter = null;
        int count = 0;
        boolean run = true;
        while (run) {
            count++;
            try {
                counter = synchronizeTaskHistory(caller, result, taskSync, schedulerId, dateToAsMinutes);
                run = false;
            } catch (Exception e) {
                handleException(method, e, count);
            }
        }
        return counter;
    }

    private void handleException(String callerMethod, Exception e, int count) throws Exception {
        if (count >= MAX_RERUNS) {
            throw e;
        } else {
            Exception lae = SOSHibernate.findLockException(e);
            if (lae == null) {
                throw e;
            } else {
                LOGGER.warn(String.format("[%s]%s occured, wait %ss and try again (%s of %s) ...", callerMethod, lae.getClass().getName(),
                        RERUN_INTERVAL, count, MAX_RERUNS));
                Thread.sleep(RERUN_INTERVAL * 1000);
            }
        }
    }

    private List<DBItemSchedulerHistory> getSchedulerHistoryTasks(Query<DBItemSchedulerHistory> query) throws Exception {
        String method = "getSchedulerHistoryTasks";
        List<DBItemSchedulerHistory> result = null;
        try {
            schedulerSession.beginTransaction();
            result = schedulerSession.getResultList(query);
            schedulerSession.commit();
        } catch (Exception e) {
            try {
                schedulerSession.rollback();
            } catch (Exception ex) {
                LOGGER.warn(String.format("[%s]schedulerConnection %s", method, ex.toString()), ex);
            }
            throw new Exception(String.format("[%s]%s", method, e.toString()), e);
        }
        return result;
    }

    private CounterSynchronize synchronizeTaskHistory(String caller, List<DBItemSchedulerHistory> result, TaskSync taskSync, String schedulerId,
            Long dateToAsMinutes) throws Exception {
        String method = new StringBuilder(caller).append("][synchronizeTaskHistory").toString();
        CounterSynchronize counter = new CounterSynchronize();
        try {
            int counterTotal = 0;
            int counterSkip = 0;
            int counterInserted = 0;
            int counterUpdated = 0;
            int totalSize = result.size();
            if (isDebugEnabled) {
                LOGGER.debug(String.format("[%s]found %s tasks", method, totalSize));
            }
            if (totalSize > 0) {
                DateTime start = new DateTime();
                getDbLayer().getSession().beginTransaction();
                for (int i = 0; i < totalSize; i++) {
                    DBItemSchedulerHistory task = result.get(i);
                    if (task.getJobName().equals("(Spooler)")) {
                        // LOGGER.debug(String.format("%s: %s) skip jobName = %s", method, counterTotal, task.getJobName()));
                        // counterSkip++;
                        continue;
                    }

                    counterTotal++;

                    checkReduceEventTaskStartedHistoryIds(method, task.getId(), task.getJobName());

                    if (taskSync.equals(TaskSync.PERIOD)) {
                        if (eventTaskClosedHistoryIds.contains(task.getId())) {
                            if (isDebugEnabled) {
                                LOGGER.debug(String.format("[%s][%s][skip][%s]was already handled by event TaskClose", method, counterTotal, task
                                        .getJobName()));
                            }
                            counterSkip++;
                            continue;
                        }
                    }

                    boolean syncCompleted = calculateIsSyncCompleted(task.getStartTime(), task.getEndTime(), dateToAsMinutes);
                    DBItemReportTask reportTask = getDbLayer().getTask(schedulerId, task.getId());
                    if (reportTask == null) {
                        if (isDebugEnabled) {
                            LOGGER.debug(String.format("[%s][%s][insert][%s]historyId=%s, cause=%s, syncCompleted=%s", method, counterTotal, task
                                    .getJobName(), task.getId(), task.getCause(), syncCompleted));
                        }
                        boolean isOrder = false;
                        if (task.getCause() != null && task.getCause().equals(EStartCauses.ORDER.value())) {
                            isOrder = true;
                        }
                        List<Map<String, String>> infos = null;
                        try {
                            infos = getDbLayer().getInventoryJobInfoByJobName(schedulerId, options.current_scheduler_hostname.getValue(),
                                    options.current_scheduler_http_port.value(), task.getJobName());
                        } catch (Exception e) {
                            throw new Exception(String.format("error on getInventoryJobInfoByJobName: %s", e.toString()), e);
                        }
                        InventoryInfo inventoryInfo = getInventoryInfo(infos);
                        if (isDebugEnabled) {
                            LOGGER.debug(String.format("[%s][%s][%s][inventory_info]title=%s, isRuntimeDefined=%s, isOrderJob=%s", method,
                                    counterTotal, task.getJobName(), inventoryInfo.getTitle(), inventoryInfo.getIsRuntimeDefined(), inventoryInfo
                                            .getIsOrderJob()));
                        }
                        if (!isOrder && inventoryInfo.getIsOrderJob()) {
                            isOrder = true;
                        }

                        reportTask = getDbLayer().insertTask(task, inventoryInfo, isOrder, syncCompleted);
                        counterInserted++;
                    } else {
                        if (isDebugEnabled) {
                            LOGGER.debug(String.format("[%s][%s][update][%s]id=%s, historyId=%s, cause=%s, syncCompleted=%s", method, counterTotal,
                                    task.getJobName(), reportTask.getId(), task.getId(), task.getCause(), syncCompleted));
                        }
                        boolean updateExecutions = false;
                        if (reportTask.getError() && (reportTask.getExitCode() == null || reportTask.getExitCode().equals(new Integer(0)))) {
                            updateExecutions = true;
                        }

                        getDbLayer().updateTask(reportTask, task, syncCompleted);

                        if (updateExecutions) {
                            int rc = getDbLayer().updateComplitedExecutionsByTask(reportTask);
                            if (isDebugEnabled) {
                                LOGGER.debug(String.format("[%s][%s][updateComplitedExecutionsByTask]reportTaskId=%s, updated executions=%s", method,
                                        counterTotal, reportTask.getId(), rc));
                            }
                        }

                        counterUpdated++;
                    }

                    pluginOnProcess(reportTask);

                    if (taskSync.equals(TaskSync.UNCOMPLETED) && uncompletedTaskHistoryIds.contains(task.getId())) {
                        uncompletedTaskHistoryIds.remove(task.getId());
                    }

                    if (counterTotal % options.log_info_step.value() == 0) {
                        LOGGER.info(String.format("[%s]%s of %s history steps processed ...", method, counterTotal, totalSize));
                    }
                }
                getDbLayer().getSession().commit();
                if (isDebugEnabled) {
                    LOGGER.debug(String.format("[%s]total history tasks=%s, inserted=%s, updated=%s, skip=%s, duration=%s", method, counterTotal,
                            counterInserted, counterUpdated, counterSkip, ReportUtil.getDuration(start, new DateTime())));
                }
            }
            counter.setTotal(counterTotal);
            counter.setSkip(counterSkip);
            counter.setInsertedTriggers(0);
            counter.setUpdatedTriggers(0);
            counter.setInsertedExecutions(0);
            counter.setUpdatedExecutions(0);
            counter.setInsertedTasks(counterInserted);
            counter.setUpdatedTasks(counterUpdated);
        } catch (Exception e) {
            try {
                getDbLayer().getSession().rollback();
            } catch (Exception ex) {
                LOGGER.warn(String.format("[%s]%s", method, ex.toString()), ex);
            }
            throw e;
        }
        return counter;
    }

    private synchronized CounterSynchronize synchronizeOrderHistory(String caller, Query<DBItemSchedulerHistoryOrderStepReporting> query,
            Long dateToAsMinutes) throws Exception {
        String method = "synchronizeOrderHistory";
        List<DBItemSchedulerHistoryOrderStepReporting> result = getSchedulerHistoryOrderSteps(query);
        CounterSynchronize counter = null;
        int count = 0;
        boolean run = true;
        while (run) {
            count++;
            try {
                counter = synchronizeOrderHistory(caller, result, dateToAsMinutes);
                run = false;
            } catch (Exception e) {
                handleException(method, e, count);
            }
        }
        return counter;
    }

    private List<DBItemSchedulerHistoryOrderStepReporting> getSchedulerHistoryOrderSteps(Query<DBItemSchedulerHistoryOrderStepReporting> query)
            throws Exception {
        String method = "getSchedulerHistoryOrderSteps";
        List<DBItemSchedulerHistoryOrderStepReporting> result = null;
        try {
            schedulerSession.beginTransaction();
            result = schedulerSession.getResultList(query);
            schedulerSession.commit();
        } catch (Exception e) {
            try {
                schedulerSession.rollback();
            } catch (Exception ex) {
                LOGGER.warn(String.format("[%s]schedulerConnection %s", method, ex.toString()), ex);
            }
            throw new Exception(String.format("[%s]%s", method, e.toString()), e);
        }
        return result;
    }

    private CounterSynchronize synchronizeOrderHistory(String caller, List<DBItemSchedulerHistoryOrderStepReporting> result, Long dateToAsMinutes)
            throws Exception {
        String method = new StringBuilder(caller).append("][synchronizeOrderHistory").toString();
        CounterSynchronize counter = new CounterSynchronize();
        try {
            Map<Long, DBItemReportTrigger> triggerObjects = new HashMap<Long, DBItemReportTrigger>();
            int counterTotal = 0;
            int counterSkip = 0;
            int counterInsertedTriggers = 0;
            int counterUpdatedTriggers = 0;
            int counterInsertedExecutions = 0;
            int counterUpdatedExecutions = 0;
            int counterInsertedTasks = 0;
            int counterUpdatedTasks = 0;
            int totalSize = result.size();
            if (isDebugEnabled) {
                LOGGER.debug(String.format("[%s]found %s order steps in the scheduler db", method, totalSize));
            }
            if (totalSize > 0) {
                DateTime start = new DateTime();
                getDbLayer().getSession().beginTransaction();
                for (int i = 0; i < totalSize; i++) {
                    counterTotal++;
                    DBItemSchedulerHistoryOrderStepReporting step = result.get(i);
                    if (step.getOrderHistoryId() == null && step.getOrderId() == null && step.getOrderStartTime() == null) {
                        counterSkip++;
                        if (isDebugEnabled) {
                            LOGGER.debug(String.format("[%s][%s][order object is null]step=%s, historyId=%s ", method, counterTotal, step
                                    .getStepState(), step.getStepHistoryId()));
                        }
                        continue;
                    }
                    DBItemReportTask reportTask = null;
                    // e.g. waiting_for_agent
                    if (step.getTaskId() == null && step.getTaskJobName() == null && step.getTaskCause() == null) {
                        if (isDebugEnabled) {
                            LOGGER.debug(String.format("[%s][%s][task object is null]jobChain=%s, order=%s, orderHistoryId=%s, step=%s, taskId=%s ",
                                    method, counterTotal, step.getOrderJobChain(), step.getOrderId(), step.getOrderHistoryId(), step.getStepState(),
                                    step.getStepTaskId()));
                        }
                        reportTask = getDbLayer().getTask(step.getOrderSchedulerId(), step.getStepTaskId());
                        if (reportTask == null || reportTask.getBasename().equals(DBLayerReporting.NOT_FOUNDED_JOB_BASENAME)) {
                            List<Map<String, String>> infos = null;
                            try {
                                infos = getDbLayer().getInventoryJobInfoByJobChain(schedulerId, options.current_scheduler_hostname.getValue(),
                                        options.current_scheduler_http_port.value(), step.getOrderJobChain(), step.getStepState());
                            } catch (Exception e) {
                                throw new Exception(String.format("error on getInventoryJobInfoByJobChain: %s", e.toString()), e);
                            }
                            InventoryInfo taskInventoryInfo = getInventoryInfo(infos);
                            if (reportTask == null) {
                                try {
                                    reportTask = getDbLayer().insertTaskByOrderStep(step, taskInventoryInfo, false);
                                    if (isDebugEnabled) {
                                        LOGGER.debug(String.format("[%s][%s][task inserted][%s]id=%s", method, counterTotal, reportTask.getName(),
                                                reportTask.getId()));
                                    }
                                } catch (Exception e) {
                                    throw new SOSReportingConcurrencyException(e);
                                }
                                counterInsertedTasks++;
                            } else {
                                if (isDebugEnabled) {
                                    LOGGER.debug(String.format("[%s][%s][task already exist][%s]id=%s. try to find the correct name ...", method,
                                            counterTotal, reportTask.getName(), reportTask.getId()));
                                }
                                if (taskInventoryInfo != null && taskInventoryInfo.getName() != null) {
                                    reportTask.setFolder(ReportUtil.getFolderFromName(taskInventoryInfo.getName()));
                                    reportTask.setName(taskInventoryInfo.getName());
                                    reportTask.setBasename(ReportUtil.getBasenameFromName(taskInventoryInfo.getName()));
                                    reportTask.setTitle(taskInventoryInfo.getTitle());
                                    reportTask.setModified(ReportUtil.getCurrentDateTime());
                                    getDbLayer().getSession().update(reportTask);
                                    if (isDebugEnabled) {
                                        LOGGER.debug(String.format("[%s][%s][task updated][%s]id=%s", method, counterTotal, reportTask.getName(),
                                                reportTask.getId()));
                                    }
                                }
                            }
                        } else {
                            if (isDebugEnabled) {
                                LOGGER.debug(String.format("[%s][%s][task already exist][%s]id=%s", method, counterTotal, reportTask.getName(),
                                        reportTask.getId()));
                            }
                        }
                        step.setTaskId(reportTask.getHistoryId());
                        step.setTaskStartTime(reportTask.getStartTime());
                        step.setTaskJobName(reportTask.getName());
                        step.setTaskClusterMemberId(reportTask.getClusterMemberId());
                        step.setTaskAgentUrl(reportTask.getAgentUrl());
                        step.setTaskCause(reportTask.getCause());

                        checkReduceEventTaskStartedHistoryIds(method, reportTask.getHistoryId(), reportTask.getName());
                    }
                    if (isDebugEnabled) {
                        LOGGER.debug(String.format("[%s][%s]orderHistoryId=%s, jobChain=%s, orderId=%s, step=%s, stepState=%s, taskJobName=%s",
                                method, counterTotal, step.getOrderHistoryId(), step.getOrderJobChain(), step.getOrderId(), step.getStepStep(), step
                                        .getStepState(), step.getTaskJobName()));
                    }
                    DBItemReportTrigger rt = null;
                    if (triggerObjects.containsKey(step.getOrderHistoryId())) {
                        rt = triggerObjects.get(step.getOrderHistoryId());
                        if (isDebugEnabled) {
                            LOGGER.debug(String.format("[%s][%s][use trigger]id=%s, name=%s", method, counterTotal, rt.getId(), rt.getName()));
                        }
                    } else {
                        boolean syncCompleted = calculateIsSyncCompleted(step.getOrderStartTime(), step.getOrderEndTime(), dateToAsMinutes);
                        rt = getDbLayer().getTrigger(step.getOrderSchedulerId(), step.getOrderHistoryId());
                        if (rt == null) {
                            List<Map<String, String>> infos = null;
                            try {
                                infos = getDbLayer().getInventoryOrderInfoByJobChain(step.getOrderSchedulerId(), options.current_scheduler_hostname
                                        .getValue(), options.current_scheduler_http_port.value(), step.getOrderId(), step.getOrderJobChain());
                            } catch (Exception e) {
                                throw new Exception(String.format("error on getInventoryOrderInfoByJobChain: %s", e.toString()), e);
                            }
                            InventoryInfo triggerInventoryInfo = getInventoryInfo(infos);
                            if (isDebugEnabled) {
                                if (isDebugEnabled) {
                                    LOGGER.debug(String.format("[%s][%s][inventory_info][%s][%s]title=%s, isRuntimeDefined=%s", method, counterTotal,
                                            step.getOrderId(), step.getOrderJobChain(), triggerInventoryInfo.getTitle(), triggerInventoryInfo
                                                    .getIsRuntimeDefined()));
                                }
                            }
                            String startCause = step.getTaskCause();
                            if (startCause.equals(EStartCauses.ORDER.value())) {
                                String inventoryStartCause = getDbLayer().getInventoryJobChainStartCause(step.getOrderSchedulerId(),
                                        this.options.current_scheduler_hostname.getValue(), this.options.current_scheduler_http_port.value(),
                                        ReportUtil.normalizeDbItemPath(step.getOrderJobChain()));
                                if (!SOSString.isEmpty(inventoryStartCause)) {
                                    startCause = inventoryStartCause;
                                }
                            }

                            try {
                                rt = getDbLayer().insertTrigger(step, triggerInventoryInfo, startCause, syncCompleted);
                            } catch (Exception e) {
                                throw new Exception(String.format("error on insertTrigger: %s", e.toString()), e);
                            }
                            if (isDebugEnabled) {
                                LOGGER.debug(String.format("[%s][%s][trigger created]id=%s, name=%s", method, counterTotal, rt.getId(), rt
                                        .getName()));
                            }
                            counterInsertedTriggers++;
                        } else {
                            if (rt.getSyncCompleted() != syncCompleted || step.getOrderEndTime() != null) {
                                try {
                                    rt = getDbLayer().updateTrigger(rt, step, syncCompleted);
                                } catch (Exception e) {
                                    throw new Exception(String.format("error on updateTrigger: %s", e.toString()), e);
                                }
                                if (isDebugEnabled) {
                                    LOGGER.debug(String.format("[%s][%s][trigger updated]id=%s, name=%s", method, counterTotal, rt.getId(), rt
                                            .getName()));
                                }
                                counterUpdatedTriggers++;
                            }
                        }
                        triggerObjects.put(step.getOrderHistoryId(), rt);
                    }

                    DBItemReportExecution re = getDbLayer().getExecution(step.getOrderSchedulerId(), step.getStepTaskId(), rt.getId(), step
                            .getStepStep());
                    if (re == null) {
                        if (reportTask == null) {
                            reportTask = getDbLayer().getTask(step.getOrderSchedulerId(), step.getStepTaskId());
                            if (reportTask == null) {// e.g. task created by splitter
                                List<Map<String, String>> infos = null;
                                try {
                                    infos = getDbLayer().getInventoryJobInfoByJobName(schedulerId, options.current_scheduler_hostname.getValue(),
                                            options.current_scheduler_http_port.value(), ReportUtil.normalizeDbItemPath(step.getTaskJobName()));
                                } catch (Exception e) {
                                    throw new Exception(String.format("error on getInventoryJobInfoByJobName: %s", e.toString()), e);
                                }
                                InventoryInfo inventoryInfo = getInventoryInfo(infos);
                                boolean syncCompleted = calculateIsSyncCompleted(step.getTaskStartTime(), step.getTaskEndTime(), dateToAsMinutes);
                                try {
                                    reportTask = getDbLayer().insertTaskByOrderStep(step, inventoryInfo, syncCompleted);
                                } catch (Exception e) {
                                    throw new SOSReportingConcurrencyException(e);
                                }
                                if (isDebugEnabled) {
                                    LOGGER.debug(String.format("[%s][%s][reportTask created]id=%s, name=%s", method, counterTotal, reportTask.getId(),
                                            reportTask.getName()));
                                }
                                counterInsertedTasks++;
                            }
                        }
                        try {
                            boolean syncCompleted = calculateIsSyncCompleted(step.getStepStartTime(), step.getStepEndTime(), dateToAsMinutes);
                            re = getDbLayer().insertExecution(step, rt, reportTask, syncCompleted);
                        } catch (Exception e) {
                            throw new Exception(String.format("error on insertExecution: %s", e.toString()), e);
                        }
                        if (isDebugEnabled) {
                            LOGGER.debug(String.format("[%s][%s][execution created]id=%s, rt.id=%s, name=%s", method, counterTotal, re.getId(), rt
                                    .getId(), re.getName()));
                        }
                        counterInsertedExecutions++;
                    } else {
                        try {
                            boolean syncCompleted = calculateIsSyncCompleted(step.getStepStartTime(), step.getStepEndTime(), dateToAsMinutes);
                            re = getDbLayer().updateExecution(re, step, syncCompleted);
                        } catch (Exception e) {
                            throw new Exception(String.format("error on updateExecution: %s", e.toString()));
                        }
                        if (isDebugEnabled) {
                            LOGGER.debug(String.format("[%s][%s][execution updated]id=%s, rt.id=%s, name=%s", method, counterTotal, re.getId(), rt
                                    .getId(), re.getId()));
                        }
                        counterUpdatedExecutions++;
                    }
                    re.setTaskStartTime(step.getTaskStartTime());
                    re.setTaskEndTime(step.getTaskEndTime());
                    if (isDebugEnabled) {
                        LOGGER.debug(String.format("[%s][%s]step.getStepStep=%s, rt.getResultSteps=%s", method, counterTotal, step.getStepStep(), rt
                                .getResultSteps()));
                    }
                    if (step.getStepStep() >= rt.getResultSteps()) {
                        try {
                            rt = getDbLayer().updateTriggerResults(rt, re, step);
                        } catch (Exception e) {
                            throw new Exception(String.format("error on updateTriggerResults: %s", e.toString()), e);
                        }
                        if (isDebugEnabled) {
                            LOGGER.debug(String.format("[%s][%s][trigger result updated]id=%s, name=%s, resultSteps=%s", method, counterTotal, rt
                                    .getId(), rt.getName(), rt.getResultSteps()));
                        }
                        triggerObjects.put(step.getOrderHistoryId(), rt);
                        counterUpdatedTriggers++;
                    }
                    pluginOnProcess(rt, re, true);

                    if (counterTotal % options.log_info_step.value() == 0) {
                        LOGGER.info(String.format("[%s]%s of %s history steps processed ...", method, counterTotal, totalSize));

                        triggerObjects = null;
                        triggerObjects = new HashMap<Long, DBItemReportTrigger>();
                        triggerObjects.put(step.getOrderHistoryId(), rt);
                    }
                }
                getDbLayer().getSession().commit();
                if (isDebugEnabled) {
                    LOGGER.debug(String.format("[%s]duration=%s", method, ReportUtil.getDuration(start, new DateTime())));
                }
            }
            counter.setTotal(counterTotal);
            counter.setSkip(counterSkip);
            counter.setInsertedTriggers(counterInsertedTriggers);
            counter.setUpdatedTriggers(counterUpdatedTriggers);
            counter.setInsertedExecutions(counterInsertedExecutions);
            counter.setUpdatedExecutions(counterUpdatedExecutions);
            counter.setInsertedTasks(counterInsertedTasks);
            counter.setUpdatedTasks(counterUpdatedTasks);

        } catch (Exception e) {
            try {
                getDbLayer().getSession().rollback();
            } catch (Exception ex) {
                LOGGER.warn(String.format("[%s]rollback %s", method, ex.toString()), ex);
            }
            throw e;
        }
        return counter;
    }

    private synchronized void synchronizeNotFounded(Long dateToAsMinutes) throws Exception {
        String method = "synchronizeNotFounded";
        counterTaskSyncNotFounded = new CounterSynchronize();
        int count = 0;
        boolean run = true;
        while (run) {
            count++;
            try {
                counterTaskSyncNotFounded = synchronizeNotFoundedTasks(dateToAsMinutes);
                run = false;
            } catch (Exception e) {
                handleException(method, e, count);
            }
        }
    }

    private void checkReduceEventTaskStartedHistoryIds(String caller, Long taskHistoryId, String jobName) {
        if (eventTaskStartedHistoryIds.containsKey(taskHistoryId)) {
            eventTaskStartedHistoryIds.remove(taskHistoryId);
            if (isDebugEnabled) {
                LOGGER.debug(String.format("[%s][TaskStarted=%s]%s(%s) removed", caller, eventTaskStartedHistoryIds.size(), taskHistoryId, jobName));
            }
        }
    }

    private CounterSynchronize synchronizeNotFoundedTasks(Long dateToAsMinutes) throws Exception {
        String method = "synchronizeNotFoundedTasks";
        CounterSynchronize counter = new CounterSynchronize();
        int size = uncompletedTaskHistoryIds.size();
        int counterUpdatedTasks = 0;
        int counterUpdatedExecutions = 0;
        int counterSkip = 0;
        if (isDebugEnabled) {
            LOGGER.debug(String.format("[%s]size=%s", method, size));
        }
        if (size > 0) {
            try {
                getDbLayer().getSession().beginTransaction();
                for (Long historyId : uncompletedTaskHistoryIds) {
                    DBItemReportTask reportTask = getDbLayer().getTask(schedulerId, historyId);
                    if (reportTask == null) {
                        if (isDebugEnabled) {
                            LOGGER.debug(String.format("[%s]not found reportTasks for schedulerId=%s, historyId=%s", method, schedulerId, historyId));
                        }
                    } else {
                        checkReduceEventTaskStartedHistoryIds(method, reportTask.getHistoryId(), reportTask.getName());

                        if (isDebugEnabled) {
                            LOGGER.debug(String.format("[%s]found reportTask.id=%s (historyId=%s)", method, reportTask.getId(), historyId));
                        }
                        if (reportTask.getIsOrder()) {
                            List<DBItemReportExecution> executions = getDbLayer().getExecutionsByTask(reportTask.getId());
                            if (executions == null || executions.size() == 0) {
                                if (isDebugEnabled) {
                                    LOGGER.debug(String.format("[%s]not found executions for taskId=%s", method, reportTask.getId()));
                                }
                            } else {
                                if (isDebugEnabled) {
                                    LOGGER.debug(String.format("[%s]found %s executions for taskId=%s", method, executions.size(), reportTask
                                            .getId()));
                                }
                                boolean doUpdate = false;
                                if (reportTask.getBasename().equals(DBLayerReporting.NOT_FOUNDED_JOB_BASENAME)) {
                                    DBItemReportExecution firstExecution = executions.get(0);
                                    List<Map<String, String>> infos = null;
                                    try {
                                        infos = getDbLayer().getInventoryJobInfoByJobChain(schedulerId, options.current_scheduler_hostname.getValue(),
                                                options.current_scheduler_http_port.value(), firstExecution.getFolder(), firstExecution.getState());
                                    } catch (Exception e) {
                                        throw new Exception(String.format("error on getInventoryJobInfoByJobChain: %s", e.toString()), e);
                                    }
                                    InventoryInfo taskInventoryInfo = getInventoryInfo(infos);
                                    if (taskInventoryInfo != null && taskInventoryInfo.getName() != null) {
                                        reportTask.setFolder(ReportUtil.getFolderFromName(taskInventoryInfo.getName()));
                                        reportTask.setName(taskInventoryInfo.getName());
                                        reportTask.setBasename(ReportUtil.getBasenameFromName(taskInventoryInfo.getName()));
                                        reportTask.setTitle(taskInventoryInfo.getTitle());
                                        doUpdate = true;
                                    }
                                }

                                DBItemReportExecution lastExecutionWithEndTime = null;
                                Date endTime = reportTask.getEndTime();
                                boolean syncCompleted = false;
                                for (DBItemReportExecution execution : executions) {
                                    if (isDebugEnabled) {
                                        LOGGER.debug(String.format("[%s]execution (id=%s, error=%s, syncCompleted=%s, endTime=%s)", method, execution
                                                .getId(), execution.getError(), execution.getSyncCompleted(), execution.getEndTime()));
                                    }
                                    if (execution.getSyncCompleted()) {
                                        syncCompleted = true;
                                    }
                                    if (execution.getEndTime() != null) {
                                        if (endTime == null) {
                                            lastExecutionWithEndTime = execution;
                                        } else {
                                            if (execution.getEndTime().getTime() > endTime.getTime()) {
                                                lastExecutionWithEndTime = execution;
                                            }
                                        }
                                    }

                                    if (execution.getBasename().equals(DBLayerReporting.NOT_FOUNDED_JOB_BASENAME) && doUpdate) {
                                        execution.setFolder(reportTask.getFolder());
                                        execution.setName(reportTask.getName());
                                        execution.setBasename(reportTask.getBasename());
                                        execution.setTitle(reportTask.getTitle());
                                        execution.setModified(ReportUtil.getCurrentDateTime());
                                        getDbLayer().getSession().update(execution);
                                        counterUpdatedExecutions++;

                                        execution.setTaskStartTime(reportTask.getStartTime());
                                        execution.setTaskEndTime(reportTask.getEndTime());
                                        pluginOnProcess(null, execution, true);
                                    }
                                }
                                if (syncCompleted && calculateIsSyncCompleted(reportTask.getStartTime(), null, dateToAsMinutes)) {
                                    reportTask.setSyncCompleted(true);
                                    doUpdate = true;
                                }
                                if (lastExecutionWithEndTime != null) {
                                    if (isDebugEnabled) {
                                        LOGGER.debug(String.format("[%s]found last execution (id=%s, error=%s)", method, lastExecutionWithEndTime
                                                .getId(), lastExecutionWithEndTime.getError()));
                                    }
                                    if (calculateIsSyncCompleted(lastExecutionWithEndTime.getStartTime(), null, dateToAsMinutes)) {
                                        reportTask.setEndTime(lastExecutionWithEndTime.getEndTime());
                                        reportTask.setSyncCompleted(true);
                                    }
                                    if (!reportTask.getError()) {
                                        reportTask.setError(lastExecutionWithEndTime.getError());
                                        reportTask.setErrorCode(lastExecutionWithEndTime.getErrorCode());
                                        reportTask.setErrorText(lastExecutionWithEndTime.getErrorText());
                                    }
                                    lastExecutionWithEndTime.setTaskStartTime(reportTask.getStartTime());
                                    lastExecutionWithEndTime.setTaskEndTime(reportTask.getEndTime());
                                    pluginOnProcess(null, lastExecutionWithEndTime, true);
                                    doUpdate = true;
                                }

                                if (doUpdate) {
                                    reportTask.setModified(ReportUtil.getCurrentDateTime());
                                    getDbLayer().getSession().update(reportTask);
                                    counterUpdatedTasks++;
                                } else {
                                    counterSkip++;
                                }
                            }
                        } else {
                            if (isDebugEnabled) {
                                LOGGER.debug(String.format("[%s]task has isOrder=0", method));
                            }
                            reportTask.setSyncCompleted(true);
                            reportTask.setModified(ReportUtil.getCurrentDateTime());
                            getDbLayer().getSession().update(reportTask);
                            counterUpdatedTasks++;
                        }
                    }
                }
                getDbLayer().getSession().commit();
            } catch (Exception e) {
                try {
                    getDbLayer().getSession().rollback();
                } catch (Exception ex) {
                    LOGGER.warn(String.format("[%s]rollback %s", method, ex.toString()), ex);
                }
                throw e;
            }
        }
        counter.setTotal(size);
        counter.setSkip(counterSkip);
        counter.setUpdatedTasks(counterUpdatedTasks);
        counter.setUpdatedExecutions(counterUpdatedExecutions);
        return counter;
    }

    private synchronized void updateNotificationRest() throws Exception {
        String method = "updateNotificationRest";

        if (notificationPlugin == null) {
            return;
        }
        if (notificationPlugin.hasModelInitError()) {
            notificationPlugin = null;
            return;
        }
        if (endedOrderTasks4notification.size() == 0) {
            LOGGER.debug(String.format("[%s][skip]size=0", method));
            return;
        }
        if (isDebugEnabled) {
            LOGGER.debug(String.format("[%s]size=%s", method, endedOrderTasks4notification.size()));
        }
        int count = 0;
        boolean run = true;
        while (run) {
            count++;
            try {
                updateNotificationTaskData();
                run = false;
            } catch (Exception e) {
                handleException(method, e, count);
            }
        }
    }

    private void updateNotificationTaskData() throws Exception {
        String method = "updateNotificationTaskData";
        try {
            getDbLayer().getSession().beginTransaction();
            for (DBItemReportTask task : endedOrderTasks4notification.values()) {
                List<DBItemReportExecution> executions = getDbLayer().getExecutionsByTask(task.getId());
                if (executions == null || executions.size() == 0) {
                    if (isDebugEnabled) {
                        LOGGER.debug(String.format("[%s][skip]not found executions for taskId=%s", method, task.getId()));
                    }
                    continue;
                }
                for (DBItemReportExecution execution : executions) {
                    execution.setTaskStartTime(task.getStartTime());
                    execution.setTaskEndTime(task.getEndTime());
                    pluginOnProcess(null, execution, false);
                }
            }
            getDbLayer().getSession().commit();
        } catch (Exception e) {
            try {
                getDbLayer().getSession().rollback();
            } catch (Exception ex) {
                LOGGER.warn(String.format("[%s]rollback %s", method, ex.toString()), ex);
            }
            throw e;
        }
    }

    private InventoryInfo getInventoryInfo(List<Map<String, String>> infos) {
        String method = "getInventoryInfo";
        InventoryInfo item = new InventoryInfo();
        item.setSchedulerId(schedulerId);
        item.setHostname(options.current_scheduler_hostname.getValue());
        item.setPort(options.current_scheduler_http_port.value());

        item.setName(null);
        item.setTitle(null);
        item.setIsRuntimeDefined(false);

        item.setIsOrderJob(false);

        item.setClusterType(null);
        item.setUrl(null);
        item.setOrdering(new Integer(0));

        if (infos != null && infos.size() > 0) {
            try {
                for (int i = 0; i < infos.size(); i++) {
                    Map<String, String> row = infos.get(i);
                    if (isDebugEnabled) {
                        LOGGER.debug(String.format("[%s]row=%s", method, row));
                    }
                    item.setName(SOSString.isEmpty(row.get("name")) ? null : row.get("name"));
                    item.setTitle(SOSString.isEmpty(row.get("title")) ? null : row.get("title"));
                    item.setIsRuntimeDefined(row.get("is_runtime_defined").equals("1"));
                    if (row.size() > 3) {
                        if (row.containsKey("is_order_job")) {
                            item.setIsOrderJob(row.get("is_order_job").equals("1"));
                        }
                        if (row.size() > 4) {
                            if (row.containsKey("cluster_type")) {
                                item.setClusterType(SOSString.isEmpty(row.get("cluster_type")) ? null : row.get("cluster_type"));
                            }
                            if (row.containsKey("url")) {
                                item.setUrl(SOSString.isEmpty(row.get("url")) ? null : row.get("url"));
                            }
                            if (row.containsKey("ordering")) {
                                item.setOrdering(SOSString.isEmpty(row.get("ordering")) ? null : Integer.parseInt(row.get("ordering")));
                            }
                            if (item.getOrdering() != null && item.getOrdering().equals(new Integer(1))) {
                                break;
                            }
                        }
                    }
                }

            } catch (Exception ex) {
                LOGGER.warn(String.format("can't create InventoryInfo object: %s", ex.toString()));
            }
        }
        return item;
    }

    private void initCounters() throws Exception {
        counterOrderSync = new CounterSynchronize();
        counterOrderSyncUncompleted = new CounterSynchronize();

        counterTaskSync = new CounterSynchronize();
        counterTaskSyncUncompleted = new CounterSynchronize();
        counterTaskSyncEvent = new CounterSynchronize();
        counterTaskSyncNotFounded = new CounterSynchronize();
        counterTaskStartedEventsInserted = 0;

        endedOrderTasks4notification = new HashMap<Long, DBItemReportTask>();
        eventTaskClosedHistoryIds = new ArrayList<Long>();
    }

    private void setChangedSummary() throws Exception {
        isChanged = false;
        isOrdersChanged = false;
        isTasksChanged = false;
        if (counterOrderSync.getInsertedTriggers() > 0 || counterOrderSync.getUpdatedTriggers() > 0 || counterOrderSync.getInsertedExecutions() > 0
                || counterOrderSync.getUpdatedExecutions() > 0 || counterOrderSyncUncompleted.getInsertedTriggers() > 0 || counterOrderSyncUncompleted
                        .getUpdatedTriggers() > 0 || counterOrderSyncUncompleted.getInsertedExecutions() > 0 || counterOrderSyncUncompleted
                                .getUpdatedExecutions() > 0 || counterTaskSyncNotFounded.getUpdatedExecutions() > 0) {
            isOrdersChanged = true;
            isChanged = true;
        }
        if (counterOrderSync.getInsertedTasks() > 0 || counterOrderSyncUncompleted.getInsertedTasks() > 0 || counterTaskSync.getInsertedTasks() > 0
                || counterTaskSync.getUpdatedTasks() > 0 || counterTaskSyncUncompleted.getInsertedTasks() > 0 || counterTaskSyncUncompleted
                        .getUpdatedTasks() > 0 || counterTaskSyncNotFounded.getUpdatedTasks() > 0 || counterTaskSyncEvent.getInsertedTasks() > 0
                || counterTaskSyncEvent.getUpdatedTasks() > 0 || counterTaskStartedEventsInserted > 0) {
            isTasksChanged = true;
            isChanged = true;
        }
    }

    private void logSummary(String from, String to, DateTime start) throws Exception {
        String method = "logSummary";
        if (isChanged) {
            String range = "task";
            if (isTasksChanged) {
                LOGGER.info(String.format(
                        "[%s to %s UTC][%s][new]history tasks=%s, tasks(inserted=%s, updated=%s), skip=%s [old]total=%s, tasks(inserted=%s, updated=%s), skip=%s [event TaskClose]total=%s, tasks(inserted=%s, updated=%s), skip=%s [event TaskStarted]inserted=%s [not founded]total=%s, tasks(updated=%s), skip=%s",
                        from, to, range, counterTaskSync.getTotal(), counterTaskSync.getInsertedTasks(), counterTaskSync.getUpdatedTasks(),
                        counterTaskSync.getSkip(), counterTaskSyncUncompleted.getTotal(), counterTaskSyncUncompleted.getInsertedTasks(),
                        counterTaskSyncUncompleted.getUpdatedTasks(), counterTaskSyncUncompleted.getSkip(), counterTaskSyncEvent.getTotal(),
                        counterTaskSyncEvent.getInsertedTasks(), counterTaskSyncEvent.getUpdatedTasks(), counterTaskSyncEvent.getSkip(),
                        counterTaskStartedEventsInserted, counterTaskSyncNotFounded.getTotal(), counterTaskSyncNotFounded.getUpdatedTasks(),
                        counterTaskSyncNotFounded.getSkip()));
            } else {
                LOGGER.info(String.format("[%s to %s UTC][%s] 0 changes", from, to, range));
            }
            range = "order";
            if (isOrdersChanged) {
                LOGGER.info(String.format(
                        "[%s to %s UTC][%s][new]history steps=%s, triggers(inserted=%s, updated=%s), executions(inserted=%s, updated=%s), tasks(inserted=%s), skip=%s [old]total=%s, triggers(inserted=%s, updated), executions(inserted=%s, updated=%s), tasks(inserted=%s), skip=%s",
                        from, to, range, counterOrderSync.getTotal(), counterOrderSync.getInsertedTriggers(), counterOrderSync.getUpdatedTriggers(),
                        counterOrderSync.getInsertedExecutions(), counterOrderSync.getUpdatedExecutions(), counterOrderSync.getInsertedTasks(),
                        counterOrderSync.getSkip(), counterOrderSyncUncompleted.getTotal(), counterOrderSyncUncompleted.getInsertedTriggers(),
                        counterOrderSyncUncompleted.getUpdatedTriggers(), counterOrderSyncUncompleted.getInsertedExecutions(),
                        counterOrderSyncUncompleted.getUpdatedExecutions(), counterOrderSyncUncompleted.getInsertedTasks(),
                        counterOrderSyncUncompleted.getSkip()));
            } else {
                LOGGER.info(String.format("[%s to %s UTC][%s] 0 changes", from, to, range));
            }

        } else {
            LOGGER.info(String.format("[%s to %s UTC] 0 changes", from, to));
        }
        if (isDebugEnabled) {
            LOGGER.debug(String.format("%s: duration=%s", method, ReportUtil.getDuration(start, new DateTime())));
        }
    }

    private Date getDateFrom(DBItemReportVariable reportingVariable, Date dateTo, String dateToAsString) throws Exception {
        String method = "getDateFrom";
        Long currentMaxAge = new Long(maxHistoryAge);
        Date storedDateFrom = ReportUtil.getDateFromString(getWithoutLocked(reportingVariable.getTextValue()));
        Date dateFrom = null;
        if (options.force_max_history_age.value()) {
            if (isDebugEnabled) {
                LOGGER.debug(String.format("[%s][set dateFrom=null]force_max_history_age=true", method));
            }
            dateFrom = null;
        } else {
            Long dateFromAsMinutes = ReportUtil.getDateAsMinutes(storedDateFrom);
            Long dateToAsMinutes = ReportUtil.getDateAsMinutes(dateTo);
            Long diffMinutes = dateToAsMinutes - dateFromAsMinutes;
            if (diffMinutes > currentMaxAge) {
                Long countHistoryTasks = getDbLayer().getCountSchedulerHistoryTasks(schedulerSession, schedulerId, storedDateFrom);
                if (countHistoryTasks > maxHistoryTasks) {
                    dateFrom = null;
                    LOGGER.info(String.format(
                            "[%s][set dateFrom=null]diffMinutes=%s > currentMaxAge=%sm and countHistoryTasks=%s > maxHistoryTasks=%s", method,
                            diffMinutes, currentMaxAge, countHistoryTasks, maxHistoryTasks));
                } else {
                    dateFrom = storedDateFrom;
                    if (isDebugEnabled) {
                        LOGGER.debug(String.format(
                                "[%s][use storedDateFrom]diffMinutes=%s > currentMaxAge=%sm and countHistoryTasks=%s <= maxHistoryTasks=%s", method,
                                diffMinutes, currentMaxAge, countHistoryTasks, maxHistoryTasks));
                    }
                }
            } else {
                dateFrom = storedDateFrom;
                if (isDebugEnabled) {
                    LOGGER.debug(String.format("[%s][use storedDateFrom]diffMinutes=%s(dateTo=%s-dateFrom=%s) <= currentMaxAge=%sm", method,
                            diffMinutes, dateToAsString, ReportUtil.getDateAsString(dateFrom), currentMaxAge));
                }
            }
            // dateFrom = storedDateFrom;
        }
        if (dateFrom == null) {
            dateFrom = ReportUtil.getDateTimeMinusMinutes(dateTo, currentMaxAge);
            if (isDebugEnabled) {
                LOGGER.debug(String.format("[%s][set dateFrom from null]dateFrom=%s (%s-%s)", method, ReportUtil.getDateAsString(dateFrom),
                        dateToAsString, currentMaxAge));
            }
        }
        return dateFrom;
    }

    private void registerPlugin() {
        if (options.execute_notification_plugin.value()) {
            notificationPlugin = new FactNotificationPlugin();
        }
    }

    private void pluginOnInit(PluginMailer mailer, Path configDirectory) {
        if (notificationPlugin != null) {
            notificationPlugin.init(getDbLayer().getSession(), mailer, configDirectory);
        }
    }

    private void pluginOnProcess(DBItemReportTrigger trigger, DBItemReportExecution execution, boolean reduceList4Notifications) {
        String method = "pluginOnProcess";
        if (notificationPlugin == null || execution == null) {
            return;
        }
        if (notificationPlugin.hasModelInitError()) {
            notificationPlugin = null;
            return;
        }
        if (trigger == null) {
            try {
                trigger = getDbLayer().getTrigger(execution.getTriggerId());
            } catch (SOSHibernateException e) {
                if (isDebugEnabled) {
                    LOGGER.debug(String.format("[%s]cannot get trigger for triggerId=%s: %s", method, execution.getTriggerId(), e.toString()));
                }
                return;
            }
            if (trigger == null) {
                if (isDebugEnabled) {
                    LOGGER.debug(String.format("[%s]not found trigger with triggerId=%s", method, execution.getTriggerId()));
                }
                return;
            }
        }

        if (reduceList4Notifications) {
            if (endedOrderTasks4notification.containsKey(execution.getHistoryId())) {
                endedOrderTasks4notification.remove(execution.getHistoryId());
                if (isDebugEnabled) {
                    LOGGER.debug(String.format("[pluginOnProcess][endedOrderTasks4notification][removed]task history id=%s, jobName=%s", execution
                            .getHistoryId(), execution.getName()));
                }
            }
        }
        if (isDebugEnabled) {
            LOGGER.debug(String.format("[%s]trigger.id=%s, execution.id=%s", method, trigger.getId(), execution.getId()));
        }
        notificationPlugin.process(notificationPlugin.convert2OrderExecution(trigger, execution), true, true);
    }

    private void pluginOnProcess(DBItemReportTask task) {
        if (notificationPlugin == null || task == null) {
            return;
        }
        if (notificationPlugin.hasModelInitError()) {
            notificationPlugin = null;
            return;
        }
        if (task.getIsOrder()) {
            if (task.getEndTime() != null && !endedOrderTasks4notification.containsKey(task.getHistoryId())) {
                endedOrderTasks4notification.put(task.getHistoryId(), task);
                if (isDebugEnabled) {
                    LOGGER.debug(String.format("[pluginOnProcess][endedOrderTasks4notification][added]task history id=%s, jobName=%s", task
                            .getHistoryId(), task.getName()));
                }
            }
            return;
        }
        if (isDebugEnabled) {
            LOGGER.debug(String.format("[pluginOnProcess]task.id=%s", task.getId()));
        }
        notificationPlugin.process(notificationPlugin.convert2StandaloneExecution(task), false, true);
    }

    private void pluginOnExit() {
        if (notificationPlugin != null) {
            notificationPlugin = null;
        }
    }

    public boolean isChanged() {
        return isChanged;
    }

    public boolean isTasksChanged() {
        return isTasksChanged;
    }

    public boolean isOrdersChanged() {
        return isOrdersChanged;
    }

    public boolean isLocked() {
        return isLocked;
    }
}