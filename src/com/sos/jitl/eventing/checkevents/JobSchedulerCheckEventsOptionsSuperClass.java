

package com.sos.jitl.eventing.checkevents;

import java.util.HashMap;

import com.sos.JSHelper.Options.*;
import com.sos.JSHelper.Annotations.JSOptionClass;
import com.sos.JSHelper.Annotations.JSOptionDefinition;
import com.sos.JSHelper.Exceptions.JSExceptionMandatoryOptionMissing;
import com.sos.JSHelper.Listener.JSListener; 
import org.apache.log4j.Logger;

import com.sos.JSHelper.Options.*;

/**
 * \class 		JobSchedulerCheckEventsOptionsSuperClass - Check if events exist
 *
 * \brief 
 * An Options-Super-Class with all Options. This Class will be extended by the "real" Options-class (\see JobSchedulerCheckEventsOptions.
 * The "real" Option class will hold all the things, which are normaly overwritten at a new generation
 * of the super-class.
 *
 *

 *
 * 
 * \verbatim ;
 * \endverbatim
 * \section OptionsTable Tabelle der vorhandenen Optionen
 * 
 * Tabelle mit allen Optionen
 * 
 * MethodName
 * Title
 * Setting
 * Description
 * IsMandatory
 * DataType
 * InitialValue
 * TestValue
 * 
 * 
 *
 * \section TestData Eine Hilfe zum Erzeugen einer HashMap mit Testdaten
 *
 * Die folgenden Methode kann verwendet werden, um f�r einen Test eine HashMap
 * mit sinnvollen Werten f�r die einzelnen Optionen zu erzeugen.
 *
 * \verbatim
 private HashMap <String, String> SetJobSchedulerSSHJobOptions (HashMap <String, String> pobjHM) {
	pobjHM.put ("		JobSchedulerCheckEventsOptionsSuperClass.auth_file", "test");  // This parameter specifies the path and name of a user's pr
		return pobjHM;
  }  //  private void SetJobSchedulerSSHJobOptions (HashMap <String, String> pobjHM)
 * \endverbatim
 */
@JSOptionClass(name = "JobSchedulerCheckEventsOptionsSuperClass", description = "JobSchedulerCheckEventsOptionsSuperClass")
public class JobSchedulerCheckEventsOptionsSuperClass extends JSOptionsClass {
	private final String					conClassName						= "JobSchedulerCheckEventsOptionsSuperClass";
		@SuppressWarnings("unused")
	private static Logger		logger			= Logger.getLogger(JobSchedulerCheckEventsOptionsSuperClass.class);

		

/**
 * \var configuration_file : 
 * 
 *
 */
    @JSOptionDefinition(name = "configuration_file", 
    description = "", 
    key = "configuration_file", 
    type = "SOSOptionString", 
    mandatory = false)
    
    public SOSOptionString configuration_file = new SOSOptionString(this, conClassName + ".configuration_file", // HashMap-Key
                                                                "", // Titel
                                                                " ", // InitValue
                                                                " ", // DefaultValue
                                                                false // isMandatory
                    );

/**
 * \brief getconfiguration_file : 
 * 
 * \details
 * 
 *
 * \return 
 *
 */
    public SOSOptionString  getconfiguration_file() {
        return configuration_file;
    }

/**
 * \brief setconfiguration_file : 
 * 
 * \details
 * 
 *
 * @param configuration_file : 
 */
    public void setconfiguration_file (SOSOptionString p_configuration_file) { 
        this.configuration_file = p_configuration_file;
    }

                        

/**
 * \var event_class : 
 * 
 *
 */
    @JSOptionDefinition(name = "event_class", 
    description = "", 
    key = "event_class", 
    type = "SOSOptionString", 
    mandatory = false)
    
