package org.dcm4che.staticwado;
import org.dcm4che3.data.*;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

/** Provides access to DICOM binary files */
public class DicomAccess {
    private static final Logger log = LoggerFactory.getLogger(DicomAccess.class);

    public static final String DEDUPPED_CREATER = "dedupped";
    // The default tag value to assume
    public static final int DEDUPPED_CREATOR_TAG = 0x00090010;
    public static final int DEDUPPED_CREATOR_GROUP = 0x00091000;

    // The hash value that this item can be referenced by
    public static final int DEDUPPED_HASH = 0x00090010;

    // The hash value that this item can be referenced by
    public static final int DEDUPPED_REF = 0x00090010;

    // The hash value that this item can be referenced by
    public static final int DEDUPPED_NAME = 0x00090010;

    // The maximum length of a LUT table
    static final int LUT_LENGTH_MAX = 64*1024*2;

    // The maximum allowable size for a private tag
    static final int MAX_PRIVATE_SIZE = 64;

    static boolean descriptor(List<ItemPointer> itemPointer, String privateCreator, int tag, VR vr, int length) {
        if( privateCreator!=null ) {
            return length>MAX_PRIVATE_SIZE;
        }
        return tag==Tag.PixelData || length > LUT_LENGTH_MAX;
    }

    public static Attributes readFile(String path, File bulkFile) throws IOException {
        bulkFile.mkdirs();
        try(DicomInputStream dis = new DicomInputStream(new File(path))) {
            dis.setIncludeBulkData(DicomInputStream.IncludeBulkData.URI);
            dis.setBulkDataDirectory(bulkFile);
            dis.setBulkDataFileSuffix(".raw");

            dis.setBulkDataDescriptor(DicomAccess::descriptor);
            Attributes fmi = dis.readFileMetaInformation();
            String transferSyntax = fmi!=null ? fmi.getString(Tag.TransferSyntaxUID) : UID.ImplicitVRLittleEndian;
            Attributes attr = dis.readDataset();
            attr.setString(Tag.AvailableTransferSyntaxUID, VR.UI, transferSyntax);
            return attr;
        }
    }

    /** Returns a SHA1 sum of the attributes instances */
    public static String hashAttributes(Attributes testAttr) {
        try(HashOutputStream hos = new HashOutputStream(); DicomOutputStream dos = new DicomOutputStream(hos,UID.ImplicitVRLittleEndian)) {
            dos.writeDataset(null,testAttr);
            return hos.getHash();
        } catch(IOException e) {
            throw new Error(e);
        }
    }

    public static void addToStrings(Attributes attr, String creator, int tag, String value) {
        String[] values = attr.getStrings(creator,tag);
        if( values==null || values.length==0 ) {
            attr.setString(creator,tag,VR.ST, value);
            return;
        }
        String[] newValues = Arrays.copyOf(values,values.length+1);
        newValues[newValues.length-1] = value;
        attr.setString(creator,tag,VR.ST,newValues);
    }

    public static class HashOutputStream extends OutputStream {
        private final MessageDigest digest;

        public HashOutputStream() {
            try {
                digest = MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException e) {
                throw new Error(e);
            }
            digest.reset();
        }

        @Override
        public void write(int b) throws IOException {
            digest.update(new byte[]{(byte) b},0,1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            digest.update(b, off, len);
        }

        public String getHash() {
            return new BigInteger(1, digest.digest()).toString(36);
        }
    }
}
