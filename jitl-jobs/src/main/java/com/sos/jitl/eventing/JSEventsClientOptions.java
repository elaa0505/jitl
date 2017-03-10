package com.sos.jitl.eventing;

import java.util.HashMap;

import com.sos.JSHelper.Annotations.JSOptionClass;
import com.sos.JSHelper.Exceptions.JSExceptionMandatoryOptionMissing;
import com.sos.JSHelper.Listener.JSListener;

@JSOptionClass(name = "JSEventsClientOptions", description = "Submit and Delete Events")
public class JSEventsClientOptions extends JSEventsClientOptionsSuperClass {

    private static final long serialVersionUID = -78464895002760751L;
    @SuppressWarnings("unused")
    private final String conClassName = this.getClass().getSimpleName();

    @SuppressWarnings("unused")
    private static final String conSVNVersion = "$Id$";
  
    public JSEventsClientOptions() {
    } // public JSEventsClientOptions

    public JSEventsClientOptions(final JSListener pobjListener) {
        this();
        this.registerMessageListener(pobjListener);
    } // public JSEventsClientOptions
   

    public JSEventsClientOptions(final HashMap<String, String> JSSettings) throws Exception {
        super(JSSettings);
    } // public JSEventsClientOptions (HashMap JSSettings)
    
    @Override
    // JSEventsClientOptionsSuperClass
    public void CheckMandatory() {
        try {
            super.CheckMandatory();
        } catch (Exception e) {
            throw new JSExceptionMandatoryOptionMissing(e.toString());
        }
    } // public void CheckMandatory ()
}
