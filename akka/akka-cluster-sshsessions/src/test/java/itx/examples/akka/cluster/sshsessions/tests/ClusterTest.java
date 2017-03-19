package itx.examples.akka.cluster.sshsessions.tests;

import com.google.common.util.concurrent.ListenableFuture;
import itx.examples.akka.cluster.sshsessions.Application;
import itx.examples.akka.cluster.sshsessions.client.SshClientService;
import itx.examples.akka.cluster.sshsessions.client.SshClientSession;
import itx.examples.akka.cluster.sshsessions.tests.utils.AkkaTestCluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by juraj on 3/18/17.
 */
public class ClusterTest {

    private static final Logger LOG = LoggerFactory.getLogger(ClusterTest.class);
    private AkkaTestCluster akkaTestCluster;
    private static final int CLUSTER_SIZE = 3;

    @BeforeClass
    private void init() {
        LOG.info("test init");
        akkaTestCluster = new AkkaTestCluster(CLUSTER_SIZE);
        akkaTestCluster.startCluster(20, TimeUnit.SECONDS);
        for (int i=1; i<=CLUSTER_SIZE; i++) {
            Application application = new Application(akkaTestCluster.getClusterObjectRegistry(i).getActorSystem());
            application.init();
            akkaTestCluster.getClusterObjectRegistry(i).registerSingleObject(Application.class, application);
        }
    }

    @Test
    public void testCreateSshSession() throws InterruptedException {
        LOG.info("testing");
        SshClientService sshClientService1 = akkaTestCluster.getClusterObjectRegistry(1).getSingleObject(Application.class).getSshClientService();
        SshClientSessionListenerTestImpl sshClientSessionListenerTest = new SshClientSessionListenerTestImpl();
        ListenableFuture<SshClientSession> upcommingSession = sshClientService1.createSession(sshClientSessionListenerTest, 5, TimeUnit.SECONDS);
        try {
            long duration = System.nanoTime();
            SshClientSession sshClientSession = upcommingSession.get(20, TimeUnit.SECONDS);
            duration = System.nanoTime() - duration;
            LOG.info("session created in " + duration + " ns");
            String sessionId = sshClientSession.getId();
            Assert.assertNotNull(sessionId);
            duration = System.nanoTime();
            sshClientSession.sendData("data");
            String data = sshClientSessionListenerTest.waitForData(20, TimeUnit.SECONDS);
            duration = System.nanoTime() - duration;
            LOG.info("session data transfer in " + duration + " ns");
            Assert.assertEquals(data, "DATA");
            sshClientSession.close();
        } catch (ExecutionException e) {
            LOG.error("Execution Exception: ", e.getCause().getMessage());
            Assert.fail();
        } catch (TimeoutException e) {
            LOG.error("Test TIMEOUT");
            Assert.fail();
        } catch (Exception e) {
            LOG.error("Exception: ", e);
            Assert.fail();
        }
    }

    @AfterClass
    private void destroy() {
        try {
            for (int i=1; i<=CLUSTER_SIZE; i++) {
                Application application = akkaTestCluster.getClusterObjectRegistry(i).getSingleObject(Application.class);
                application.destroy();
            }
            akkaTestCluster.stopCluster();
        } catch (Exception e) {
            LOG.error("Akka cluster shutdown failed !", e);
        }
    }

}