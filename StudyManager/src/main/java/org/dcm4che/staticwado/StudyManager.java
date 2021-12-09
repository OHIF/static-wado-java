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
import java.util.function.BiConsumer;

/**
 * The callbacks used for generating DICOM data - setup with a default set that is appropriate for basic
 * parsing and modification of the imaging data.
 */
public class StudyManager {
  public static final String INSTANCE_ONLY = "instanceMetadata";
  private static final Logger log = LoggerFactory.getLogger(StudyManager.class);
  public static final String DEDUPLICATE_GROUP = "deduplicateGroup";
  public static final String STUDY_METADATA = "studyMetadata";
  public static final String DEDUPLICATE = "deduplicate";

  public BiConsumer<SopId, Attributes> instanceConsumer;
  public BiConsumer<SopId, Attributes> deduplicatedConsumer;
  public ExtractImageFrames imageConsumer;
  public ExtractImageFrames bulkConsumer;
  public CompleteStudyHandler studyHandler;
  public FileHandler fileHandler;
  public BiConsumer<SopId, Attributes> extractConsumer;
  public BiConsumer<String, Attributes> studyConsumer;

  public Stats overallStats = new Stats("Overall Stats", null);
  public Stats studyStats = new Stats("StudyStats", overallStats);

  public boolean isDeduplicateGroup() {
    return deduplicateGroup || completeStudy;
  }

  public void setDeduplicateGroup(boolean deduplicateGroup) {
    this.deduplicateGroup = deduplicateGroup;
  }

  public boolean isDeduplicate() {
    return deduplicate || completeStudy;
  }

  public void setDeduplicate(boolean deduplicate) {
    this.deduplicate = deduplicate || completeStudy;
  }

  public boolean isInstanceMetadata() {
    return instanceMetadata;
  }

  public void setInstanceMetadata(boolean instanceMetadata) {
    this.instanceMetadata = instanceMetadata;
  }

  public boolean isStudyMetadata() {
    return studyMetadata || completeStudy;
  }

  public void setStudyMetadata(boolean studyMetadata) {
    this.studyMetadata = studyMetadata;
  }

  public boolean isCompleteStudy() {
    return completeStudy;
  }

  public void setCompleteStudy(boolean completeStudy) {
    this.completeStudy = completeStudy;
  }

  private boolean deduplicateGroup, deduplicate, instanceMetadata, studyMetadata;
  private boolean completeStudy = true;

  private String dicomWebDir = System.getProperty("user.home") + "/dicomweb";

  public StudyManager() {
    studyHandler = new CompleteStudyHandler(this);
    instanceConsumer = new InstanceDeduplicate(this);
    imageConsumer = new ExtractImageFrames(this);
    bulkConsumer = imageConsumer;
    extractConsumer = new ExtractConsumer(this);
    fileHandler = new FileHandler(this);
    deduplicatedConsumer = new DeduplicateWriter(this);
    studyConsumer = new StudyConsumer(this);
  }

  public String getDestinationTsuid() {
    return null;
  }

  /**
   * Scans the specified directories for DICOM Part 10 files, and parse/send them to the  instanceHandler.
   *
   * @param files
   */
  public int scanDicom(String... files) {
    if (files.length == 0) {
      return scanNotify();
    }
    try (var studyDataFactory = new StudyDataFactory()) {
      return DirScanner.scan(null, (dir, name) -> {
        importDicom(dir, name, studyDataFactory);
      }, files);
    }
  }

  public int scanNotify() {
    var dir = getNotifyDir();
    var files = fileHandler.listContentsIncreasingAge(dir);
    files.forEach(name -> {
      StudyData data = new StudyData(name, this);
      data.readDeduplicatedGroup();
      data.readDeduplicatedInstances();
      if (data.isEmpty()) return;
      studyHandler.completeStudy(data);
    });
    return files.size();
  }

  public String getNotifyDir() {
    if (isDeduplicateGroup()) return dicomWebDir + "/instances";
    if (isStudyMetadata()) return dicomWebDir + "/deduplicated";
    return dicomWebDir + "/studies";
  }

