package jumble.util;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Runs all jumble tests.
 *
 * @author <a href="mailto:len@reeltwo.com">Len Trigg</a>
 * @version $Revision$
 */
public class AllTests extends TestSuite {

  public static Test suite() {
    TestSuite suite = new TestSuite();
    suite.addTest(BCELRTSITest.suite());
    suite.addTest(ClassLoaderChangeableTestSuiteTest.suite());
    suite.addTest(IOThreadTest.suite());
    suite.addTest(JavaRunnerTest.suite());
    suite.addTest(RTSITest.suite());
    return suite;
  }

  public static void main(String[] args) {
    junit.textui.TestRunner.run(suite());
  }
}
