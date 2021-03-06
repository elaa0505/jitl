package sos.scheduler.misc;

import com.sos.JSHelper.Basics.JSToolBox;
import com.sos.JSHelper.Listener.JSListenerClass;

import org.apache.log4j.Logger;
import org.junit.*;

public class CopyJob2OrderParameterJUnitTest extends JSToolBox {

    @SuppressWarnings("unused")//$NON-NLS-1$
    private final static String conClassName = "CopyJob2OrderParameterJUnitTest"; //$NON-NLS-1$
    @SuppressWarnings("unused")//$NON-NLS-1$
    private static Logger logger = Logger.getLogger(CopyJob2OrderParameterJUnitTest.class);

    protected CopyJob2OrderParameterOptions objOptions = null;
    private CopyJob2OrderParameter objE = null;

    public CopyJob2OrderParameterJUnitTest() {
        //
    }

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
        objE = new CopyJob2OrderParameter();
        objE.registerMessageListener(this);
        objOptions = objE.Options();
        objOptions.registerMessageListener(this);

        JSListenerClass.bolLogDebugInformation = true;
        JSListenerClass.intMaxDebugLevel = 9;

    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    @Ignore("Test set to Ignore for later examination")
    public void testExecute() throws Exception {

        objE.Execute();

        //		assertEquals ("auth_file", objO.auth_file.Value(),"test"); //$NON-NLS-1$
        //		assertEquals ("user", objO.user.Value(),"test"); //$NON-NLS-1$

    }
}  // class CopyJob2OrderParameterJUnitTest