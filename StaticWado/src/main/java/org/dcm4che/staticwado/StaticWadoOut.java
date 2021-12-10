package org.dcm4che.staticwado;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.dcm4che3.data.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

/**
 * Generate various outputs from the
 * and this will write the specified sop instances to the local directory (or that specified in -out)
 */
public class StaticWadoOut {
  private static final Logger log = LoggerFactory.getLogger(StaticWadoOut.class);

  private static StudyManager studyManager;
  private final StudyManager callbacks;
  private File outDir;

  public static void addOptions(Options opts) {
    opts.addOption("o", "output", true, "output part 10 directory");
    opts.addOption("s", "studyUid", true, "study instance UID");
  }

  public StaticWadoOut(StudyManager manager) {
    this.callbacks = manager;
  }

  public void setOut(String out) {
    if(out==null ) {
      this.outDir = null;
      return;
    }
    this.outDir = new File(out);
    outDir.mkdirs();
  }

  public void accept(SopId sopId, Attributes attr) {
    log.warn("Result {}\n{}", sopId, attr);
    if( outDir !=null ) {
      var studyData = sopId.getStudyData();
      var studyFile = new File(outDir,studyData.getStudyUid());
      studyFile.mkdirs();
      var studyDir = studyFile.getAbsolutePath();
      studyData.forEachInstance( (seriesUid, sopUid) -> {
        log.warn("Series {} sop {}", seriesUid, sopUid);
        try (OutputStream os = callbacks.fileHandler.openForWrite(studyDir,sopUid+".dcm",false,true)) {
          studyData.writeDimse(sopUid,os,true);
        }  catch(IOException e) {
          log.warn("Caught exception", e);
        }
      });
    }
  }

  public static void main(String... args) throws Exception {
    Options opts = new Options();
    StaticWado.addStudyManagerArgs(opts);
    addOptions(opts);
    CommandLine cl = StaticWado.parseCommandLine(opts, args);
    studyManager = StaticWado.createStudyManager(cl);
    String studyUid = cl.getOptionValue("studyUid");
    String out = cl.getOptionValue("output");
    log.warn("Query for studyUid {}", studyUid);
    var query = new HashMap<String,String>();
    query.put("StudyInstanceUID", studyUid);
    var staticWadoOut = new StaticWadoOut(studyManager);
    staticWadoOut.setOut(out);

    studyManager.queryStudies(query, staticWadoOut::accept);
  }
}
