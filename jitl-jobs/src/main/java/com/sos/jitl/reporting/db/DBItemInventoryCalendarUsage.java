package com.sos.jitl.reporting.db;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.sos.hibernate.classes.DbItem;

@Entity
@Table(name = DBLayer.TABLE_INVENTORY_CALENDAR_USAGE)
@SequenceGenerator(name = DBLayer.TABLE_INVENTORY_CALENDAR_USAGE_SEQUENCE, sequenceName = DBLayer.TABLE_INVENTORY_CALENDAR_USAGE_SEQUENCE,
    allocationSize = 1)
public class DBItemInventoryCalendarUsage extends DbItem implements Serializable {

    private static final long serialVersionUID = 1L;

     /** Primary key */
    private Long id;

    /** Others */
    private Long instanceId;
    private Long calendarId;
    private String objectType;
    private String path;
    private Date created;
    
    public DBItemInventoryCalendarUsage() {
    }

    /** Primary key */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = DBLayer.TABLE_INVENTORY_CALENDAR_USAGE_SEQUENCE)
    @Column(name = "`ID`", nullable = false)
    public Long getId() {
        return this.id;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = DBLayer.TABLE_INVENTORY_CALENDAR_USAGE_SEQUENCE)
    @Column(name = "`ID`", nullable = false)
    public void setId(Long val) {
        this.id = val;
    }

    /** Others */
    @Column(name = "`INSTANCE_ID`", nullable = false)
    public void setInstanceId(Long val) {
        this.instanceId = val;
    }

    @Column(name = "`INSTANCE_ID`", nullable = false)
    public Long getInstanceId() {
        return this.instanceId;
    }

    @Column(name = "`CALENDAR_ID`", nullable = false)
    public void setCalendarId(Long val) {
        this.calendarId = val;
    }

    @Column(name = "`CALENDAR_ID`", nullable = false)
    public Long getCalendarId() {
        return this.calendarId;
    }
    
    @Column(name = "`OBJECT_TYPE`", nullable = false)
    public void setObjectType(String val) {
        this.objectType = val;
    }

    @Column(name = "`OBJECT_TYPE`", nullable = false)
    public String getObjectType() {
        return this.objectType;
    }
    
    @Column(name = "`PATH`", nullable = false)
    public void setPath(String val) {
        this.path = val;
    }

    @Column(name = "`PATH`", nullable = false)
    public String getPath() {
        return this.path;
    }
    
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "`CREATED`", nullable = false)
    public void setCreated(Date val) {
        this.created = val;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "`CREATED`", nullable = false)
    public Date getCreated() {
        return this.created;
    }


    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(instanceId).append(calendarId).append(objectType).append(path).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        // always compare on unique constraint
        if (other == this) {
            return true;
        }
        if (!(other instanceof DBItemInventoryCalendarUsage)) {
            return false;
        }
        DBItemInventoryCalendarUsage rhs = ((DBItemInventoryCalendarUsage) other);
        return new EqualsBuilder().append(instanceId,rhs.instanceId).append(calendarId, rhs.calendarId)
                .append(objectType, rhs.objectType).append(path, rhs.path).isEquals();
    }

}