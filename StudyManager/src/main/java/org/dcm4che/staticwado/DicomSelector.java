package org.dcm4che.staticwado;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

import java.util.Arrays;
import java.util.HashSet;

/** Selects various dicom sub-sets */
public interface DicomSelector {
    Attributes select(Attributes src);

    class SpecifiedDicomSelector implements DicomSelector {
        private int[] selection;

        public Attributes select(Attributes src) {
            Attributes ret = new Attributes();
            ret.addSelected(src,selection);
            return ret;
        }

        public SpecifiedDicomSelector add(int... tags) {
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

    int[] STUDY_TAGS = new int[]{Tag.SpecificCharacterSet, Tag.StudyInstanceUID, Tag.StudyID, Tag.StudyDate, Tag.StudyDescription,
        Tag.AccessionNumber, Tag.StudyTime, Tag.StudyComments,
    };

    int[] PATIENT_TAGS = new int[]{Tag.SpecificCharacterSet, Tag.PatientName, Tag.PatientID, Tag.OtherPatientIDs, Tag.IssuerOfPatientID, Tag.PatientAge};

    int[] SERIES_TAGS = new int[]{Tag.SpecificCharacterSet, Tag.SeriesInstanceUID, Tag.SeriesDescription, Tag.SeriesDate, Tag.SeriesTime,
        Tag.SeriesDescriptionCodeSequence, Tag.SeriesNumber, Tag.SeriesType,
    };

    int[] INSTANCE_TAGS = new int[]{Tag.SeriesInstanceUID, Tag.SOPInstanceUID, Tag.SOPClassUID, Tag.AvailableTransferSyntaxUID, Tag.InstanceNumber,
        Tag.NumberOfFrames, Tag.InstanceNumber, Tag.InstanceAvailability, Tag.Rows, Tag.Columns,
            Tag.InstanceCreationDate, Tag.ContentDate, Tag.ContentTime,
    };

    DicomSelector PATIENT_STUDY = new SpecifiedDicomSelector().add(PATIENT_TAGS).add(STUDY_TAGS);
    DicomSelector SERIES = new SpecifiedDicomSelector().add(SERIES_TAGS);
    DicomSelector INSTANCE = new SpecifiedDicomSelector().add(DicomSelector.INSTANCE_TAGS);
    DicomSelector PATIENT = new SpecifiedDicomSelector().add(PATIENT_TAGS);
    DicomSelector STUDY = new SpecifiedDicomSelector().add(STUDY_TAGS);
}
