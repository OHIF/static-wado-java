package org.dcm4che.staticwado;

import org.dcm4che3.data.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.dcm4che.staticwado.DicomAccess.*;

import java.io.*;
import java.util.*;

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
            handler.setGzip(true);
            json.writeJson("studies", studyData.getStudyAttributes());
            json.writeJson("series", studyData.getSeries());
            json.writeJson("instances", instances);
            json.writeJson("metadata", studyData.getMetadata());
            studyData.getSeriesUids().forEach(seriesUid -> {
                json.writeJson("series/" + seriesUid + "/metadata", studyData.getMetadata(seriesUid));
            });
            Attributes[] deduplicated = deduplicate(studyData.getInstances());
            json.writeJson("deduplicated", deduplicated);
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
        Map<String,Attributes> hashAttributes = new HashMap<>();
        for(Attributes attr : srcAttr) {
            deduplicate(hashAttributes,attr);
        }
        return hashAttributes.values().toArray(new Attributes[0]);
    }

    private static final List<DicomSelector> deduplicateSelectors = new ArrayList<>(Arrays.asList(
            DicomSelector.PATIENT,
            DicomSelector.STUDY,
            DicomSelector.SERIES,
            DicomSelector.RENDER,
            DicomSelector.REFERENCE
    ));

    /** De-duplicates a single source attr instance */
    public void deduplicate(Map<String,Attributes> hashAttributes, Attributes attr) {
        Attributes dedupped = new Attributes(attr);
        for(DicomSelector selector : deduplicateSelectors) {
            Attributes testAttr = selector.select(dedupped);
            String hashKey = DicomAccess.hashAttributes(testAttr);
            testAttr.setString(DEDUPPED_CREATER, DEDUPPED_HASH, VR.ST, hashKey);
            selector.addTypeTo(testAttr);
            hashAttributes.putIfAbsent(hashKey,testAttr);
            dedupped.removeSelected(testAttr.tags());
            DicomAccess.addToStrings(dedupped,DEDUPPED_CREATER, DEDUPPED_REF, hashKey);
        }
        hashAttributes.putIfAbsent(DicomAccess.hashAttributes(dedupped), dedupped);
    }
}