    public SOSOptionString event_class = new SOSOptionString(this, conClassName + ".event_class", // HashMap-Key
                                                                "", // Titel
                                                                "", // InitValue
                                                                "", // DefaultValue
                                                                false // isMandatory
                    );

/**
 * \brief getevent_class : 
 * 
 * \details
 * 
 *
 * \return 
 *
 */
    public SOSOptionString  getevent_class() {
        return event_class;
    }

/**
 * \brief setevent_class : 
 * 
 * \details
 * 
 *
 * @param event_class : 
 */
    public void setevent_class (SOSOptionString p_event_class) { 
        this.event_class = p_event_class;
    }

                        
 
/**
 * \var event_condition : 
 * 
 *
 */
    @JSOptionDefinition(name = "event_condition", 
    description = "", 
    key = "event_condition", 
    type = "SOSOptionString", 
    mandatory = false)
    
    public SOSOptionString event_condition = new SOSOptionString(this, conClassName + ".event_condition", // HashMap-Key
                                                                "", // Titel
                                                                "", // InitValue
                                                                "", // DefaultValue
                                                                false // isMandatory
                    );

/**
 * \brief getevent_condition : 
 * 
 * \details
 * 
 *
 * \return 
 *
 */
    public SOSOptionString  getevent_condition() {
        return event_condition;
    }

/**
 * \brief setevent_condition : 
 * 
 * \details
 * 
 *
 * @param event_condition : 
 */
    public void setevent_condition (SOSOptionString p_event_condition) { 
        this.event_condition = p_event_condition;
    }

                        

/**
 * \var event_exit_code : 
 * 
 *
 */
    @JSOptionDefinition(name = "event_exit_code", 
    description = "", 
    key = "event_exit_code", 
    type = "SOSOptionString", 
    mandatory = false)
    
    public SOSOptionString event_exit_code = new SOSOptionString(this, conClassName + ".event_exit_code", // HashMap-Key
                                                                "", // Titel
                                                                "", // InitValue
                                                                "", // DefaultValue
                                                                false // isMandatory
                    );

/**
 * \brief getevent_exit_code : 
 * 
 * \details
 * 
 *
 * \return 
 *
 */
    public SOSOptionString  getevent_exit_code() {
        return event_exit_code;
    }

/**
 * \brief setevent_exit_code : 
 * 
 * \details
 * 
 *
 * @param event_exit_code : 
 */
    public void setevent_exit_code (SOSOptionString p_event_exit_code) { 
        this.event_exit_code = p_event_exit_code;
    }

                        

/**
 * \var event_id : 
 * 
 *
 */
    @JSOptionDefinition(name = "event_id", 
    description = "", 
    key = "event_id", 
    type = "SOSOptionString", 
    mandatory = false)
    
    public SOSOptionString event_id = new SOSOptionString(this, conClassName + ".event_id", // HashMap-Key
                                                                "", // Titel
                                                                "", // InitValue
                                                                "", // DefaultValue
                                                                false // isMandatory
                    );

/**
 * \brief getevent_id : 
 * 
 * \details
 * 
 *
 * \return 
 *
 */
    public SOSOptionString  getevent_id() {
        return event_id;
    }

/**
 * \brief setevent_id : 
 * 
 * \details
 * 
 *
 * @param event_id : 
 */
    public void setevent_id (SOSOptionString p_event_id) { 
        this.event_id = p_event_id;
    }

                        

/**
 * \var event_scheduler_id : 
 * 
 *
 */
    @JSOptionDefinition(name = "event_scheduler_id", 
    description = "", 
    key = "event_scheduler_id", 
    type = "SOSOptionString", 
    mandatory = false)
    
    public SOSOptionString event_scheduler_id = new SOSOptionString(this, conClassName + ".event_scheduler_id", // HashMap-Key
                                                                "", // Titel
                                                                "", // InitValue
                                                                "", // DefaultValue
                                                                false // isMandatory
                    );

/**
 * \brief getevent_scheduler_id : 
 * 
 * \details
 * 
 *
 * \return 
 *
 */
    public SOSOptionString  getevent_scheduler_id() {
        return event_scheduler_id;
    }

/**
 * \brief setevent_scheduler_id : 
 * 
 * \details
 * 
 *
 * @param event_scheduler_id : 
 */
    public void setevent_scheduler_id (SOSOptionString p_event_scheduler_id) { 
        this.event_scheduler_id = p_event_scheduler_id;
    }

                        

/**
 * \var handle_existing_as : 
 * 
 *
 */
    @JSOptionDefinition(name = "handle_existing_as", 
    description = "", 
    key = "handle_existing_as", 
    type = "SOSOptionString", 
    mandatory = false)
    
