package com.sos.jitl.inventory.db;

import java.util.Date;
import java.util.List;

import javax.persistence.TemporalType;

import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sos.hibernate.classes.DbItem;
import com.sos.hibernate.classes.SOSHibernateSession;
import com.sos.hibernate.exceptions.SOSHibernateException;
import com.sos.jitl.inventory.helper.ObjectType;
import com.sos.jitl.reporting.db.DBItemCalendar;
import com.sos.jitl.reporting.db.DBItemInventoryAgentCluster;
import com.sos.jitl.reporting.db.DBItemInventoryAgentClusterMember;
import com.sos.jitl.reporting.db.DBItemInventoryAgentInstance;
import com.sos.jitl.reporting.db.DBItemInventoryAppliedLock;
import com.sos.jitl.reporting.db.DBItemInventoryCalendarUsage;
import com.sos.jitl.reporting.db.DBItemInventoryFile;
import com.sos.jitl.reporting.db.DBItemInventoryInstance;
import com.sos.jitl.reporting.db.DBItemInventoryJob;
import com.sos.jitl.reporting.db.DBItemInventoryJobChain;
import com.sos.jitl.reporting.db.DBItemInventoryJobChainNode;
import com.sos.jitl.reporting.db.DBItemInventoryLock;
import com.sos.jitl.reporting.db.DBItemInventoryOperatingSystem;
import com.sos.jitl.reporting.db.DBItemInventoryOrder;
import com.sos.jitl.reporting.db.DBItemInventoryProcessClass;
import com.sos.jitl.reporting.db.DBItemInventorySchedule;
import com.sos.jitl.reporting.db.DBLayer;
import com.sos.jitl.reporting.helper.ReportUtil;

