package org.dcm4che.staticwado;
import org.dcm4che.staticwado.StudyManager;

public class StaticWado {
    public static void main(String[] args) {
        StudyManager manager = new StudyManager();
        String importDir = args.length>0 ? args[0] : "c:/dicom";
        String exportDir = args.length>1 ? args[1] : "c:/dicomweb/studies";
        manager.setExportDir(exportDir);
        manager.importStudies(importDir);
    }
}
