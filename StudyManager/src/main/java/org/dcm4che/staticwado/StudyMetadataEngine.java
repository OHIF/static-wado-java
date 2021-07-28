package org.dcm4che.staticwado;

import org.dcm4che3.data.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * The study metadata engine has the internal knowledge on how to read DICOM files and add them to a static WADO
 * study metadata tree.
 */
public class StudyMetadataEngine {
    private static final Logger log = LoggerFactory.getLogger(StudyMetadataEngine.class);

    StudyData studyData;

    public boolean isNewStudy(String testUID) {
        return studyData==null || !studyData.getStudyUid().equals(testUID);
    }

    public void finalizeStudy(File exportDir) {
        if( studyData==null ) return;
        log.warn("Finalizing study {}", studyData.getStudyUid());
        studyData.updateCounts();
        JsonWadoAccess json = new JsonWadoAccess(exportDir, studyData.getStudyUid());
        json.writeJson("studies.json", studyData.getStudyAttributes());
        json.writeJson("series.json", studyData.getSeries());
        json.writeJson("metadata.json", studyData.getMetadata());
        json.setGzip(true);
        json.writeJson("studies.json", studyData.getStudyAttributes());
        json.writeJson("series.json", studyData.getSeries());
        json.writeJson("metadata.json", studyData.getMetadata());
        studyData = null;
    }

    public Attributes openNewStudy(Attributes sopAttr) {
        studyData = new StudyData(sopAttr);
        return studyData.getStudyAttributes();
    }

    public void addObject(Attributes attr) {
        studyData.addObject(attr);
    }
}
