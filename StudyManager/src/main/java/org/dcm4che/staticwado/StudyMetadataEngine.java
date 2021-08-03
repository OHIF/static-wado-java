package org.dcm4che.staticwado;

import org.dcm4che3.data.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * The study metadata engine has the internal knowledge on how to read DICOM files and add them to a static WADO
 * study metadata tree.
 */
public class StudyMetadataEngine {
    private static final Logger log = LoggerFactory.getLogger(StudyMetadataEngine.class);

    StudyData studyData;
    FileHandler handler;
    private BulkDataAccess bulkDataAccess;

    public boolean isNewStudy(String testUID) {
        return studyData == null || !studyData.getStudyUid().equals(testUID);
    }

    public void finalizeStudy() {
        if (studyData == null) return;
        try {
            log.warn("Finalizing study {}", studyData.getStudyUid());
            studyData.updateCounts();
            JsonWadoAccess json = new JsonWadoAccess(handler);
            Attributes[] instances = studyData.getInstances();
            handler.setGzip(false);
            json.writeJson("series.json", studyData.getSeries());
            handler.setGzip(true);
            json.writeJson("studies", studyData.getStudyAttributes());
            json.writeJson("series", studyData.getSeries());
            json.writeJson("instances", instances);
            json.writeJson("metadata", studyData.getMetadata());
            studyData.getSeriesUids().forEach(seriesUid -> {
                json.writeJson("series/" + seriesUid + "/metadata", studyData.getMetadata(seriesUid));
            });
        } finally {
            studyData = null;
            handler = null;
        }
    }

    public Attributes openNewStudy(Attributes sopAttr, File exportDir) {
        studyData = new StudyData(sopAttr);
        handler = new FileHandler(exportDir, studyData.getStudyUid());
        bulkDataAccess = new BulkDataAccess(handler);
        handler.setGzip(true);
        return studyData.getStudyAttributes();
    }

    public void addObject(File sourceFile, Attributes attr) {
        studyData.addObject(attr);
        bulkDataAccess.moveBulkdata(sourceFile, attr);
    }

    /**
     * Creates a de-duplicated copy of the source attributes.
     * This extracts Attributes instances
     * @param srcAttr
     * @return
     */
    public Attributes[] deduplicate(Attributes[] srcAttr) {
        throw new UnsupportedOperationException("TODO");
    }


}
