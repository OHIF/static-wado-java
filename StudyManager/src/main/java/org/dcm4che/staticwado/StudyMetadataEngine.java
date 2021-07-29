package org.dcm4che.staticwado;

import org.dcm4che3.data.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The study metadata engine has the internal knowledge on how to read DICOM files and add them to a static WADO
 * study metadata tree.
 */
public class StudyMetadataEngine {
    private static final Logger log = LoggerFactory.getLogger(StudyMetadataEngine.class);

    public static final Set<String> MULTIFRAME_TSUIDS = new HashSet<>(Arrays.asList(
            UID.MPEG2MPHL, UID.MPEG2MPML, UID.MPEG4HP41, UID.MPEG4HP41BD,
            UID.MPEG4HP42STEREO, UID.MPEG4HP422D, UID.MPEG4HP423D    ));

    public static final String SEPARATOR = "BOUNDARY_FIXED_32934857949532587";

    public static final String OCTET_STREAM = "application/octet-stream";

    public static final Map<String,String> CONTENT_TYPES = new HashMap<>();
    static {
        CONTENT_TYPES.put(UID.ImplicitVRLittleEndian,OCTET_STREAM);
        CONTENT_TYPES.put(UID.ExplicitVRLittleEndian,OCTET_STREAM);
        CONTENT_TYPES.put(UID.JPEG2000,"image/jp2");
        CONTENT_TYPES.put(UID.JPEGBaseline8Bit,"image/jpeg");
        CONTENT_TYPES.put(UID.RLELossless,"image/dicom-rle");
        CONTENT_TYPES.put(UID.JPEGLossless,"image/jpeg");
        CONTENT_TYPES.put(UID.JPEGLosslessSV1, "image/jll");
        CONTENT_TYPES.put(UID.JPEGLSLossless, "image/jls");
        CONTENT_TYPES.put(UID.MPEG2MPML, "video/mpeg");
    }

