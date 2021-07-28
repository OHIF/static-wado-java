package org.dcm4che.staticwado;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** A holder class for study data */
public class StudyData {
    private static final Logger log = LoggerFactory.getLogger(StudyData.class);

    String studyUid;
    Attributes studyAttributes;
    Map<String,Attributes> metadata = new HashMap<>();
    Map<String,Attributes> series = new HashMap<>();

    public StudyData(Attributes attr) {
        studyUid = attr.getString(Tag.StudyInstanceUID);
        studyAttributes = DicomSelector.PATIENT_STUDY.select(attr);
    }

    public String getStudyUid() {
        return studyUid;
    }

    public Attributes getStudyAttributes() {
        return studyAttributes;
    }

    public void addObject(Attributes attr) {
        String sopUid = attr.getString(Tag.SOPInstanceUID);
        if( metadata.put(sopUid,attr)!=null ) {
            log.warn("Already contains {}", sopUid);
            return;
        }
        String seriesUid = attr.getString(Tag.SeriesInstanceUID);
        Attributes seriesData = series.computeIfAbsent(seriesUid, (key) -> DicomSelector.SERIES.select(attr));
        log.debug("Adding series {} with contents {}", seriesUid, seriesData);
        seriesData.setInt(Tag.NumberOfSeriesRelatedInstances,VR.IS, 1+seriesData.getInt(Tag.NumberOfSeriesRelatedInstances,0));
    }

    /** Updates the number of series and number of instances */
    public void updateCounts() {
        studyAttributes.setInt(Tag.NumberOfStudyRelatedInstances, VR.IS, metadata.size());
        studyAttributes.setInt(Tag.NumberOfStudyRelatedSeries, VR.IS, series.size());
    }

    public Attributes[] getMetadata() {
        return metadata.values().toArray(Attributes[]::new);
    }

    public Attributes[] getSeries() {
        return series.values().toArray(Attributes[]::new);
    }

    public Attributes[] getInstances() {
        return metadata.values().stream().map( DicomSelector.INSTANCE::select ).toArray(Attributes[]::new);
    }

    public Collection<String> getSeriesUids() {
        return series.keySet();
    }

    public Attributes[] getMetadata(String seriesUID) {
        return metadata.values().stream().filter(attr -> seriesUID.equals(attr.getString(Tag.SeriesInstanceUID)))
                .toArray(Attributes[]::new);
    }
}
