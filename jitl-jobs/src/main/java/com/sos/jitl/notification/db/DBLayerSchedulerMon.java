package com.sos.jitl.notification.db;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sos.hibernate.classes.SOSHibernate;
import com.sos.hibernate.classes.SOSHibernateSession;
import com.sos.hibernate.exceptions.SOSHibernateException;

import sos.util.SOSString;

public class DBLayerSchedulerMon extends DBLayer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DBLayerSchedulerMon.class);
    private static final String FROM_WITH_SPACES = " from ";
    private static final String FROM = "from ";
    private static final String WHERE_N2_EQUALS_N1 = "  where n2.orderHistoryId = n1.orderHistoryId ";
    private static final String DELETE_FROM = "delete from ";
    private static final String DELETE_COUNT = "deleted %s = %s";
    private static final String ORDER_HISTORY_ID = "orderHistoryId";
    private static final String SERVICE_NAME = "serviceName";
    private static final String SYSTEM_ID = "systemId";
    private static final String UPDATE = "update ";

    /** result rerun interval in seconds */
    private static final long RERUN_INTERVAL = 2;
    private static final int MAX_RERUNS = 3;

    public DBLayerSchedulerMon(SOSHibernateSession sess) {
        super(sess);
    }

    public void cleanupNotifications(Date date) throws SOSHibernateException {
        String hql = String.format("delete from %s where created <= :date", DBITEM_SCHEDULER_MON_NOTIFICATIONS);
        Query<?> query = getSession().createQuery(hql).setParameter("date", date);
        int count = getSession().executeUpdate(query);
        LOGGER.info(String.format(DELETE_COUNT, DBITEM_SCHEDULER_MON_NOTIFICATIONS, count));

        String whereNotificationIdNotIn = "where notificationId not in (select id from " + DBITEM_SCHEDULER_MON_NOTIFICATIONS + ")";

        hql = String.format("delete from %s %s", DBITEM_SCHEDULER_MON_RESULTS, whereNotificationIdNotIn);
        query = getSession().createQuery(hql);
        count = getSession().executeUpdate(query);
        LOGGER.info(String.format(DELETE_COUNT, DBITEM_SCHEDULER_MON_RESULTS, count));

        hql = String.format("delete from %s %s", DBITEM_SCHEDULER_MON_CHECKS, whereNotificationIdNotIn);
        query = getSession().createQuery(hql);
        count = getSession().executeUpdate(query);
        LOGGER.info(String.format(DELETE_COUNT, DBITEM_SCHEDULER_MON_CHECKS, count));

        hql = String.format("delete from %s %s", DBITEM_SCHEDULER_MON_SYSNOTIFICATIONS, whereNotificationIdNotIn);
        query = getSession().createQuery(hql);
        int countS1 = getSession().executeUpdate(query);

        hql = String.format("delete from %s where checkId > 0 and checkId not in (select id from %s)", DBITEM_SCHEDULER_MON_SYSNOTIFICATIONS,
                DBITEM_SCHEDULER_MON_CHECKS);
        query = getSession().createQuery(hql);
        int countS2 = getSession().executeUpdate(query);
        count = countS1 + countS2;
        LOGGER.info(String.format(DELETE_COUNT, DBITEM_SCHEDULER_MON_SYSNOTIFICATIONS, count));

    }

    public int resetAcknowledged(String systemId, String serviceName) throws SOSHibernateException {
        StringBuilder hql = new StringBuilder(UPDATE);
        hql.append(DBITEM_SCHEDULER_MON_SYSNOTIFICATIONS);
        hql.append(" set acknowledged = 1");
        hql.append(" where lower(systemId) = :systemId");
        if (!SOSString.isEmpty(serviceName)) {
            hql.append(" and serviceName =:serviceName");
        }
        Query<?> query = getSession().createQuery(hql.toString());
        query.setParameter(SYSTEM_ID, systemId.toLowerCase());
        if (!SOSString.isEmpty(serviceName)) {
            query.setParameter(SERVICE_NAME, serviceName);
        }
        return getSession().executeUpdate(query);
    }

    public List<DBItemSchedulerMonNotifications> getNotificationOrderSteps(Long notificationId) throws SOSHibernateException {
        String method = "getNotificationOrderSteps";
        StringBuilder hql = new StringBuilder(FROM);
        hql.append(DBITEM_SCHEDULER_MON_NOTIFICATIONS).append(" n1");
        hql.append(" where exists (");
        hql.append("   select n2.orderHistoryId ");
        hql.append("   from ");
        hql.append(DBITEM_SCHEDULER_MON_NOTIFICATIONS).append(" n2");
        hql.append("   where n1.orderHistoryId = n2.orderHistoryId");
        hql.append("   and n2.id = :id ");
        hql.append(" )");
        hql.append(" order by n1.step");

        Query<DBItemSchedulerMonNotifications> query = getSession().createQuery(hql.toString());
        query.setParameter("id", notificationId);
        return executeQueryList(method, query);
    }

    public List<DBItemSchedulerMonResults> getNotificationResults(Long notificationId) throws SOSHibernateException {
        String method = "getNotificationResults";
        String hql = String.format("from %s r where r.notificationId = :id", DBITEM_SCHEDULER_MON_RESULTS);

        Query<DBItemSchedulerMonResults> query = getSession().createQuery(hql);
        query.setParameter("id", notificationId);
        return executeQueryList(method, query);
    }

    public List<DBItemSchedulerMonChecks> getSchedulerMonChecksForSetTimer(Optional<Integer> fetchSize) throws SOSHibernateException {
        String method = "getSchedulerMonChecksForSetTimer";
        String hql = String.format("from %s where checked = 0", DBITEM_SCHEDULER_MON_CHECKS);

        Query<DBItemSchedulerMonChecks> query = getSession().createQuery(hql);
        query.setReadOnly(true);
        if (fetchSize.isPresent()) {
            query.setFetchSize(fetchSize.get());
        }
        return executeQueryList(method, query);
    }

    public void setNotificationCheck(DBItemSchedulerMonChecks check, Date stepFromStartTime, Date stepToEndTime, String text, String resultIds)
            throws SOSHibernateException {
        check.setStepFromStartTime(stepFromStartTime);
        check.setStepToEndTime(stepToEndTime);
        check.setChecked(true);
        check.setCheckText(text);
        check.setResultIds(SOSString.isEmpty(resultIds) ? null : resultIds);
        check.setModified(DBLayer.getCurrentDateTime());
        getSession().update(check);
    }

    public void setNotificationCheckForRerun(DBItemSchedulerMonChecks check, Date stepFromStartTime, Date stepToEndTime, String text,
            String resultIds) throws SOSHibernateException {
        check.setStepFromStartTime(stepFromStartTime);
        check.setStepToEndTime(stepToEndTime);
        check.setChecked(false);
        check.setCheckText("1");
        check.setResultIds(SOSString.isEmpty(resultIds) ? null : resultIds);
        check.setModified(DBLayer.getCurrentDateTime());
        getSession().update(check);
    }

    public int setRecovered(Long orderHistoryId, Long step, String state) throws SOSHibernateException {
        StringBuilder hql = new StringBuilder(FROM);
        hql.append(DBITEM_SCHEDULER_MON_NOTIFICATIONS);
        hql.append(" where step <= :step");
        hql.append(" and orderHistoryId = :orderHistoryId");
        hql.append(" and orderStepState = :state");
        hql.append(" and error = true");

        Query<DBItemSchedulerMonNotifications> query = getSession().createQuery(hql.toString());
        query.setReadOnly(true);
        query.setParameter("orderHistoryId", orderHistoryId);
        query.setParameter("step", step);
        query.setParameter("state", state);

        List<DBItemSchedulerMonNotifications> r = getSession().getResultList(query);
        if (r != null && r.size() > 0) {
            ArrayList<Long> ids = new ArrayList<Long>();
            for (int i = 0; i < r.size(); i++) {
                ids.add(r.get(i).getId());
            }

            hql = new StringBuilder(UPDATE);
            hql.append(DBITEM_SCHEDULER_MON_NOTIFICATIONS);
            hql.append(" set recovered=true");
            hql.append(" where id in :ids");

            query = getSession().createQuery(hql.toString());
            query.setParameterList("ids", ids);
            return getSession().executeUpdate(query);
        }
        return 0;
    }

    public DBItemSchedulerMonNotifications getNotification(String schedulerId, boolean standalone, Long taskId, Long step, Long orderHistoryId,
            boolean checkDummyMaxStep) throws SOSHibernateException {
        String method = "getNotification";
        StringBuilder hql = new StringBuilder(FROM);
        hql.append(DBITEM_SCHEDULER_MON_NOTIFICATIONS);
        hql.append(" where schedulerId = :schedulerId");
        hql.append(" and standalone = :standalone");
        hql.append(" and taskId = :taskId");
        if (checkDummyMaxStep) {
            hql.append(" and step in :steps");
        } else {
            hql.append(" and step = :step");
        }
        hql.append(" and orderHistoryId = :orderHistoryId ");

        Query<DBItemSchedulerMonNotifications> query = getSession().createQuery(hql.toString());
        query.setParameter("schedulerId", schedulerId);
        query.setParameter("standalone", standalone);
        query.setParameter("taskId", taskId);
        if (checkDummyMaxStep) {
            ArrayList<Long> steps = new ArrayList<Long>();
            steps.add(step);
            steps.add(DBLayer.NOTIFICATION_DUMMY_MAX_STEP);
            query.setParameterList("steps", steps);
        } else {
            query.setParameter("step", step);
        }
        query.setParameter(ORDER_HISTORY_ID, orderHistoryId);

        List<DBItemSchedulerMonNotifications> result = executeQueryList(method, query);
        if (result != null && result.size() > 0) {
            int resultSize = result.size();
            if (resultSize > 1) {
                for (int i = 0; i < resultSize; i++) {
                    DBItemSchedulerMonNotifications r = result.get(i);
                    if (r.getStep().equals(DBLayer.NOTIFICATION_DUMMY_MAX_STEP)) {
                        return r;
                    }
                }
            }
            return result.get(0);
        }
        return null;
    }

    public List<DBItemSchedulerMonNotifications> getNotificationsByState(String schedulerId, boolean standalone, Long taskId, Long orderHistoryId,
            String state) throws SOSHibernateException {
        String method = "getNotificationsByState";
        StringBuilder hql = new StringBuilder(FROM);
        hql.append(DBITEM_SCHEDULER_MON_NOTIFICATIONS);
        hql.append(" where schedulerId = :schedulerId");
        hql.append(" and standalone = :standalone");
        hql.append(" and taskId = :taskId");
        hql.append(" and orderHistoryId = :orderHistoryId ");
        hql.append(" and orderStepState = :state");

        Query<DBItemSchedulerMonNotifications> query = getSession().createQuery(hql.toString());
        query.setParameter("schedulerId", schedulerId);
        query.setParameter("standalone", standalone);
        query.setParameter("taskId", taskId);
        query.setParameter(ORDER_HISTORY_ID, orderHistoryId);
        query.setParameter("state", state);
        return executeQueryList(method, query);
    }

    public DBItemSchedulerMonNotifications getNotification(Long id) throws SOSHibernateException {
        return getSession().get(DBItemSchedulerMonNotifications.class, id);
    }

    public List<DBItemSchedulerMonSystemNotifications> getSystemNotifications4NotifyAgain(String systemId) throws SOSHibernateException {
        String method = "getSystemNotifications4NotifyAgain";
        StringBuilder hql = new StringBuilder(FROM);
        hql.append(DBITEM_SCHEDULER_MON_SYSNOTIFICATIONS);
        hql.append(" where lower(systemId) = :systemId");
        hql.append(" and maxNotifications = false");
        hql.append(" and acknowledged = false");
        Query<DBItemSchedulerMonSystemNotifications> query = getSession().createQuery(hql.toString());
        query.setParameter(SYSTEM_ID, systemId.toLowerCase());
        return executeQueryList(method, query);
    }

    public List<DBItemSchedulerMonNotifications> getNotifications4NotifyNew(String systemId) throws SOSHibernateException {
        String method = "getNotifications4NotifyNew";
        StringBuilder hql = new StringBuilder("select max(sn.notificationId) ");
        hql.append(FROM);
        hql.append(DBITEM_SCHEDULER_MON_SYSNOTIFICATIONS).append(" sn");
        hql.append(" where lower(sn.systemId) = :systemId");
        Query<Long> q = getSession().createQuery(hql.toString());
        q.setParameter(SYSTEM_ID, systemId.toLowerCase());
        Long maxNotificationId = getSession().getSingleValue(q);

        Query<DBItemSchedulerMonNotifications> query = null;
        if (maxNotificationId == null || maxNotificationId.equals(new Long(0))) {
            hql = new StringBuilder(FROM).append(DBITEM_SCHEDULER_MON_NOTIFICATIONS);
            query = getSession().createQuery(hql.toString());
        } else {
            hql = new StringBuilder(FROM).append(DBITEM_SCHEDULER_MON_NOTIFICATIONS).append(" n");
            hql.append(" where n.id > :maxNotificationId");
            query = getSession().createQuery(hql.toString());
            query.setParameter("maxNotificationId", new Long(maxNotificationId));
        }
        return executeQueryList(method, query);
    }

    public List<DBItemSchedulerMonSystemNotifications> getSystemNotifications(String systemId, String serviceName, Long notificationId)
            throws SOSHibernateException {
        String method = "getSystemNotifications";
        StringBuilder hql = new StringBuilder(FROM);
        hql.append(DBITEM_SCHEDULER_MON_SYSNOTIFICATIONS);
        hql.append(" where notificationId = :notificationId");
        if (serviceName != null) {
            hql.append(" and serviceName = :serviceName ");
        }
        hql.append(" and lower(systemId) = :systemId");

        Query<DBItemSchedulerMonSystemNotifications> query = getSession().createQuery(hql.toString());
        query.setParameter("notificationId", notificationId);
        query.setParameter(SYSTEM_ID, systemId.toLowerCase());
        if (serviceName != null) {
            query.setParameter(SERVICE_NAME, serviceName);
        }
        return executeQueryList(method, query);
    }

    public DBItemSchedulerMonSystemNotifications getSystemNotification(String systemId, String serviceName, Long notificationId, Long checkId,
            Long objectType, boolean onSuccess, String stepFrom, String stepTo, String returnCodeFrom, String returnCodeTo)
            throws SOSHibernateException {
        String method = "getSystemNotification";
        LOGGER.debug(String.format(
                "%s: systemId = %s, serviceName = %s, notificationId = %s, checkId = %s, objectType = %s, onSuccess = %s, stepFrom = %s, stepTo = %s, returnCodeFrom = %s, returnCodeTo = %s",
                method, systemId, serviceName, notificationId, checkId, objectType, onSuccess, stepFrom, stepTo, returnCodeFrom, returnCodeTo));
        StringBuilder hql = new StringBuilder(FROM);
        hql.append(DBITEM_SCHEDULER_MON_SYSNOTIFICATIONS);
        hql.append(" where notificationId = :notificationId");
        hql.append(" and checkId = :checkId");
        hql.append(" and objectType = :objectType");
        hql.append(" and serviceName = :serviceName");
        hql.append(" and lower(systemId) = :systemId");
        hql.append(" and success = :success");
        if (stepFrom != null) {
            hql.append(" and stepFrom = :stepFrom");
        }
        if (stepTo != null) {
            hql.append(" and stepTo = :stepTo");
        }
        if (returnCodeFrom != null) {
            hql.append(" and returnCodeFrom = :returnCodeFrom");
        }
        if (returnCodeTo != null) {
            hql.append(" and returnCodeTo = :returnCodeTo");
        }
        Query<DBItemSchedulerMonSystemNotifications> query = getSession().createQuery(hql.toString());
        query.setParameter("notificationId", notificationId);
        query.setParameter("checkId", checkId);
        query.setParameter("objectType", objectType);
        query.setParameter(SERVICE_NAME, serviceName);
        query.setParameter(SYSTEM_ID, systemId.toLowerCase());
        query.setParameter("success", onSuccess);
        if (stepFrom != null) {
            query.setParameter("stepFrom", stepFrom);
        }
        if (stepTo != null) {
            query.setParameter("stepTo", stepTo);
        }
        if (returnCodeFrom != null) {
            query.setParameter("returnCodeFrom", returnCodeFrom);
        }
        if (returnCodeTo != null) {
            query.setParameter("returnCodeTo", returnCodeTo);
        }

        List<DBItemSchedulerMonSystemNotifications> result = executeQueryList(method, query);
        if (!result.isEmpty()) {
            return result.get(0);
        } else {
            LOGGER.debug(String.format(
                    "%s: SystemNotification not found for systemId = %s, serviceName = %s, notificationId = %s, checkId = %s, objectType = %s, onSuccess = %s, "
                            + "stepFrom = %s, stepTo = %s, returnCodeFrom = %s, returnCodeTo = %s", method, systemId, serviceName, notificationId,
                    checkId, objectType, onSuccess, stepFrom, stepTo, returnCodeFrom, returnCodeTo));
        }
        return null;

    }

    public DBItemSchedulerMonResults createResult(Long notificationId, String name, String value) {
        DBItemSchedulerMonResults item = new DBItemSchedulerMonResults();
        item.setNotificationId(notificationId);
        item.setName(name);
        item.setValue(value);
        item.setCreated(DBLayer.getCurrentDateTime());
        item.setModified(DBLayer.getCurrentDateTime());
        return item;
    }

    public List<DBItemSchedulerMonChecks> getChecksForNotifyTimer(Optional<Integer> fetchSize) throws SOSHibernateException {
        String method = "getChecksForNotifyTimer";
        String hql = String.format("from %s where checked = 1", DBITEM_SCHEDULER_MON_CHECKS);

        Query<DBItemSchedulerMonChecks> query = getSession().createQuery(hql);
        query.setReadOnly(true);
        if (fetchSize.isPresent()) {
            query.setFetchSize(fetchSize.get());
        }
        return executeQueryList(method, query);
    }

    public DBItemSchedulerMonNotifications getNotificationFirstStep(DBItemSchedulerMonNotifications notification) throws SOSHibernateException {
        String method = "getNotificationFirstStep";
        StringBuilder hql = new StringBuilder(FROM);
        hql.append(DBITEM_SCHEDULER_MON_NOTIFICATIONS).append(" n");
        hql.append(" where n.orderHistoryId = :orderHistoryId");
        hql.append(" and n.step = 1");

        Query<DBItemSchedulerMonNotifications> query = getSession().createQuery(hql.toString());
        query.setParameter(ORDER_HISTORY_ID, notification.getOrderHistoryId());
        List<DBItemSchedulerMonNotifications> result = executeQueryList(method, query);
        if (!result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    public DBItemSchedulerMonNotifications getNotificationByStep(Long orderHistoryId, Long step) throws SOSHibernateException {
        String method = "getNotificationByStep";
        StringBuilder hql = new StringBuilder(FROM);
        hql.append(DBITEM_SCHEDULER_MON_NOTIFICATIONS).append(" n");
        hql.append(" where n.orderHistoryId = :orderHistoryId");
        hql.append(" and n.step = :step");

        Query<DBItemSchedulerMonNotifications> query = getSession().createQuery(hql.toString());
        query.setParameter(ORDER_HISTORY_ID, orderHistoryId);
        query.setParameter("step", step);
        List<DBItemSchedulerMonNotifications> result = executeQueryList(method, query);
        if (!result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    public List<DBItemSchedulerMonNotifications> getPreviousErrorNotifications(DBItemSchedulerMonNotifications notification)
            throws SOSHibernateException {
        String method = "getPreviousErrorNotifications";
        StringBuilder hql = new StringBuilder(FROM);
        hql.append(DBITEM_SCHEDULER_MON_NOTIFICATIONS).append(" n");
        hql.append(" where n.orderHistoryId = :orderHistoryId");
        hql.append(" and n.error = true");
        hql.append(" and n.step < :step");

        Query<DBItemSchedulerMonNotifications> query = getSession().createQuery(hql.toString());
        query.setParameter(ORDER_HISTORY_ID, notification.getOrderHistoryId());
        query.setParameter("step", notification.getStep());
        return executeQueryList(method, query);
    }

    public DBItemSchedulerMonNotifications getNotificationsLastStep(DBItemSchedulerMonNotifications notification, boolean orderCompleted)
            throws SOSHibernateException {
        String method = "getNotificationsLastStep";
        LOGGER.debug(String.format("%s: orderHistoryId = %s, orderCompleted = %s", method, notification.getOrderHistoryId(), orderCompleted));
        StringBuilder hql = new StringBuilder(FROM);
        hql.append(DBITEM_SCHEDULER_MON_NOTIFICATIONS).append(" n1");
        hql.append(" where n1.orderHistoryId = :orderHistoryId");
        hql.append(" and n1.step = ");
        hql.append(" (select max(n2.step) ");
        hql.append(FROM_WITH_SPACES);
        hql.append(DBITEM_SCHEDULER_MON_NOTIFICATIONS).append(" n2 ");
        hql.append(WHERE_N2_EQUALS_N1);
        hql.append(" ) ");
        if (orderCompleted) {
            hql.append(" and n1.orderEndTime is not null");
        }

        Query<DBItemSchedulerMonNotifications> query = getSession().createQuery(hql.toString());
        query.setParameter(ORDER_HISTORY_ID, notification.getOrderHistoryId());
        query.setReadOnly(true);
        List<DBItemSchedulerMonNotifications> result = executeQueryList(method, query);
        if (!result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    private <T> List<T> executeQueryList(String callerMethodName, Query<T> query) throws SOSHibernateException {
        List<T> result = null;
        int count = 0;
        boolean run = true;
        while (run) {
            count++;
            try {
                result = getSession().getResultList(query);
                run = false;
            } catch (Exception e) {
                if (count >= MAX_RERUNS) {
                    throw e;
                } else {
                    Throwable te = SOSHibernate.findLockException(e);
                    if (te == null) {
                        throw e;
                    } else {
                        LOGGER.warn(String.format("%s: %s occured, wait %ss and try again (%s of %s) ...", callerMethodName, te.getClass().getName(),
                                RERUN_INTERVAL, count, MAX_RERUNS));
                        try {
                            Thread.sleep(RERUN_INTERVAL * 1000);
                        } catch (InterruptedException e1) {
                        }
                    }
                }
            }
        }
        return result;
    }

    public List<DBItemSchedulerMonNotifications> getOrderNotifications(Optional<Integer> fetchSize, Long orderHistoryId)
            throws SOSHibernateException {
        String method = "getOrderNotifications";
        StringBuilder hql = new StringBuilder(FROM);
        hql.append(DBITEM_SCHEDULER_MON_NOTIFICATIONS);
        hql.append(" where orderHistoryId = :orderHistoryId");
        hql.append(" order by step");

        Query<DBItemSchedulerMonNotifications> query = getSession().createQuery(hql.toString());
        query.setReadOnly(true);
        if (fetchSize.isPresent()) {
            query.setFetchSize(fetchSize.get());
        }
        query.setParameter(ORDER_HISTORY_ID, orderHistoryId);
        return executeQueryList(method, query);
    }

    public int removeCheck(Long checkId) throws SOSHibernateException {
        String hql = String.format("delete from %s where id = :id", DBITEM_SCHEDULER_MON_CHECKS);
        Query<?> query = getSession().createQuery(hql);
        query.setParameter("id", checkId);
        return getSession().executeUpdate(query);
    }

    public DBItemSchedulerMonChecks getCheck(Long notificationId) throws SOSHibernateException {
        String method = "getCheck";
        String hql = String.format("from %s where notificationId = :notificationId", DBITEM_SCHEDULER_MON_CHECKS);
        Query<DBItemSchedulerMonChecks> query = getSession().createQuery(hql);
        query.setReadOnly(true);
        query.setParameter("notificationId", notificationId);

        List<DBItemSchedulerMonChecks> results = executeQueryList(method, query);
        if (results != null && results.size() > 0) {
            return results.get(0);
        }
        return null;
    }

    public DBItemSchedulerMonChecks createCheck(String name, DBItemSchedulerMonNotifications notification, String stepFrom, String stepTo,
            Date stepFromStartTime, Date stepToEndTime, Long objectType) throws SOSHibernateException {

        Long notificationId = notification.getId();
        // NULL wegen batch Insert bei den Datenbanken, die kein Autoincrement
        // haben (Oracle ...)
        DBItemSchedulerMonChecks item = null;
        if (notificationId == null || notificationId.equals(new Long(0))) {
            item = new DBItemSchedulerMonChecks();
            item.setName(name);

            notificationId = new Long(0);
            item.setResultIds(notification.getSchedulerId() + ";" + (notification.getStandalone() ? "true" : "false") + ";" + notification.getTaskId()
                    + ";" + notification.getStep() + ";" + notification.getOrderHistoryId());
            item.setNotificationId(notificationId);
            item.setStepFrom(stepFrom);
            item.setStepTo(stepTo);
            item.setStepFromStartTime(stepFromStartTime);
            item.setStepToEndTime(stepToEndTime);
            item.setChecked(false);
            item.setObjectType(objectType);
            item.setCreated(DBLayer.getCurrentDateTime());
            item.setModified(DBLayer.getCurrentDateTime());

            getSession().save(item);
        } else {
            item = getCheck(notificationId);
            if (item == null) {
                item = new DBItemSchedulerMonChecks();
                item.setName(name);
                item.setNotificationId(notificationId);
                item.setStepFrom(stepFrom);
                item.setStepTo(stepTo);
                item.setStepFromStartTime(stepFromStartTime);
                item.setStepToEndTime(stepToEndTime);
                item.setChecked(false);
                item.setObjectType(objectType);
                item.setCreated(DBLayer.getCurrentDateTime());
                item.setModified(DBLayer.getCurrentDateTime());
                getSession().save(item);
            } else {
                item.setStepFrom(stepFrom);
                item.setStepTo(stepTo);
                item.setStepFromStartTime(stepFromStartTime);
                item.setStepToEndTime(stepToEndTime);
                item.setModified(DBLayer.getCurrentDateTime());
                item.setObjectType(objectType);
                getSession().update(item);
            }
        }
        return item;
    }

    public DBItemSchedulerMonSystemNotifications createSystemNotification(String systemId, String serviceName, Long notificationId, Long checkId,
            String returnCodeFrom, String returnCodeTo, Long objectType, String stepFrom, String stepTo, Date stepFromStartTime, Date stepToEndTime,
            Long currentNotification, Long notifications, boolean acknowledged, boolean recovered, boolean success) {
        DBItemSchedulerMonSystemNotifications item = new DBItemSchedulerMonSystemNotifications();
        item.setSystemId(systemId);
        item.setServiceName(serviceName);
        item.setNotificationId(notificationId);
        item.setCheckId(checkId);
        item.setReturnCodeFrom(returnCodeFrom);
        item.setReturnCodeTo(returnCodeTo);
        item.setObjectType(objectType);
        item.setStepFrom(stepFrom);
        item.setStepTo(stepTo);
        item.setStepFromStartTime(stepFromStartTime);
        item.setStepToEndTime(stepToEndTime);
        item.setMaxNotifications(false);
        item.setCurrentNotification(currentNotification);
        item.setNotifications(notifications);
        item.setAcknowledged(acknowledged);
        item.setRecovered(recovered);
        item.setSuccess(success);
        item.setCreated(DBLayer.getCurrentDateTime());
        item.setModified(DBLayer.getCurrentDateTime());
        return item;
    }

    public void deleteDummySystemNotification(String systemId) throws SOSHibernateException {
        StringBuffer hql = new StringBuffer(DELETE_FROM);
        hql.append(DBITEM_SCHEDULER_MON_SYSNOTIFICATIONS);
        hql.append(" where objectType = :objectType");
        hql.append(" and lower(systemId) = :systemId");

        Query<?> query = getSession().createQuery(hql.toString());
        query.setParameter("objectType", DBLayer.NOTIFICATION_OBJECT_TYPE_DUMMY);
        query.setParameter("systemId", systemId.toLowerCase());
        getSession().executeUpdate(query);
    }

    public DBItemSchedulerMonSystemNotifications createDummySystemNotification(String systemId, Long notificationId) {
        String serviceName = DBLayer.DEFAULT_EMPTY_NAME;
        Long checkId = new Long(0);
        String returnCodeFrom = DBLayer.DEFAULT_EMPTY_NAME;
        String returnCodeTo = DBLayer.DEFAULT_EMPTY_NAME;
        Long objectType = DBLayer.NOTIFICATION_OBJECT_TYPE_DUMMY;
        String stepFrom = DBLayer.DEFAULT_EMPTY_NAME;
        String stepTo = DBLayer.DEFAULT_EMPTY_NAME;
        Date stepFromStartTime = null;
        Date stepToEndTime = null;
        Long currentNotification = new Long(0);
        Long notifications = new Long(0);
        boolean acknowledged = false;
        boolean recovered = false;
        boolean success = false;

        DBItemSchedulerMonSystemNotifications sm = createSystemNotification(systemId, serviceName, notificationId, checkId, returnCodeFrom,
                returnCodeTo, objectType, stepFrom, stepTo, stepFromStartTime, stepToEndTime, currentNotification, notifications, acknowledged,
                recovered, success);
        sm.setMaxNotifications(true);
        return sm;
    }

    public DBItemSchedulerMonNotifications createNotification(String schedulerId, boolean standalone, Long taskId, Long step, Long orderHistoryId,
            String jobChainName, String jobChainTitle, String orderId, String orderTitle, Date orderStartTime, Date orderEndTime,
            String orderStepState, Date orderStepStartTime, Date orderStepEndTime, String jobName, String jobTitle, Date taskStartTime,
            Date taskEndTime, boolean recovered, Long returnCode, String agentUrl, String clusterMemberId, boolean error, String errorCode,
            String errorText) throws Exception {
        DBItemSchedulerMonNotifications item = new DBItemSchedulerMonNotifications();
        // set unique key
        item.setSchedulerId(schedulerId);
        item.setStandalone(standalone);
        item.setTaskId(taskId);
        item.setStep(step);
        item.setOrderHistoryId(orderHistoryId);
        // set others
        item.setJobChainName(jobChainName);
        item.setJobChainTitle(jobChainTitle);
        item.setOrderId(orderId);
        item.setOrderTitle(orderTitle);
        item.setOrderStartTime(orderStartTime);
        item.setOrderEndTime(orderEndTime);
        item.setOrderStepState(orderStepState);
        item.setOrderStepStartTime(orderStepStartTime);
        item.setOrderStepEndTime(orderStepEndTime);
        item.setJobName(jobName);
        item.setJobTitle(jobTitle);
        item.setTaskStartTime(taskStartTime);
        item.setTaskEndTime(taskEndTime);
        item.setRecovered(recovered);
        item.setReturnCode(returnCode);
        item.setAgentUrl(agentUrl);
        item.setClusterMemberId(clusterMemberId);
        item.setError(error);
        item.setErrorCode(errorCode);
        item.setErrorText(errorText);
        item.setCreated(DBLayer.getCurrentDateTime());
        item.setModified(DBLayer.getCurrentDateTime());
        return item;
    }
}