    StudyData studyData;
    FileHandler handler;


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
            handler.setGzip(false);
            json.writeJson("series.json", studyData.getSeries());
            handler.setGzip(true);
            json.writeJson("studies", studyData.getStudyAttributes());
            json.writeJson("series", studyData.getSeries());
            json.writeJson("instances", instances);
            json.writeJson("metadata", studyData.getMetadata());
            studyData.getSeriesUids().forEach(seriesUid -> {
                json.writeJson("series/" + seriesUid + "/metadata", studyData.getMetadata(seriesUid));
            });
        } finally {
            studyData = null;
            handler = null;
        }
    }

    public Attributes openNewStudy(Attributes sopAttr, File exportDir) {
        studyData = new StudyData(sopAttr);
        handler = new FileHandler(exportDir, studyData.getStudyUid());
        handler.setGzip(true);
        return studyData.getStudyAttributes();
    }

    public void addObject(Attributes attr) {
        studyData.addObject(attr);
        moveBulkdata(attr);
    }

    /**
     * Creates a de-duplicated copy of the source attributes.
     * This extracts Attributes instances
     * @param srcAttr
     * @return
     */
    public Attributes[] deduplicate(Attributes[] srcAttr) {
        throw new UnsupportedOperationException("TODO");
    }

    ////////////// TODO - move the bulkdata code into BulkDataAccess
    /**
     * Moves the bulkdata from the temp directory into:
     * series/SERIES_UID/instances/SOP_UID/frames/frame#
     * and
     * series/SERIES_UID/instances/SOP_UID/bulkdata/bulkdataHashCode
     * Note that frame# starts at 1.
     * <p>
     * It then replaces the URL reference with a relative URL reference starting with the study UID.
     * <p>
     * TODO: Handle video and fragmented images
     *
     * @param attr which is searched for bulkdata
     */
    public void moveBulkdata(Attributes attr) {
        String studyUid = attr.getString(Tag.StudyInstanceUID);
        String seriesUID = attr.getString(Tag.SeriesInstanceUID);
        String sopUID = attr.getString(Tag.SOPInstanceUID);
        try {
            attr.accept((retrievePath, tag, vr, value) -> {
                if (value instanceof BulkData ) {
                    handler.setGzip(true);
                    BulkData bulk = (BulkData) value;
                    log.warn("Moving bulkdata item {}", bulk.getURI());
                    if( tag==Tag.PixelData ) {
                        saveUncompressed(attr,seriesUID, sopUID,bulk);
                    } else {
                        saveBulkdata(studyUid, bulk);
                    }
                    bulk.getFile().delete();
                } else if( value instanceof Fragments ) {
                    Fragments fragments = (Fragments) value;
                    /*
                    There are several options here - non image data should never be handled as fragments, is a TODO
                    Video data should be concatenated from all fragments into one
                    Segmented data should be concatenated on a per-frame basis
                    Simple frames should be written out one at a time to frame numbers
                     */
                    if( tag==Tag.PixelData ) {
                        saveCompressed(attr,seriesUID, sopUID, fragments);
                    } else {
                        throw new UnsupportedOperationException("Not implemented yet");
                    }
                }
                return true;
            }, true);
        } catch (Exception e) {
            log.warn("Unable to move item because", e);
        }
    }

    public void saveUncompressed(Attributes attr, String seriesUid, String sopUid, BulkData bulk) {
        String bulkBase = "series/" + seriesUid + "/instances/" + sopUid + "/frames/";
        int rows = attr.getInt(Tag.Rows,0);
        int cols = attr.getInt(Tag.Columns,0);
        int bits = attr.getInt(Tag.BitsAllocated, 8);
        int samples = attr.getInt(Tag.SamplesPerPixel,1);
        long imageLen = rows*cols*bits*samples;
        int frames = attr.getInt(Tag.NumberOfFrames,1);
        if( imageLen % 8 !=0 ) {
            throw new UnsupportedOperationException("Can't handle partial bit images.");
        }
        imageLen /= 8;

        if( imageLen==0 ) {
            frames = 1;
            imageLen = bulk.getFile().length();
        }
        String origUri = bulk.getURI();
        if( origUri.contains("?") ) {
            origUri = origUri.substring(0,origUri.indexOf('?'));
        }
        String frameName = "series/"+seriesUid + "/instances/"+ sopUid + "/frames/";
        String contentType = OCTET_STREAM;
        for(int i=1; i<= frames; i++) {
            bulk.setURI(origUri + "?offset="+(imageLen*i-imageLen)+"&length="+imageLen);
            saveMultipart(frameName+i, bulk, contentType, SEPARATOR );
        }
    }

    public static boolean isMultiframe(Attributes attr) {
        String tsuid = attr.getString(Tag.AvailableTransferSyntaxUID);
        return MULTIFRAME_TSUIDS.contains(tsuid);
    }
    public void saveCompressed(Attributes attr, String seriesUid, String sopUid, Fragments fragments) {
        int frames = attr.getInt(Tag.NumberOfFrames,1);
        if( isMultiframe(attr) ) {
            throw new Error("No implementation of video/multiframe images");
        }
        if( fragments.size()!=frames+1 ) {
            throw new Error("Can't handle segmented images");
        }
        String frameName = "series/"+seriesUid + "/instances/"+ sopUid + "/frames/";
        String tsuid = attr.getString(Tag.AvailableTransferSyntaxUID);
        String contentType = CONTENT_TYPES.get(tsuid);
        if( contentType==null ) contentType=OCTET_STREAM;
        // TODO - figure out what can be done here as far as compression is concerned
        handler.setGzip(false && OCTET_STREAM.equals(contentType));
        for(int i=1; i<fragments.size(); i++) {
            saveMultipart(frameName+i, fragments.get(i), contentType, SEPARATOR );
        }
        // Clean up after ourselves
        ((BulkData) fragments.get(1)).getFile().delete();
    }

    static final byte[] DASH_BYTES = "--".getBytes(StandardCharsets.UTF_8);
    static final byte[] NEWLINE_BYTES = "\r\n".getBytes(StandardCharsets.UTF_8);
    static final byte[] CONTENT_TYPE_BYTES = "Content-Type: ".getBytes(StandardCharsets.UTF_8);

    public void saveMultipart(String dest, Object value, String contentType, String separator) {
        byte[] separatorBytes = separator.getBytes(StandardCharsets.UTF_8);
        log.warn("Writing multipart {} content type {}", dest, contentType);
        try(OutputStream os = handler.openForWrite(dest)) {
            os.write(DASH_BYTES);
            os.write(separatorBytes);
            os.write(NEWLINE_BYTES);
            os.write(CONTENT_TYPE_BYTES);
            os.write(contentType.getBytes(StandardCharsets.UTF_8));
            os.write(NEWLINE_BYTES);
            os.write(NEWLINE_BYTES);
            copyFrom(value,os);
            os.write(NEWLINE_BYTES);
            os.write(DASH_BYTES);
            os.write(separatorBytes);
            os.write(DASH_BYTES);
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private static final Pattern OFFSET_REGEXP = Pattern.compile("offset=([0-9]+)");
    private static final Pattern LENGTH_REGEXP = Pattern.compile("length=([0-9]+)");

    public void copyFrom(Object value, OutputStream os) throws IOException {
        if( value instanceof byte[] ) {
            os.write((byte[]) value);
            return;
        }
        BulkData bulk = (BulkData) value;
        String uri = bulk.getURI();
        long start = 0;
        Matcher m = OFFSET_REGEXP.matcher(uri);
        if( m.find() ) {
            start = Long.parseLong(m.group(1));
        }
        long length = Long.MAX_VALUE;
        m = LENGTH_REGEXP.matcher(uri);
        if( m.find()) {
            length = Long.parseLong(m.group(1));
        }
        try(InputStream is = new FileInputStream(bulk.getFile())) {
            is.skip(start);
            byte[] buffer = new byte[16384];
            long currentLength = 0;
            while(currentLength < length) {
                int maxBytes = (int) Math.min(length-currentLength,buffer.length);
                int readLen = is.read(buffer,0,maxBytes);
                if( readLen==-1 ) break;
                currentLength += readLen;
                os.write(buffer,0,readLen);
            }
        }
    }
    private void saveBulkdata(String studyUid, BulkData bulk) {
        String hash = handler.hashOf(bulk.getFile());
        String bulkName = "bulkdata/" + hash + ".raw";
        handler.move(bulk.getFile(), bulkName);
        String finalUri = studyUid + "/" + bulkName;
        log.warn("Final uri = {} was {}", finalUri, bulk.getURI());
        bulk.setURI(finalUri);
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