    public SOSOptionString handle_existing_as = new SOSOptionString(this, conClassName + ".handle_existing_as", // HashMap-Key
                                                                "", // Titel
                                                                "", // InitValue
                                                                "", // DefaultValue
                                                                false // isMandatory
                    );

/**
 * \brief gethandle_existing_as : 
 * 
 * \details
 * 
 *
 * \return 
 *
 */
    public SOSOptionString  gethandle_existing_as() {
        return handle_existing_as;
    }

/**
 * \brief sethandle_existing_as : 
 * 
 * \details
 * 
 *
 * @param handle_existing_as : 
 */
    public void sethandle_existing_as (SOSOptionString p_handle_existing_as) { 
        this.handle_existing_as = p_handle_existing_as;
    }

                        

/**
 * \var handle_not_existing_as : 
 * 
 *
 */
    @JSOptionDefinition(name = "handle_not_existing_as", 
    description = "", 
    key = "handle_not_existing_as", 
    type = "SOSOptionString", 
    mandatory = false)
    
    public SOSOptionString handle_not_existing_as = new SOSOptionString(this, conClassName + ".handle_not_existing_as", // HashMap-Key
                                                                "", // Titel
                                                                "", // InitValue
                                                                "", // DefaultValue
                                                                false // isMandatory
                    );

/**
 * \brief gethandle_not_existing_as : 
 * 
 * \details
 * 
 *
 * \return 
 *
 */
    public SOSOptionString  gethandle_not_existing_as() {
        return handle_not_existing_as;
    }

/**
 * \brief sethandle_not_existing_as : 
 * 
 * \details
 * 
 *
 * @param handle_not_existing_as : 
 */
    public void sethandle_not_existing_as (SOSOptionString p_handle_not_existing_as) { 
        this.handle_not_existing_as = p_handle_not_existing_as;
    }

                        

/**
 * \var scheduler_event_handler_host : 
 * 
 *
 */
    @JSOptionDefinition(name = "scheduler_event_handler_host", 
    description = "", 
    key = "scheduler_event_handler_host", 
    type = "SOSOptionString", 
    mandatory = false)
    
    public SOSOptionString scheduler_event_handler_host = new SOSOptionString(this, conClassName + ".scheduler_event_handler_host", // HashMap-Key
                                                                "", // Titel
                                                                "", // InitValue
                                                                "", // DefaultValue
                                                                false // isMandatory
                    );

/**
 * \brief getscheduler_event_handler_host : 
 * 
 * \details
 * 
 *
 * \return 
 *
 */
    public SOSOptionString  getscheduler_event_handler_host() {
        return scheduler_event_handler_host;
    }

/**
 * \brief setscheduler_event_handler_host : 
 * 
 * \details
 * 
 *
 * @param scheduler_event_handler_host : 
 */
    public void setscheduler_event_handler_host (SOSOptionString p_scheduler_event_handler_host) { 
        this.scheduler_event_handler_host = p_scheduler_event_handler_host;
    }

                        

/**
 * \var scheduler_event_handler_port : 
 * 
 *
 */
    @JSOptionDefinition(name = "scheduler_event_handler_port", 
    description = "", 
    key = "scheduler_event_handler_port", 
    type = "SOSOptionString", 
    mandatory = false)
    
    public SOSOptionString scheduler_event_handler_port = new SOSOptionString(this, conClassName + ".scheduler_event_handler_port", // HashMap-Key
                                                                "", // Titel
                                                                "", // InitValue
                                                                "", // DefaultValue
                                                                false // isMandatory
                    );

/**
 * \brief getscheduler_event_handler_port : 
 * 
 * \details
 * 
 *
 * \return 
 *
 */
    public SOSOptionString  getscheduler_event_handler_port() {
        return scheduler_event_handler_port;
    }

/**
 * \brief setscheduler_event_handler_port : 
 * 
 * \details
 * 
 *
 * @param scheduler_event_handler_port : 
 */
    public void setscheduler_event_handler_port (SOSOptionString p_scheduler_event_handler_port) { 
        this.scheduler_event_handler_port = p_scheduler_event_handler_port;
    }

                        
        
        
	public JobSchedulerCheckEventsOptionsSuperClass() {
		objParentClass = this.getClass();
	} // public JobSchedulerCheckEventsOptionsSuperClass

