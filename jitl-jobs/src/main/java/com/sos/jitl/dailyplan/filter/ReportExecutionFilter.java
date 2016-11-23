package com.sos.jitl.dailyplan.filter;

import java.util.ArrayList;

import com.sos.scheduler.history.classes.HistorySeverity;

public class ReportExecutionFilter extends ReportHistoryFilter {

    protected String jobname = null;
    private String status = "";
    private ArrayList<String> listOfJobs;

    public ArrayList<String> getListOfJobs() {
        return listOfJobs;
    }

    public String getJobname() {
        if (jobname == null) {
            return jobname;
        }
        if (jobname.startsWith("/")) {
            return jobname.substring(1);
        } else {
            return jobname;
        }
    }

    public ReportExecutionFilter() {
        super();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setJobname(String jobname) {
        this.jobname = jobname;
    }

    public void addJobPath(String job) {
        if (listOfJobs == null) {
            listOfJobs = new ArrayList<String>();
        }
        listOfJobs.add(job);

    }

}