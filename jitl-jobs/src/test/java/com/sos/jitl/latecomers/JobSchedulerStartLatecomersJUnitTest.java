

package com.sos.jitl.latecomers;

import static org.junit.Assert.assertEquals;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import com.sos.JSHelper.Basics.JSToolBox;
import com.sos.JSHelper.Listener.JSListenerClass;
import com.sos.jitl.latecomers.classes.DailyPlanExecuter;
import com.sos.jitl.restclient.WebserviceCredentials;
import com.sos.jitl.restclient.WebserviceExecuter;


public class JobSchedulerStartLatecomersJUnitTest extends JSToolBox {

	protected JobSchedulerStartLatecomersOptions jobSchedulerStartLatecomersOptions = null;
	private static final Logger	LOGGER = Logger.getLogger(JobSchedulerStartLatecomersJUnitTest.class);
	private JobSchedulerStartLatecomers jobSchedulerStartLatecomers = null;
	
	public JobSchedulerStartLatecomersJUnitTest() {
		//
	}

 	@Before
	public void setUp() throws Exception {
		jobSchedulerStartLatecomers = new JobSchedulerStartLatecomers();
		jobSchedulerStartLatecomers.registerMessageListener(this);
		jobSchedulerStartLatecomersOptions = jobSchedulerStartLatecomers.getOptions();
		jobSchedulerStartLatecomersOptions.registerMessageListener(this);
		JSListenerClass.bolLogDebugInformation = true;
		JSListenerClass.intMaxDebugLevel = 9;
	}

 

	@Test
	public void testExecute() throws Exception {
	
		String jocUrl = "http://localhost:4446/joc/api";
		WebserviceCredentials webserviceCredentials = new WebserviceCredentials();
		webserviceCredentials.setSchedulerId("scheduler_joc_cockpit");
		webserviceCredentials.setUser("api_user");
		webserviceCredentials.setPassword("api");

		jobSchedulerStartLatecomers.setSchedulerId(webserviceCredentials.getSchedulerId());
		jobSchedulerStartLatecomers.setJocUrl(jocUrl);

		WebserviceExecuter webServiceExecuter = null;
		webServiceExecuter = new DailyPlanExecuter(jocUrl, webserviceCredentials.account()); 
		webServiceExecuter.setSchedulerId(webserviceCredentials.getSchedulerId());
		webServiceExecuter.login();
		jobSchedulerStartLatecomers.setxAccessToken(webServiceExecuter.getAccessToken());
		jobSchedulerStartLatecomers.getOptions().onlyReport.setTrue();
		jobSchedulerStartLatecomers.getOptions().ignoreFolderList.setValue("/x;/s*");
		
		jobSchedulerStartLatecomers.execute();
	}

}   