public class DBLayerInventory extends DBLayer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DBLayerInventory.class);

    public DBLayerInventory(SOSHibernateSession connection) {
        super(connection);
    }

    public DBItemInventoryInstance getInventoryInstance(String schedulerId, String schedulerHost, Integer schedulerPort)
            throws SOSHibernateException {
        StringBuilder sql = new StringBuilder("from ");
        sql.append(DBITEM_INVENTORY_INSTANCES);
        sql.append(" where lower(schedulerId) = :schedulerId");
        sql.append(" and lower(hostname) = :hostname");
        sql.append(" and port = :port");
        Query<DBItemInventoryInstance> query = getSession().createQuery(sql.toString());
        query.setParameter("schedulerId", schedulerId.toLowerCase());
        query.setParameter("hostname", schedulerHost.toLowerCase());
        query.setParameter("port", schedulerPort);
        List<DBItemInventoryInstance> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    public DBItemInventoryInstance getInventoryInstance(String schedulerHost, Integer schedulerPort) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder("from ");
        sql.append(DBITEM_INVENTORY_INSTANCES);
        sql.append(" where lower(hostname) = :hostname");
        sql.append(" and port = :port");
        sql.append(" order by modified desc");
        Query<DBItemInventoryInstance> query = getSession().createQuery(sql.toString());
        query.setParameter("hostname", schedulerHost.toLowerCase());
        query.setParameter("port", schedulerPort);
        List<DBItemInventoryInstance> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    public DBItemInventoryInstance getInventoryInstance(Long id) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder("from ");
        sql.append(DBITEM_INVENTORY_INSTANCES);
        sql.append(" where id = :id");
        Query<DBItemInventoryInstance> query = getSession().createQuery(sql.toString());
        query.setParameter("id", id);
        return getSession().getSingleResult(query);
    }
    
    public DBItemInventoryInstance getInventoryInstance(String url) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder("from ");
        sql.append(DBITEM_INVENTORY_INSTANCES);
        sql.append(" where url = :url");
        sql.append(" order by modified desc");
        Query<DBItemInventoryInstance> query = getSession().createQuery(sql.toString());
        query.setParameter("url", url.toLowerCase());
        List<DBItemInventoryInstance> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }
    
    public DBItemInventoryInstance getInventorySupervisorInstance(String commandUrl) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder("from ");
        sql.append(DBITEM_INVENTORY_INSTANCES);
        sql.append(" where lower(commandUrl) = :commandUrl order by modified desc");
        Query<DBItemInventoryInstance> query = getSession().createQuery(sql.toString());
        query.setParameter("commandUrl", commandUrl.toLowerCase());
        List<DBItemInventoryInstance> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }
    
    public DBItemInventoryJob getInventoryJob(Long instanceId, String name) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder("from ");
        sql.append(DBITEM_INVENTORY_JOBS);
        sql.append(" where name = :name");
        sql.append(" and instanceId = :instanceId");
        Query<DBItemInventoryJob> query = getSession().createQuery(sql.toString());
        query.setParameter("name", name);
        query.setParameter("instanceId", instanceId);
        List<DBItemInventoryJob> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    public DBItemInventoryJob getInventoryJobCaseInsensitive(Long instanceId, String name) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder("from ");
        sql.append(DBITEM_INVENTORY_JOBS);
        sql.append(" where lower(name) = :name");
        sql.append(" and instanceId = :instanceId");
        Query<DBItemInventoryJob> query = getSession().createQuery(sql.toString());
        query.setParameter("name", name.toLowerCase());
        query.setParameter("instanceId", instanceId);
        List<DBItemInventoryJob> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    public DBItemInventoryJobChain getInventoryJobChain(Long instanceId, String name) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder("from ");
        sql.append(DBITEM_INVENTORY_JOB_CHAINS);
        sql.append(" where name = :name");
        sql.append(" and instanceId = :instanceId");
        Query<DBItemInventoryJobChain> query = getSession().createQuery(sql.toString());
        query.setParameter("name", name);
        query.setParameter("instanceId", instanceId);
        List<DBItemInventoryJobChain> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    public DBItemInventoryJobChain getInventoryJobChainCaseInsensitive(Long instanceId, String name) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder("from ");
        sql.append(DBITEM_INVENTORY_JOB_CHAINS);
        sql.append(" where lower(name) = :name");
        sql.append(" and instanceId = :instanceId");
        Query<DBItemInventoryJobChain> query = getSession().createQuery(sql.toString());
        query.setParameter("name", name.toLowerCase());
        query.setParameter("instanceId", instanceId);
        List<DBItemInventoryJobChain> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    public DBItemInventoryOrder getInventoryOrder(Long instanceId, String name) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder("from ");
        sql.append(DBITEM_INVENTORY_ORDERS);
        sql.append(" where name = :name");
        sql.append(" and instanceId = :instanceId");
        Query<DBItemInventoryOrder> query = getSession().createQuery(sql.toString());
        query.setParameter("name", name);
        query.setParameter("instanceId", instanceId);
        List<DBItemInventoryOrder> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    public DBItemInventoryOrder getInventoryOrderCaseInsensitive(Long instanceId, String name) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder("from ");
        sql.append(DBITEM_INVENTORY_ORDERS);
        sql.append(" where lower(name) = :name");
        sql.append(" and instanceId = :instanceId");
        Query<DBItemInventoryOrder> query = getSession().createQuery(sql.toString());
        query.setParameter("name", name.toLowerCase());
        query.setParameter("instanceId", instanceId);
        List<DBItemInventoryOrder> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    public DBItemInventoryProcessClass getInventoryProcessClass(Long instanceId, String name) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder("from ");
        sql.append(DBITEM_INVENTORY_PROCESS_CLASSES);
        sql.append(" where name = :name");
        sql.append(" and instanceId = :instanceId");
        Query<DBItemInventoryProcessClass> query = getSession().createQuery(sql.toString());
        query.setParameter("name", name);
        query.setParameter("instanceId", instanceId);
        List<DBItemInventoryProcessClass> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    public DBItemInventoryProcessClass getInventoryProcessClassCaseInsensitive(Long instanceId, String name) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder("from ");
        sql.append(DBITEM_INVENTORY_PROCESS_CLASSES);
        sql.append(" where lower(name) = :name");
        sql.append(" and instanceId = :instanceId");
        Query<DBItemInventoryProcessClass> query = getSession().createQuery(sql.toString());
        query.setParameter("name", name.toLowerCase());
        query.setParameter("instanceId", instanceId);
        List<DBItemInventoryProcessClass> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    public DBItemInventorySchedule getInventorySchedule(Long instanceId, String name) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder("from ");
        sql.append(DBITEM_INVENTORY_SCHEDULES);
        sql.append(" where name = :name");
        sql.append(" and instanceId = :instanceId");
        Query<DBItemInventorySchedule> query = getSession().createQuery(sql.toString());
        query.setParameter("name", name);
        query.setParameter("instanceId", instanceId);
        List<DBItemInventorySchedule> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    public DBItemInventorySchedule getInventoryScheduleCaseInsensitive(Long instanceId, String name) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder("from ");
        sql.append(DBITEM_INVENTORY_SCHEDULES);
        sql.append(" where lower(name) = :name");
        sql.append(" and instanceId = :instanceId");
        Query<DBItemInventorySchedule> query = getSession().createQuery(sql.toString());
        query.setParameter("name", name.toLowerCase());
        query.setParameter("instanceId", instanceId);
        List<DBItemInventorySchedule> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    public DBItemInventoryLock getInventoryLock(Long instanceId, String name) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder("from ");
        sql.append(DBITEM_INVENTORY_LOCKS);
        sql.append(" where name = :name");
        sql.append(" and instanceId = :instanceId");
        Query<DBItemInventoryLock> query = getSession().createQuery(sql.toString());
        query.setParameter("name", name);
        query.setParameter("instanceId", instanceId);
        List<DBItemInventoryLock> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }
    
    public DBItemInventoryLock getInventoryLockCaseInsensitive(Long instanceId, String name) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder("from ");
        sql.append(DBITEM_INVENTORY_LOCKS);
        sql.append(" where lower(name) = :name");
        sql.append(" and instanceId = :instanceId");
        Query<DBItemInventoryLock> query = getSession().createQuery(sql.toString());
        query.setParameter("name", name.toLowerCase());
        query.setParameter("instanceId", instanceId);
        List<DBItemInventoryLock> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }
    
    public DBItemInventoryProcessClass getProcessClassIfExists(Long instanceId, String processClass) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_PROCESS_CLASSES);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and name = :name");
        Query<DBItemInventoryProcessClass> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        query.setParameter("name", processClass);
        List<DBItemInventoryProcessClass> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    public DBItemInventorySchedule getScheduleIfExists(Long instanceId, String scheduleName) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_SCHEDULES);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and name = :name");
        Query<DBItemInventorySchedule> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        query.setParameter("name", scheduleName);
        List<DBItemInventorySchedule> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }
    
    public DBItemInventoryFile getInventoryFile(Long instanceId, String fileName) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_FILES);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and fileName = :fileName");
        Query<DBItemInventoryFile> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        query.setParameter("fileName", fileName);
        List<DBItemInventoryFile> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    public DBItemInventorySchedule getSubstituteIfExists(String substitute, Long instanceId) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_SCHEDULES);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and name = :name");
        Query<DBItemInventorySchedule> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        query.setParameter("name", substitute);
        List<DBItemInventorySchedule> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }
    
    public Long saveOrUpdateSchedule(DBItemInventorySchedule newSchedule) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_SCHEDULES);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and fileId = :fileId");
        sql.append(" and name = :name");
        Query<DBItemInventorySchedule> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", newSchedule.getInstanceId());
        query.setParameter("fileId", newSchedule.getFileId());
        query.setParameter("name", newSchedule.getName());
        DBItemInventorySchedule result = getSession().getSingleResult(query);
        if (result != null) {
            DBItemInventorySchedule classFromDb = result;
            classFromDb.setBasename(newSchedule.getBasename());
            classFromDb.setTitle(newSchedule.getTitle());
            classFromDb.setSubstitute(newSchedule.getSubstitute());
            classFromDb.setSubstituteId(newSchedule.getSubstituteId());
            classFromDb.setSubstituteName(newSchedule.getSubstituteName());
            classFromDb.setSubstituteValidFrom(newSchedule.getSubstituteValidFrom());
            classFromDb.setSubstituteValidTo(newSchedule.getSubstituteValidTo());
            classFromDb.setModified(ReportUtil.getCurrentDateTime());
            getSession().update(classFromDb);
            return classFromDb.getId();
        } else {
            newSchedule.setCreated(ReportUtil.getCurrentDateTime());
            newSchedule.setModified(ReportUtil.getCurrentDateTime());
            getSession().save(newSchedule);
            return newSchedule.getId();
        }
    }
    
    public Long getJobChainId(Long instanceId, String name) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("select id from ");
        sql.append(DBITEM_INVENTORY_JOB_CHAINS);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and name = :name");
        Query<Long> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        query.setParameter("name", name);
        return getSession().getSingleResult(query);
    }
    
    public DBItemInventoryJob getJobIfExists(Long instanceId, String jobName) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_JOBS);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and name = :name");
        Query<DBItemInventoryJob> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        query.setParameter("name", jobName);
        List<DBItemInventoryJob> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

