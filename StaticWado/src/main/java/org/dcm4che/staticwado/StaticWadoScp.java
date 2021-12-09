package org.dcm4che.staticwado;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.dcm4che3.data.Tag;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCEchoSCP;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StaticWadoScp {
  private static final Logger log = LoggerFactory.getLogger(StaticWadoScp.class);

  private final StudyManager studyManager;

  public StaticWadoScp(CommandLine cl, StudyManager studyManager) {
    log.warn("Creating StaticWadoScp");
    this.studyManager = studyManager;
    device.setDimseRQHandler(createServiceRegistry());
    device.addConnection(conn);
    device.addApplicationEntity(ae);
    ae.setAssociationAcceptor(true);
    ae.addConnection(conn);
    conn.setPort(Integer.parseInt(cl.getOptionValue("scpPort","11112")));
  }

  public static void addOptions(Options opts) {
    opts.addOption(new Option("p", "scpPort", true, "Define the http port"));
    opts.addOption(new Option("ae", "aeName", true, "Sets the listen AE name"));
  }

  private final Device device = new Device("StaticWadoSCP");
  private final ApplicationEntity ae = new ApplicationEntity("*");
  private final Connection conn = new Connection();

  private final BasicCStoreSCP cstoreSCP = new BasicCStoreSCP("*") {
    private final Map<Association,StudyManager.StudyDataFactory> studyDataFactories = new HashMap<>();

    @Override
    protected void store(Association as, PresentationContext pc,
                         Attributes rq, PDVInputStream data, Attributes rsp)
        throws IOException {
      StudyManager.StudyDataFactory factory = studyDataFactories.computeIfAbsent(as,key -> studyManager.createStudyDataFactory());
      String tsuid = pc.getTransferSyntax();
      try {
        Attributes attr = data.readDataset(tsuid);
        attr.setString(Tag.AvailableTransferSyntaxUID, VR.UI, tsuid);
        SopId id = factory.createSopId(attr);
        if( id.getStudyData().alreadyExists(id) ) {
          rsp.setInt(Tag.Status,VR.US, Status.DuplicateSOPinstance);
          return;
        }
        studyManager.studyStats.add("ReceiveInstance", 25, "Got SOP {} on association {}", attr.getString(Tag.SOPInstanceUID), as);
        studyManager.importDicom(id,attr);
      } catch (Exception e) {
        log.warn("Caught", e);
        throw new DicomServiceException(Status.ProcessingFailure, e);
      }
    }

    @Override
    public void onClose(Association as) {
      StudyManager.StudyDataFactory factory = studyDataFactories.remove(as);
      if( factory!=null ) {
        factory.close();
      }
    }
  };

  private DicomServiceRegistry createServiceRegistry() {
    DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
    serviceRegistry.addDicomService(new BasicCEchoSCP());
    serviceRegistry.addDicomService(cstoreSCP);
    return serviceRegistry;
  }

  public void start() {
    try {
      log.warn("Starting SCP service {}@{}", ae.getAETitle(), conn.getPort());
      configureTransferCapability(ae);
      ExecutorService executorService = Executors.newCachedThreadPool();
      ScheduledExecutorService scheduledExecutorService =
          Executors.newScheduledThreadPool(16);
      device.setScheduledExecutor(scheduledExecutorService);
      device.setExecutor(executorService);
      device.bindConnections();
    } catch (Exception e) {
      log.error("Caught", e);
      System.exit(2);
    }
  }


  protected static void configureTransferCapability(ApplicationEntity ae) throws IOException {
    ae.addTransferCapability(
        new TransferCapability(null,
            "*",
            TransferCapability.Role.SCP,
            "*"));
  }

  public static void main(String... args) throws Exception {
    Options opts = new Options();
    StaticWado.addStudyManagerArgs(opts);
    addOptions(opts);
    CommandLine cl = StaticWado.parseCommandLine(opts, args);
    StudyManager studyManager = StaticWado.createStudyManager(cl);
    StaticWadoScp scp = new StaticWadoScp(cl,studyManager);
    scp.start();
  }
}
