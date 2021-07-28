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
    FileHandler handler;

    public boolean isNewStudy(String testUID) {
        return studyData==null || !studyData.getStudyUid().equals(testUID);
    }

    public void finalizeStudy() {
        if( studyData==null ) return;
        try {
            log.warn("Finalizing study {}", studyData.getStudyUid());
            studyData.updateCounts();
            JsonWadoAccess json = new JsonWadoAccess(handler);
            json.writeJson("studies", studyData.getStudyAttributes());
            json.writeJson("series", studyData.getSeries());
            Attributes[] instances = studyData.getInstances();
            json.writeJson("instances", instances);
            json.writeJson("metadata", studyData.getMetadata());
            handler.setGzip(true);
            json.writeJson("studies", studyData.getStudyAttributes());
            json.writeJson("series", studyData.getSeries());
            json.writeJson("instances", instances);
            json.writeJson("metadata", studyData.getMetadata());
        }
        finally {
            studyData = null;
            handler = null;
        }
    }

    public Attributes openNewStudy(Attributes sopAttr, File exportDir) {
        studyData = new StudyData(sopAttr);
        handler = new FileHandler(exportDir, studyData.getStudyUid());
        return studyData.getStudyAttributes();
    }

    public void addObject(Attributes attr) {
        studyData.addObject(attr);
    }

    /**
     * Moves the bulkdata from the specified location into the output directory structure.
     *
     * @param attr
     * @param exportDir
     */
    public void moveBulkData(Attributes attr, File exportDir) {

    }
}
