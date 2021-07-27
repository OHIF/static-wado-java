package org.dcm4che.staticwado;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ItemPointer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.BulkDataDescriptor;
import org.dcm4che3.io.DicomInputStream;

import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.List;

/** Provides access to DICOM binary files */
public class DicomAccess {
    // The maximum length of a LUT table
    static final int LUT_LENGTH_MAX = 64*1024*2;

    // The maximum allowable size for a private tag
    static final int MAX_PRIVATE_SIZE = 64;

    static boolean descriptor(List<ItemPointer> itemPointer, String privateCreator, int tag, VR vr, int length) {
            if( privateCreator!=null ) {
                return length>MAX_PRIVATE_SIZE;
            }
            return tag==Tag.PixelData || length > LUT_LENGTH_MAX;
        };

    public static Attributes readFile(String path, File bulkFile) throws IOException {
        try(FileInputStream fis = new FileInputStream(path); DicomInputStream dis = new DicomInputStream(fis)) {
            dis.setIncludeBulkData(DicomInputStream.IncludeBulkData.URI);
            dis.setBulkDataDirectory(bulkFile);
            dis.setBulkDataFileSuffix(".raw");
            dis.setBulkDataDescriptor(DicomAccess::descriptor);
            Attributes fmi = dis.readFileMetaInformation();
            String transferSyntax = fmi.getString(Tag.TransferSyntaxUID);
            Attributes attr = dis.readDataset();
            attr.setString(Tag.AvailableTransferSyntaxUID, VR.UI, transferSyntax);
            return attr;
        }
    }
}
