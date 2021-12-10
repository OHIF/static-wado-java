package org.dcm4che.staticwado;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class StudyConsumer implements BiConsumer<String, Attributes> {
  private final StudyManager callbacks;
  private static final Logger log = LoggerFactory.getLogger(StudyConsumer.class);

  public StudyConsumer(StudyManager callbacks) {
    this.callbacks = callbacks;
  }

  public void accept(String studyUid, Attributes studyQuery) {
    if( studyQuery==null || studyUid==null ) return;
    List<Attributes> studies;
    String dicomWebDir = callbacks.getDicomWebDir();
    try {
      studies = JsonAccess.read(callbacks.fileHandler,dicomWebDir, "studies.gz")
          .stream().map(item -> studyUid.equals(item.getString(Tag.StudyInstanceUID)) ? studyQuery : item)
          .collect(Collectors.toList());
    } catch (IOException e) {
      studies = new ArrayList<>();
      studies.add(studyQuery);
    }
    log.warn("Writing studies directory {} with {} instances", dicomWebDir, studies.size());
    JsonAccess.write(callbacks.fileHandler,
        dicomWebDir,
        "studies", true,
        studies.toArray(Attributes[]::new));
  }

  public List<Attributes> queryStudies(Attributes constraints, int count) {
    return Collections.emptyList();
  }

  public List<Attributes> queryPatients(Attributes constraints, int count) {
    return Collections.emptyList();
  }
}
