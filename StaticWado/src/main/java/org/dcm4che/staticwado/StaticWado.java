package org.dcm4che.staticwado;
import org.dcm4che.staticwado.StudyManager;

public class StaticWado {
    public static void main(String[] args) {
        StudyManager manager = new StudyManager();
        String importDir = "c:/dicom";
        String exportDir = "c:/dicomweb/studies";
        manager.setExportDir(exportDir);
        manager.importStudies(importDir);
    }
}