	public JobSchedulerCheckEventsOptionsSuperClass(JSListener pobjListener) {
		this();
		this.registerMessageListener(pobjListener);
	} // public JobSchedulerCheckEventsOptionsSuperClass

		//

	public JobSchedulerCheckEventsOptionsSuperClass (HashMap <String, String> JSSettings) throws Exception {
		this();
		this.setAllOptions(JSSettings);
	} // public JobSchedulerCheckEventsOptionsSuperClass (HashMap JSSettings)
/**
 * \brief getAllOptionsAsString - liefert die Werte und Beschreibung aller
 * Optionen als String
 *
 * \details
 * 
 * \see toString 
 * \see toOut
 */
	private String getAllOptionsAsString() {
		@SuppressWarnings("unused")
		final String conMethodName = conClassName + "::getAllOptionsAsString";
		String strT = conClassName + "\n";
		final StringBuffer strBuffer = new StringBuffer();
		// strT += IterateAllDataElementsByAnnotation(objParentClass, this,
		// JSOptionsClass.IterationTypes.toString, strBuffer);
		// strT += IterateAllDataElementsByAnnotation(objParentClass, this, 13,
		// strBuffer);
		strT += this.toString(); // fix
		//
		return strT;
	} // private String getAllOptionsAsString ()

/**
 * \brief setAllOptions - �bernimmt die OptionenWerte aus der HashMap
 *
 * \details In der als Parameter anzugebenden HashMap sind Schl�ssel (Name)
 * und Wert der jeweiligen Option als Paar angegeben. Ein Beispiel f�r den
 * Aufbau einer solchen HashMap findet sich in der Beschreibung dieser
 * Klasse (\ref TestData "setJobSchedulerSSHJobOptions"). In dieser Routine
 * werden die Schl�ssel analysiert und, falls gefunden, werden die
 * dazugeh�rigen Werte den Properties dieser Klasse zugewiesen.
 *
 * Nicht bekannte Schl�ssel werden ignoriert.
 *
 * \see JSOptionsClass::getItem
 *
 * @param pobjJSSettings
 * @throws Exception
 */
	public void setAllOptions(HashMap <String, String> pobjJSSettings) throws Exception {
		@SuppressWarnings("unused")
		final String conMethodName = conClassName + "::setAllOptions";
		flgSetAllOptions = true;
		objSettings = pobjJSSettings;
		super.Settings(objSettings);
		super.setAllOptions(pobjJSSettings);
		flgSetAllOptions = false;
	} // public void setAllOptions (HashMap <String, String> JSSettings)

/**
 * \brief CheckMandatory - pr�ft alle Muss-Optionen auf Werte
 *
 * \details
 * @throws Exception
 *
 * @throws Exception
 * - wird ausgel�st, wenn eine mandatory-Option keinen Wert hat
 */
		@Override
	public void CheckMandatory() throws JSExceptionMandatoryOptionMissing //
		, Exception {
		try {
			super.CheckMandatory();
		}
		catch (Exception e) {
			throw new JSExceptionMandatoryOptionMissing(e.toString());
		}
		} // public void CheckMandatory ()

/**
 *
 * \brief CommandLineArgs - �bernehmen der Options/Settings aus der
 * Kommandozeile
 *
 * \details Die in der Kommandozeile beim Starten der Applikation
 * angegebenen Parameter werden hier in die HashMap �bertragen und danach
 * den Optionen als Wert zugewiesen.
 *
 * \return void
 *
 * @param pstrArgs
 * @throws Exception
 */
	@Override
	public void CommandLineArgs(String[] pstrArgs) throws Exception {
		super.CommandLineArgs(pstrArgs);
		this.setAllOptions(super.objSettings);
	}
} // public class JobSchedulerCheckEventsOptionsSuperClass