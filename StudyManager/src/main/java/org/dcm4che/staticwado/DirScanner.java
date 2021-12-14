package org.dcm4che.staticwado;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomStreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** The study manager handles DICOM studies in the static WADO tree.  
 * It uses various components to handle the generation.
 * <ul>
 *   <li>StudyMetadataEngine to generate the basic result tree</li>
 * </ul>
 */
public class DirScanner {
    private static final Logger log = LoggerFactory.getLogger(DirScanner.class);

    public static int scan(String dir, BiConsumer<String,String> fileConsumer, String...roots) {
        int ret = 0;
        for(String root : roots) {
            File file = dir==null ? new File(root) : new File(dir,root);
            if( file.isDirectory() ) {
                scan(file.getAbsolutePath(), fileConsumer, file.list());
            } else {
                fileConsumer.accept(file.getParent(), file.getName());
                ret++;
            }
        }
        return ret;
    }
}