  public void importDicom(SopId id, Attributes attr) throws Exception {
    Object pixelData = attr.getValue(Tag.PixelData);
    if (pixelData instanceof BulkData) {
      imageConsumer.saveUncompressed(id, attr, (BulkData) pixelData);
    } else if (pixelData instanceof Fragments) {
      imageConsumer.saveCompressed(id, attr, (Fragments) pixelData);
    }

    attr.accept((retrievePath, tag, vr, value) -> {
      if( tag==Tag.PixelData && retrievePath.getParent()==null ) return true;
      if (value instanceof BulkData) {
        BulkData bulk = (BulkData) value;
        log.debug("Moving bulkdata item {}", bulk.getURI());
        bulkConsumer.saveBulkdata(id, attr, tag, bulk);
      } else if (value instanceof Fragments) {
          throw new UnsupportedOperationException("Not implemented yet");
      }
      return true;
    }, true);
    instanceConsumer.accept(id, attr);
  }

  public void importDicom(String dir, String name, StudyDataFactory factory) {
    File file = new File(new File(dir), name);

    try {
      Attributes attr = DicomAccess.readFile(fileHandler, dir, name);
      if (attr == null) return;
      SopId id = factory.createSopId(attr);
      if (id.getStudyData().alreadyExists(id)) return;
      // Steps here are to extract the bulkdata, pixel data and then send the attr to the instance consumer.
      DicomImageReader reader = (DicomImageReader) ImageIO.getImageReadersByFormatName("DICOM").next();
      studyStats.add("DICOMP10 Read", 250, "Read DICOM Part 10 file {}/{}", dir, name);
      try (FileImageInputStream fiis = new FileImageInputStream(file)) {
        reader.setInput(fiis);
        id.setDicomImageReader(reader);
        importDicom(id, attr);
      } catch (Exception e) {
        overallStats.add("Non DICOM P10", 1, "Unable to process {}", e);
      }
    } catch (DicomStreamException dse) {
      log.debug("Skipping non-dicom {}", file);
    } catch (IOException e) {
      log.warn("Caught exception: {}", e.toString());
    }
  }

  public String getBulkdataName(String hashValue, String extension) {
    return getBulkdataName(hashValue) + extension;
  }

  public String getBulkdataName(String hashValue) {
    return "bulkdata/" + hashValue.substring(0, 3) + "/" + hashValue.substring(3, 5) + "/" + hashValue.substring(5);
  }

  public String getStudiesDir(SopId id) {
    return getStudiesDir(id.getStudyInstanceUid());
  }

  public String getStudiesDir(String studyUid) {
    return dicomWebDir + "/studies/" + studyUid;
  }

  public String getDeduplicatedDir(String studyUid) {
    return dicomWebDir + "/deduplicated/" + studyUid;
  }

  public String getDicomWebDir() {
    return dicomWebDir;
  }

  public void setDicomWebDir(String dir) {
    if (dir == null) return;
    this.dicomWebDir = dir;
  }

  public String getDeduplicatedInstancesDir(String studyUid) {
    return dicomWebDir + "/instances/" + studyUid;
  }

  /**
   * Returns the name of the deduplicated instance - excluding the directory names
   */
  public String getDeduplicatedName(String hashValue) {
    return hashValue + ".gz";
  }

  public StudyDataFactory createStudyDataFactory() {
    return new StudyDataFactory();
  }


  /**
   * Holder for study data.
   */
  public class StudyDataFactory implements AutoCloseable {
    StudyData data;

    public SopId createSopId(Attributes attr) throws IOException {
      SopId ret = new SopId(attr);
      if (data != null && !data.getStudyUid().equals(ret.getStudyInstanceUid())) {
        StudyData completing = data;
        data = null;
        studyHandler.completeStudy(completing);
      }
      if (data == null) {
        data = studyHandler.createStudy(ret);
      }
      ret.setStudyData(data);
      return ret;
    }

    @Override
    public void close() {
      if (data != null) {
        studyHandler.completeStudy(data);
        data = null;
      }
    }
  }

}