//    public String getJobChainName(Long instanceId, String basename) throws SOSHibernateException {
//        StringBuilder sql = new StringBuilder();
//        sql.append("from ");
//        sql.append(DBITEM_INVENTORY_JOB_CHAINS);
//        sql.append(" where instanceId = :instanceId");
//        sql.append(" and baseName = :baseName");
//        Query<DBItemInventoryJobChain> query = getSession().createQuery(sql.toString());
//        query.setParameter("instanceId", instanceId);
//        query.setParameter("baseName", basename);
//        List<DBItemInventoryJobChain> result = getSession().getResultList(query);
//        if(result != null && !result.isEmpty()){
//            return result.get(0).getName();
//        }
//        return "";
//    }

    public DBItemInventoryJobChain getJobChain(Long instanceId, String name) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_JOB_CHAINS);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and name = :name");
        Query<DBItemInventoryJobChain> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        query.setParameter("name", name);
        List<DBItemInventoryJobChain> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    public DBItemInventoryJobChain getJobChainIfExists(Long instanceId, String jobChainName) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_JOB_CHAINS);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and name = :name");
        Query<DBItemInventoryJobChain> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        query.setParameter("name", jobChainName);
        List<DBItemInventoryJobChain> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    public DBItemInventoryJobChainNode getJobChainNodeIfExists(Long instanceId, Long jobChainId, String state)
            throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_JOB_CHAIN_NODES);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and jobChainId = :jobChainId");
        sql.append(" and state = :state");
        Query<DBItemInventoryJobChainNode> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        query.setParameter("jobChainId", jobChainId);
        query.setParameter("state", state);
        List<DBItemInventoryJobChainNode> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    public DBItemInventoryJobChainNode getJobChainNodeIfExists(Long instanceId, Long jobChainId, Integer nodeType, String state,
            String directory, String regex) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_JOB_CHAIN_NODES);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and jobChainId = :jobChainId");
        if (nodeType == 3) {
            sql.append(" and directory = :directory");
            if (regex != null) {
                sql.append(" and regex = :regex");
            }
        } else {
            sql.append(" and state = :state");
        }
        Query<DBItemInventoryJobChainNode> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        query.setParameter("jobChainId", jobChainId);
        if (nodeType == 3) {
            query.setParameter("directory", directory);
            if (regex != null) {
               query.setParameter("regex", regex); 
            }
        } else {
            query.setParameter("state", state);
        }
        List<DBItemInventoryJobChainNode> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    public List<DBItemInventoryJobChainNode> getJobChainNodes(Long instanceId, Long jobChainId) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_JOB_CHAIN_NODES);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and jobChainId = :jobChainId");
        Query<DBItemInventoryJobChainNode> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        query.setParameter("jobChainId", jobChainId);
        return getSession().getResultList(query);
    }

    public List<DBItemInventoryJob> getAllJobsForInstance(Long instanceId) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_JOBS);
        sql.append(" where instanceId = :instanceId");
        Query<DBItemInventoryJob> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        return getSession().getResultList(query);
    }
    
    public List<DBItemInventoryJobChain> getAllJobChainsForInstance(Long instanceId) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_JOB_CHAINS);
        sql.append(" where instanceId = :instanceId");
        Query<DBItemInventoryJobChain> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        return getSession().getResultList(query);
    }
    
    public List<DBItemInventoryJobChainNode> getAllJobChainNodesForInstance(Long instanceId) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_JOB_CHAIN_NODES);
        sql.append(" where instanceId = :instanceId");
        Query<DBItemInventoryJobChainNode> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        return getSession().getResultList(query);
    }
    
    public List<DBItemInventoryOrder> getAllOrdersForInstance(Long instanceId) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_ORDERS);
        sql.append(" where instanceId = :instanceId");
        Query<DBItemInventoryOrder> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        return getSession().getResultList(query);
    }
    
    public List<DBItemInventoryProcessClass> getAllProcessClassesForInstance(Long instanceId) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_PROCESS_CLASSES);
        sql.append(" where instanceId = :instanceId");
        Query<DBItemInventoryProcessClass> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        return getSession().getResultList(query);
    }
    
    public List<DBItemInventorySchedule> getAllSchedulesForInstance(Long instanceId) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_SCHEDULES);
        sql.append(" where instanceId = :instanceId");
        Query<DBItemInventorySchedule> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        return getSession().getResultList(query);
    }
    
    public List<DBItemInventoryAppliedLock> getAllAppliedLocks() throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_APPLIED_LOCKS);
        Query<DBItemInventoryAppliedLock> query = getSession().createQuery(sql.toString());
        return getSession().getResultList(query);
    }
    
    public List<DBItemInventoryLock> getAllLocksForInstance(Long instanceId) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_LOCKS);
        sql.append(" where instanceId = :instanceId");
        Query<DBItemInventoryLock> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        return getSession().getResultList(query);
    }
    
    public List<DBItemInventoryAgentCluster> getAllAgentClustersForInstance(Long instanceId) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_AGENT_CLUSTER);
        sql.append(" where instanceId = :instanceId");
        Query<DBItemInventoryAgentCluster> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        return getSession().getResultList(query);
    }
    
    public List<DBItemInventoryAgentClusterMember> getAllAgentClusterMembersForInstance(Long instanceId)
            throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_AGENT_CLUSTERMEMBERS);
        sql.append(" where instanceId = :instanceId");
        Query<DBItemInventoryAgentClusterMember> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        return getSession().getResultList(query);
    }
    
    public List<DBItemInventoryAgentClusterMember> getAllAgentClusterMembersForInstanceAndCluster(Long instanceId, Long clusterId)
            throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_AGENT_CLUSTERMEMBERS);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and agentClusterId = :agentClusterId");
        Query<DBItemInventoryAgentClusterMember> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        query.setParameter("agentClusterId", clusterId);
        return getSession().getResultList(query);
    }
    
    public List<DBItemInventoryAgentInstance> getAllAgentInstancesForInstance(Long instanceId) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_AGENT_INSTANCES);
        sql.append(" where instanceId = :instanceId");
        Query<DBItemInventoryAgentInstance> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        return getSession().getResultList(query);
    }
    
    public List<DBItemInventoryFile> getAllFilesForInstance(Long instanceId) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_FILES);
        sql.append(" where instanceId = :instanceId");
        Query<DBItemInventoryFile> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        return getSession().getResultList(query);
    }
    
    public List<DBItemInventoryCalendarUsage> getAllCalendarUsagesForInstance(Long instanceId) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_CALENDAR_USAGE);
        sql.append(" where instanceId = :instanceId");
        Query<DBItemInventoryCalendarUsage> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        return getSession().getResultList(query);
    }
    
    public List<DBItemInventoryJob> getAllJobsFromJobChain(Long instanceId, Long jobChainId) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_JOBS);
        sql.append(" where name in");
        sql.append(" (select jobName from ").append(DBITEM_INVENTORY_JOB_CHAIN_NODES);
        sql.append(" where instanceId = :instanceId and jobChainId = :jobChainId");
        sql.append(" group by jobName)");
        Query<DBItemInventoryJob> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        query.setParameter("jobChainId", jobChainId);
        return getSession().getResultList(query);
    }
    
    public void refreshUsedInJobChains(Long instanceId, List<DBItemInventoryJob> jobs) throws SOSHibernateException {
        for (DBItemInventoryJob job : jobs) {
            refreshUsedInJobChains(instanceId, job);
        }
    }
    
    public void refreshUsedInJobChains(Long instanceId, DBItemInventoryJob job) throws SOSHibernateException {
        LOGGER.debug(String.format("refreshUsedInJobChains: job   id=%1$s    basename=%2$s ", job.getId(), job.getBaseName()));
        StringBuilder sql = new StringBuilder();
        sql.append("update ").append(DBITEM_INVENTORY_JOBS);
        sql.append(" set usedInJobChains = :usedCount where id = :id");
        job.setUsedInJobChains(getUsedInJobChains(job.getName(), job.getInstanceId()));
        Query<DBItemInventoryJob> query = getSession().createQuery(sql.toString());
        query.setParameter("usedCount", getUsedInJobChains(job.getName(), job.getInstanceId()));
        query.setParameter("id", job.getId());
        getSession().executeUpdate(query);
    }
    
    private Integer getUsedInJobChains(String jobName, Long instanceId) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("select jobChainId from ");
        sql.append(DBLayer.DBITEM_INVENTORY_JOB_CHAIN_NODES);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and jobName = :jobName");
        sql.append(" group by jobChainId");
        Query<Long> query = getSession().createQuery(sql.toString());
        query.setParameter("jobName", jobName);
        query.setParameter("instanceId", instanceId);
        List<Long> jobChainIds = getSession().getResultList(query);
        if(jobChainIds != null) {
            return jobChainIds.size();
        }
        return null;
    }
    
    public DBItemInventoryAgentInstance getInventoryAgentInstanceFromDb (String url, Long instanceId) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBLayer.DBITEM_INVENTORY_AGENT_INSTANCES);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and url = :url");
        Query<DBItemInventoryAgentInstance> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        query.setParameter("url", url);
        List<DBItemInventoryAgentInstance> result = getSession().getResultList(query);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    public int deleteItemsFromDb(Date started, String tableName, Long instanceId) throws SOSHibernateException {
        LOGGER.debug(String.format("delete: items from %2$s before = %1$s and instanceId = %3$d with query.executeUpdate()",
                started.toString(), tableName, instanceId));
        StringBuilder sql = new StringBuilder();
        sql.append("delete from ");
        sql.append(tableName);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and modified < :modifiedDate");
        Query<Integer> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        query.setParameter("modifiedDate", started, TemporalType.TIMESTAMP);
        return getSession().executeUpdate(query);
    }
    
    public int deleteCalendarUsagesFromDb(Date started, Long instanceId) throws SOSHibernateException {
        LOGGER.debug(String.format("delete: items from %2$s before = %1$s and instanceId = %3$d with query.executeUpdate()",
                started.toString(), DBITEM_INVENTORY_CALENDAR_USAGE, instanceId));
        StringBuilder sql = new StringBuilder();
        sql.append("delete from ");
        sql.append(DBITEM_INVENTORY_CALENDAR_USAGE);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and created < :created");
        Query<Integer> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        query.setParameter("created", started, TemporalType.TIMESTAMP);
        return getSession().executeUpdate(query);
    }
    
    public int deleteAppliedLocksFromDb(Date started, Long instanceId) throws SOSHibernateException {
        LOGGER.debug(String.format("delete: appliedLocks before = %1$s  and instanceId = %2$d with query.executeUpdate()",
                started.toString(), instanceId));
        StringBuilder sql = new StringBuilder();
        sql.append("delete from ");
        sql.append(DBLayer.DBITEM_INVENTORY_APPLIED_LOCKS).append(" appliedLocks ");
        sql.append("where appliedLocks.id in (select locks.id from ");
        sql.append(DBLayer.DBITEM_INVENTORY_LOCKS).append(" locks");
        sql.append(" where locks.instanceId = :instanceId");
        sql.append(" and locks.modified < :modified )");
        Query<Integer> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        query.setParameter("modified", started, TemporalType.TIMESTAMP);
        return getSession().executeUpdate(query);
    }
    
    public int deleteOldNodes(DBItemInventoryJobChain jobChain) throws SOSHibernateException {
        LOGGER.debug(String.format("delete old JobChainNodes for JobChain = %1$s and instanceId = %2$d with query.executeUpdate()",
                jobChain.getName(), jobChain.getInstanceId()));
        StringBuilder sql = new StringBuilder();
        sql.append("delete from ");
        sql.append(DBLayer.DBITEM_INVENTORY_JOB_CHAIN_NODES);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and jobChainId = :jobChainId)");
        Query<Integer> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", jobChain.getInstanceId());
        query.setParameter("jobChainId", jobChain.getId());
        int i = getSession().executeUpdate(query);
        return i;
    }
    
    public DBItemInventoryLock getLockByName(String name) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBLayer.DBITEM_INVENTORY_LOCKS);
        sql.append(" where basename = :basename");
        Query<DBItemInventoryLock> query = getSession().createQuery(sql.toString());
        query.setParameter("basename", name);
        List<DBItemInventoryLock> result = getSession().getResultList(query);
        if(result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    public int updateInventoryLiveDirectory(Long instanceId, String liveDirectory) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("update ");
        sql.append(DBITEM_INVENTORY_INSTANCES);
        sql.append(" set liveDirectory = :liveDirectory");
        sql.append(" where id = :instanceId");
        Query<Integer> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        query.setParameter("liveDirectory", liveDirectory);
        return getSession().executeUpdate(query);
    }
    
    public List<DBItemInventoryOrder> getOrdersReferencingSchedule(Long instanceId, String scheduleName) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_ORDERS);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and scheduleName = :scheduleName");
        Query<DBItemInventoryOrder> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        query.setParameter("scheduleName", scheduleName);
        return query.getResultList();
    }
    
    public List<DBItemInventoryJob> getJobsReferencingSchedule(Long instanceId, String scheduleName) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_JOBS);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and scheduleName = :scheduleName");
        Query<DBItemInventoryJob> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        query.setParameter("scheduleName", scheduleName);
        return query.getResultList();
    }
    
    public DBItemInventoryOperatingSystem getInventoryOpSysById(Long id) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder("from ");
        sql.append(DBITEM_INVENTORY_OPERATING_SYSTEMS);
        sql.append(" where id = :id");
        Query<DBItemInventoryOperatingSystem> query = getSession().createQuery(sql.toString());
        query.setParameter("id", id);
        return getSession().getSingleResult(query);
    }

    public DBItemInventoryCalendarUsage getCalendarUsageFor(DbItem dbItem, Long calendarId) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_CALENDAR_USAGE);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and calendarId = :calendarId");
        sql.append(" and objectType = :objectType");
        sql.append(" and path = :path");
        Query<DBItemInventoryCalendarUsage> query = getSession().createQuery(sql.toString());
        if (dbItem instanceof DBItemInventoryJob) {
            query.setParameter("instanceId", ((DBItemInventoryJob) dbItem).getInstanceId());
            query.setParameter("calendarId", calendarId);
            query.setParameter("objectType", ObjectType.JOB.name());
            query.setParameter("path", ((DBItemInventoryJob) dbItem).getName());
        } else if (dbItem instanceof DBItemInventoryOrder) {
            query.setParameter("instanceId", ((DBItemInventoryOrder) dbItem).getInstanceId());
            query.setParameter("calendarId", calendarId);
            query.setParameter("objectType", ObjectType.ORDER.name());
            query.setParameter("path", ((DBItemInventoryOrder) dbItem).getName());
        } else if (dbItem instanceof DBItemInventorySchedule) {
            query.setParameter("instanceId", ((DBItemInventorySchedule) dbItem).getInstanceId());
            query.setParameter("calendarId", calendarId);
            query.setParameter("objectType", ObjectType.SCHEDULE.name());
            query.setParameter("path", ((DBItemInventorySchedule) dbItem).getName());
        }
        return getSession().getSingleResult(query);
    }
    
    public List<DBItemInventoryCalendarUsage> getCalendarUsages(Long instanceId, Long calendarId, String path) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_CALENDAR_USAGE);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and calendarId = :calendarId");
        sql.append(" and path = :path");
        Query<DBItemInventoryCalendarUsage> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        query.setParameter("calendarId", calendarId);
        query.setParameter("path", path);
        return getSession().getResultList(query);
    }
    
    public List<DBItemInventoryCalendarUsage> getCalendarUsagesToDelete(DbItem dbItem) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_CALENDAR_USAGE);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and objectType = :objectType");
        sql.append(" and path = :path");
        Query<DBItemInventoryCalendarUsage> query = getSession().createQuery(sql.toString());
        if (dbItem instanceof DBItemInventoryJob) {
            query.setParameter("instanceId", ((DBItemInventoryJob) dbItem).getInstanceId());
            query.setParameter("objectType", ObjectType.JOB.name());
            query.setParameter("path", ((DBItemInventoryJob) dbItem).getName());
        } else if (dbItem instanceof DBItemInventoryOrder) {
            query.setParameter("instanceId", ((DBItemInventoryOrder) dbItem).getInstanceId());
            query.setParameter("objectType", ObjectType.ORDER.name());
            query.setParameter("path", ((DBItemInventoryOrder) dbItem).getName());
        } else if (dbItem instanceof DBItemInventorySchedule) {
            query.setParameter("instanceId", ((DBItemInventorySchedule) dbItem).getInstanceId());
            query.setParameter("objectType", ObjectType.SCHEDULE.name());
            query.setParameter("path", ((DBItemInventorySchedule) dbItem).getName());
        }
        return getSession().getResultList(query);
    }
    
    public List<Long> getAllCalendarIds() throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("select id from ").append(DBITEM_CALENDARS);
        Query<Long> query = getSession().createQuery(sql.toString());
        return getSession().getResultList(query);
    }
    
    public DBItemCalendar getCalendar(Long id) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ").append(DBITEM_CALENDARS);
        sql.append(" where id = :id");
        Query<DBItemCalendar> query = getSession().createQuery(sql.toString());
        query.setParameter("id", id);
        return getSession().getSingleResult(query);
    }

    public DBItemCalendar getCalendar(Long instanceId, String path) throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ").append(DBITEM_CALENDARS);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and name = :path");
        Query<DBItemCalendar> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        query.setParameter("path", path);
        return getSession().getSingleResult(query);
    }

    public List<DBItemInventoryCalendarUsage> getCalendarUsages(Long instanceId, String path, String objectType)
            throws SOSHibernateException {
        StringBuilder sql = new StringBuilder();
        sql.append("from ");
        sql.append(DBITEM_INVENTORY_CALENDAR_USAGE);
        sql.append(" where instanceId = :instanceId");
        sql.append(" and path = :path");
        sql.append(" and objectType = :objectType");
        Query<DBItemInventoryCalendarUsage> query = getSession().createQuery(sql.toString());
        query.setParameter("instanceId", instanceId);
        query.setParameter("path", path);
        query.setParameter("objectType", objectType);
        return getSession().getResultList(query);
    }
    
}