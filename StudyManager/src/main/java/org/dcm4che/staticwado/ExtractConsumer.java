package org.dcm4che.staticwado;

import org.dcm4che3.data.Attributes;

import java.util.function.BiConsumer;

public class ExtractConsumer implements BiConsumer<SopId, Attributes> {
  final StudyManager callbacks;

  public ExtractConsumer(StudyManager callbacks) {
    this.callbacks = callbacks;
  }

  public void accept(SopId id, Attributes extract) {
    // Returns a hash only if it just got added
    String hashValue = id.getStudyData().addExtract(extract);
    if( hashValue!=null ) {
      JsonAccess.write(callbacks.fileHandler, callbacks.getStudiesDir(id), callbacks.getBulkdataName(hashValue), extract);
    }
  }
}
