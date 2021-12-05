package org.dcm4che.staticwado;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Tag;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReader;
import org.dcm4che3.io.DicomStreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageInputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The callbacks used for generating DICOM data - setup with a default set that is appropriate for basic
 * parsing and modification of the imaging data.
 */
public class StudyManager {
  private static final Logger log = LoggerFactory.getLogger(StudyManager.class);

  public BiConsumer<SopId, Attributes> instanceConsumer;
  public BiConsumer<SopId, Attributes> deduplicatedConsumer;
  public ExtractImageFrames imageConsumer;
  public ExtractImageFrames bulkConsumer;
  public CompleteStudyHandler studyHandler;
  public FileHandler fileHandler;
  public BiConsumer<SopId, Attributes> extractConsumer;

  public Stats overallStats = new Stats("Overall Stats", null);
  public Stats studyStats = new Stats("StudyStats", overallStats);

  private Map<String, Object> options = new HashMap<>();

  public StudyManager() {
    studyHandler = new CompleteStudyHandler(this);
    instanceConsumer = new InstanceDeduplicate(this);
    imageConsumer = new ExtractImageFrames(this);
    bulkConsumer = imageConsumer;
    extractConsumer = new ExtractConsumer(this);
    fileHandler = new FileHandler(this);
    deduplicatedConsumer = new DeduplicateWriter(this);
  }

  public String getDestinationTsuid() {
    Object optionsTsuid = options.get("DestinationTsuid");
    if (optionsTsuid instanceof String) return (String) optionsTsuid;
    return null;
  }

  /**
   * Scans the specified directories for DICOM Part 10 files, and parse/send them to the  instanceHandler.
   *
   * @param files
   */
  public int scanDicom(String... files) {
    try( var studyDataFactory = new StudyDataFactory() ) {
      return DirScanner.scan(null, (dir, name) -> {
        importDicom(dir, name, studyDataFactory);
      }, files);
    }
  }

  public void importDicom(String dir, String name, StudyDataFactory factory) {
    File file = new File(new File(dir), name);

    try {
      Attributes attr = DicomAccess.readFile(fileHandler, dir, name);
      if (attr == null) return;
      SopId id = factory.createSopId(attr);
      // Steps here are to extract the bulkdata, pixel data and then send the attr to the instance consumer.
      DicomImageReader reader = (DicomImageReader) ImageIO.getImageReadersByFormatName("DICOM").next();
      studyStats.add("DICOMP10 Read",1,"Read DICOM Part 10 file {}/{}", dir,name);
      try (FileImageInputStream fiis = new FileImageInputStream(file)) {
        reader.setInput(fiis);
        id.setDicomImageReader(reader);
        attr.accept((retrievePath, tag, vr, value) -> {
          if (value instanceof BulkData) {
            BulkData bulk = (BulkData) value;
            log.debug("Moving bulkdata item {}", bulk.getURI());
            if (tag == Tag.PixelData) {
              imageConsumer.saveUncompressed(id, attr, bulk);
            } else {
              bulkConsumer.saveBulkdata(id, attr, tag, bulk);
            }
          } else if (value instanceof Fragments) {
            Fragments fragments = (Fragments) value;
            if (tag == Tag.PixelData) {
              imageConsumer.saveCompressed(id, attr, fragments);
            } else {
              throw new UnsupportedOperationException("Not implemented yet");
            }
          }
          return true;
        }, true);
        instanceConsumer.accept(id, attr);
      } catch (Exception e) {
        overallStats.add("Non DICOM P10", 1, "Unable to process {}",e);
      }
    } catch (DicomStreamException dse) {
      log.debug("Skipping non-dicom {}", file);
    } catch (IOException e) {
      log.warn("Caught exception: {}", e.toString());
    }
  }

  public String getBulkdataName(String hashValue) {
    return "bulkdata/"+hashValue.substring(0,3) + "/" + hashValue.substring(3,5) + "/" + hashValue.substring(5);
  }

  public String getStudiesDir(SopId id) {
    return getStudiesDir(id.getStudyInstanceUid());
  }

  public String getStudiesDir(String studyUid) {
    Object optionsStudyDir = options.get("directory");
    if (optionsStudyDir instanceof String) {
      return ((String) optionsStudyDir) + "/studies/" + studyUid;
    }
    String userDir = System.getProperty("user.home");
    return userDir + "/dicomweb/studies/" + studyUid;
  }

  public String getDeduplicatedDir(String studyUid) {
    Object optionsStudyDir = options.get("directory");
    if (optionsStudyDir instanceof String) {
      return ((String) optionsStudyDir) + "/deduplicated/" + studyUid;
    }
    String userDir = System.getProperty("user.home");
    return userDir + "/dicomweb/deduplicated/" + studyUid;
  }


  public String getInstancesDir(String studyUid) {
    Object optionsStudyDir = options.get("directory");
    if (optionsStudyDir instanceof String) {
      return ((String) optionsStudyDir) + "/instances/" + studyUid;
    }
    String userDir = System.getProperty("user.home");
    return userDir + "/dicomweb/instances/" + studyUid;
  }

  /** Holder for study data.   */
  class StudyDataFactory implements AutoCloseable {
    StudyData data;

    public SopId createSopId(Attributes attr) throws IOException {
      SopId ret = new SopId(attr);
      if( data!=null && data.getStudyUid()!=ret.getStudyInstanceUid() ) {
        StudyData completing = data;
        data = null;
        studyHandler.completeStudy(data);
      }
      if( data==null ) {
        data = studyHandler.createStudy(ret);
      }
      ret.setStudyData(data);
      return ret;
    }

    @Override
    public void close() {
      if( data!=null ) {
        studyHandler.completeStudy(data);
        data = null;
      }
    }
  }

}
