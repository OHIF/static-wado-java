package org.dcm4che.staticwado;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Tag;
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
            json.writeJson("series.json", studyData.getSeries());
            Attributes[] instances = studyData.getInstances();
            json.writeJson("instances.json", instances);
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
        moveBulkdata(attr);
    }

    /**
     * Moves the bulkdata from the temp directory into:
     * series/SERIES_UID/instances/SOP_UID/frames/frame#
     * and
     * series/SERIES_UID/instances/SOP_UID/bulkdata/bulkdataHashCode
     * Note that frame# starts at 1.
     *
     * It then replaces the URL reference with a relative URL reference starting with the study UID.
     *
     * TODO: Handle video and fragmented images
     * @param attr which is searched for bulkdata
     */
    public void moveBulkdata(Attributes attr) {
        String studyUid = attr.getString(Tag.StudyInstanceUID);
        String seriesUID = attr.getString(Tag.SeriesInstanceUID);
        String sopUID = attr.getString(Tag.SOPInstanceUID);
        try {
            attr.accept((retrievePath, tag, vr, value) -> {
                if( ! (value instanceof BulkData) ) return true;
                BulkData bulk = (BulkData) value;
                log.warn("Moving bulkdata item {}", bulk.getURI());
                String hash = handler.hashOf(bulk.getFile());
                String bulkName = "../bulkdata/"+hash+".raw";
                // TODO - handle multi-frame as well as transfer syntax/type
                if( tag==Tag.PixelData ) {
                    log.warn("TODO - handle extension and frame#");
                }
                handler.move(bulk.getFile(), bulkName);
                String finalUri = studyUid + "/"+bulkName;
                log.warn("Final uri = {} was {}", finalUri, bulk.getURI());
                bulk.setURI(finalUri);
                return true;
            }, true);
        } catch (Exception e) {
            log.warn("Unable to move item because", e);
        }
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
