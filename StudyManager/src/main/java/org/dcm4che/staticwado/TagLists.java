package org.dcm4che.staticwado;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.dcm4che.staticwado.DicomAccess.*;

/** Selects various dicom sub-sets */
public interface TagLists {
    Attributes select(Attributes src);
    void remove(Attributes src);

    default String getName() {
        return null;
    }

    default void addTypeTo(Attributes attr) {
        String name = getName();
        if( name!=null ) {
            attr.setString(DEDUPPED_CREATER, DicomAccess.DEDUPPED_TYPE, VR.ST, name);
        }
    }

    class SpecifiedTagLists implements TagLists {
        private int[] selection;
        private String name;
        private Map<Integer,String> creators;

        public SpecifiedTagLists() {}

        public SpecifiedTagLists(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        public SpecifiedTagLists creator(String name, int tag) {
            if( creators==null ) creators = new HashMap<>();
            creators.put(tag,name);
            return this;
        }

        public Attributes select(Attributes src) {
            Attributes ret = new Attributes();
            ret.addSelected(src,selection);
            return ret;
        }

        public void remove(Attributes src) {
            src.removeSelected(selection);
        }

        public SpecifiedTagLists add(int... tags) {
            if( selection==null ) {
                selection = tags;
            } else {
                // Need to de-duplicate this as the Attributes add selection doesn't like duplicated items.
                HashSet<Integer> newSelection = new HashSet<>();
                for(int item : selection) newSelection.add(item);
                for(int item : tags) newSelection.add(item);
                selection = new int[newSelection.size()];
                int i=0;
                for(Integer item : newSelection) selection[i++] = item;
            }
            Arrays.sort(selection);
            return this;
        }
    }

    int[] STUDY_TAGS = new int[]{
            Tag.SpecificCharacterSet, Tag.StudyInstanceUID, Tag.StudyID, Tag.StudyDate, Tag.StudyDescription,
            Tag.AccessionNumber, Tag.StudyTime, Tag.StudyComments,
    };

    int[] PATIENT_TAGS = new int[]{Tag.SpecificCharacterSet, Tag.PatientName, Tag.PatientID, Tag.OtherPatientIDs, Tag.IssuerOfPatientID, Tag.PatientAge};

    int[] SERIES_TAGS = new int[]{
            Tag.SpecificCharacterSet, Tag.StudyInstanceUID, Tag.SeriesInstanceUID, Tag.SeriesDescription,
            Tag.SeriesDate, Tag.SeriesTime,
            Tag.SeriesDescriptionCodeSequence, Tag.SeriesNumber, Tag.SeriesType, Tag.Modality,
    };

    int[] INSTANCE_TAGS = new int[]{
            Tag.StudyInstanceUID, Tag.SeriesInstanceUID, Tag.SOPInstanceUID,
            Tag.SOPClassUID, Tag.AvailableTransferSyntaxUID, Tag.InstanceNumber,
            Tag.NumberOfFrames, Tag.InstanceNumber, Tag.InstanceAvailability, Tag.Rows, Tag.Columns,
            Tag.InstanceCreationDate, Tag.ContentDate, Tag.ContentTime,
    };

    int[] IMAGE_TAGS = new int[]{Tag.Rows, Tag.Columns, Tag.BitsStored, Tag.LossyImageCompression, Tag.BitsStored,
            Tag.BitsAllocated, Tag.AvailableTransferSyntaxUID, Tag.PhotometricInterpretation, Tag.PlanarConfiguration,
            Tag.ModalityLUTSequence, Tag.VOILUTSequence, Tag.VOILUTFunction, Tag.VOIType, Tag.WindowCenter, Tag.WindowWidth,
            Tag.WindowCenterWidthExplanation, Tag.RescaleIntercept, Tag.RescaleSlope,Tag.RescaleType, Tag.ImageType,
    };

    int[] REFERENCE_TAGS = new int[]{DicomAccess.DEDUPPED_REF | DEDUPPED_CREATOR_GROUP,
        Tag.Manufacturer, Tag.InstitutionalDepartmentName, Tag.InstitutionName,
    };

    TagLists PATIENT_STUDY = new SpecifiedTagLists("PatientStudy").add(PATIENT_TAGS).add(STUDY_TAGS);
    TagLists SERIES = new SpecifiedTagLists("series").add(SERIES_TAGS);
    TagLists INSTANCE_QUERY = new SpecifiedTagLists("instance").add(TagLists.INSTANCE_TAGS);
    TagLists PATIENT = new SpecifiedTagLists("patient").add(PATIENT_TAGS);
    TagLists STUDY = new SpecifiedTagLists("study").add(STUDY_TAGS);
    TagLists IMAGE = new SpecifiedTagLists("image").add(IMAGE_TAGS);
    TagLists REFERENCE = new SpecifiedTagLists().add(IMAGE_TAGS).creator(DEDUPPED_CREATER, DEDUPPED_CREATOR_TAG);
}
