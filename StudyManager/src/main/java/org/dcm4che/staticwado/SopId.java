package org.dcm4che.staticwado;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReader;

public class SopId {
  private final String studyInstanceUid;
  private final String seriesInstanceUid;
  private final String sopInstanceUid;
  private StudyData studyData;
  private DicomImageReader reader;

  public SopId(String studyInstanceUid, String seriesInstanceUid, String sopInstanceUid) {
    this.studyInstanceUid = studyInstanceUid;
    this.seriesInstanceUid = seriesInstanceUid;
    this.sopInstanceUid = sopInstanceUid;
  }

  public SopId(Attributes attr) {
    this(attr.getString(Tag.StudyInstanceUID),
        attr.getString(Tag.SeriesInstanceUID),
        attr.getString(Tag.SOPInstanceUID)
    );
  }

  public String getStudyInstanceUid() {
    return studyInstanceUid;
  }

  public String getSeriesInstanceUid() {
    return seriesInstanceUid;
  }

  public String getSopInstanceUid() {
    return sopInstanceUid;
  }

  public void setStudyData(StudyData data) {
    this.studyData = data;
  }

  public StudyData getStudyData() { return studyData; }

  public DicomImageReader getDicomImageReader() {
    return reader;
  }

  public void setDicomImageReader(DicomImageReader reader) {
    this.reader = reader;
  }
}
