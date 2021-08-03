package org.dcm4che.staticwado;

import org.dcm4che3.data.*;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dcm4che3.imageio.plugins.dcm.DicomMetaData;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import java.awt.image.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Handles access to the bulkdata.  This includes
 * updating the bulkdata paths, image compression/decompression and related operations.
 */
public class BulkDataAccess {
    private static final Logger log = LoggerFactory.getLogger(BulkDataAccess.class);

    FileHandler handler;

    public static final Set<String> MULTIFRAME_TSUIDS = new HashSet<>(Arrays.asList(
            UID.MPEG2MPHL, UID.MPEG2MPML, UID.MPEG4HP41, UID.MPEG4HP41BD,
            UID.MPEG4HP42STEREO, UID.MPEG4HP422D, UID.MPEG4HP423D    ));

    public static final String IMAGE_JPEG_LOSSLESS = "image/jll";
    public static final String IMAGE_JPEG_LS = "image/jls";

    public static final String OCTET_STREAM = "application/octet-stream";

    public static final Map<String,String> mapTsContentTypeFrames = new HashMap<>();
    private static String defaultFramesContentType = OCTET_STREAM;

    /** Map hash tree values to image/jls by default, or to video/* if it is video */
    public static final Map<String,String> mapTsContentTypeHash = new HashMap<>();
    private static String defaultHashContentType = "image/jls";

    public static final String SEPARATOR = "BOUNDARY_FIXED_32934857949532587";

    private final DicomImageReader imageReader =  (DicomImageReader) ImageIO.getImageReadersByFormatName("DICOM").next();

    public static final Map<String,String> CONTENT_TYPES = new HashMap<>();
    static {
        CONTENT_TYPES.put(UID.ImplicitVRLittleEndian,OCTET_STREAM);
        CONTENT_TYPES.put(UID.ExplicitVRLittleEndian,OCTET_STREAM);
        CONTENT_TYPES.put(UID.JPEG2000,"image/jp2");
        CONTENT_TYPES.put(UID.JPEGBaseline8Bit,"image/jpeg");
        CONTENT_TYPES.put(UID.RLELossless,"image/dicom-rle");
        CONTENT_TYPES.put(UID.JPEGLossless,"image/jpeg");
        CONTENT_TYPES.put(UID.JPEGLosslessSV1, IMAGE_JPEG_LOSSLESS);
        CONTENT_TYPES.put(UID.JPEGLSLossless, IMAGE_JPEG_LS);
        CONTENT_TYPES.put(UID.JPEG2000Lossless, "image/j2k");
        CONTENT_TYPES.put(UID.JPEG2000, "image/j2k");
        CONTENT_TYPES.put(UID.MPEG2MPML, "video/mpeg");
    }

    public BulkDataAccess(FileHandler handler) {
        this.handler = handler;
    }

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
    public void moveBulkdata(File file, Attributes attr) {
        String studyUid = attr.getString(Tag.StudyInstanceUID);
        String seriesUID = attr.getString(Tag.SeriesInstanceUID);
        String sopUID = attr.getString(Tag.SOPInstanceUID);
        // TODO - move this up so we only read the stream metadata once and so we correctly handle non-image large bulkdata
        try (FileImageInputStream fiis = new FileImageInputStream(file) ) {
            imageReader.setInput(fiis);
            attr.accept((retrievePath, tag, vr, value) -> {
                if (value instanceof BulkData) {
                    handler.setGzip(true);
                    BulkData bulk = (BulkData) value;
                    log.debug("Moving bulkdata item {}", bulk.getURI());
                    if( tag==Tag.PixelData ) {
                        saveUncompressed(attr,seriesUID, sopUID,bulk);
                    } else {
                        saveBulkdata(studyUid, bulk, OCTET_STREAM);
                    }
                } else if( value instanceof Fragments) {
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
            throw new Error(e);
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
        long origOffset = getOffset(origUri);
        String baseUri = origUri.contains("?") ? origUri.substring(0,origUri.indexOf('?')) : origUri;
        String frameName = "series/"+seriesUid + "/instances/"+ sopUid + "/frames/";
        String framesContentType = chooseConvertType(attr,mapTsContentTypeFrames, defaultFramesContentType);

        for(int i=1; i<= frames; i++) {
            bulk.setURI(baseUri + "?offset="+(origOffset+imageLen*i-imageLen)+"&length="+imageLen);
            convertImageFormat(attr, frameName+i, i, bulk, OCTET_STREAM, framesContentType);
        }
        bulk.setURI(origUri);
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
        String frameName = "series/"+seriesUid + "/instances/"+ sopUid + "/frames/";

        String contentType = chooseConvertType(attr,Collections.emptyMap(), null);
        String framesContentType = chooseConvertType(attr,mapTsContentTypeFrames, defaultFramesContentType);
        String hashContentType = chooseConvertType(attr,mapTsContentTypeHash, defaultHashContentType);

        if( fragments.size()!=frames+1 ) {
            // Causes a read/write to occur, which de-frags the input
            contentType="fragmented";
        }

        log.warn("Source content type {} desired frames content type {} desired hashContent type {}", contentType, framesContentType, hashContentType);
        for(int i=1; i<fragments.size(); i++) {
            BulkData bulk = (BulkData) fragments.get(i);
            convertImageFormat(attr, frameName+i, i, bulk, contentType, framesContentType);
        }
    }

    static final byte[] DASH_BYTES = "--".getBytes(StandardCharsets.UTF_8);
    static final byte[] NEWLINE_BYTES = "\r\n".getBytes(StandardCharsets.UTF_8);
    static final byte[] CONTENT_TYPE_BYTES = "Content-Type: ".getBytes(StandardCharsets.UTF_8);

    public void saveMultipart(String dest, Object value, String contentType, String separator) {
        byte[] separatorBytes = separator.getBytes(StandardCharsets.UTF_8);
        log.debug("Writing multipart {} content type {}", dest, contentType);
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

    public long getOffset(String uri) {
        Matcher m = OFFSET_REGEXP.matcher(uri);
        if (m.find()) {
            return Long.parseLong(m.group(1));
        }
        return 0;
    }

    public static long getLength(String uri) {
        long length = Long.MAX_VALUE;
        Matcher m = LENGTH_REGEXP.matcher(uri);
        if( m.find()) {
            length = Long.parseLong(m.group(1));
        }
        return length;
    }

    public void copyFrom(Object value, OutputStream os) throws IOException {
        if( value instanceof byte[] ) {
            os.write((byte[]) value);
            return;
        }
        BulkData bulk = (BulkData) value;
        String uri = bulk.getURI();
        long start = getOffset(uri);
        long length = getLength(uri);
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
    private void saveBulkdata(String studyUid, BulkData bulk, String contentType) {
        String uri = bulk.getURI();
        String hash = handler.hashOf(bulk.getFile(), getOffset(uri), getLength(uri));
        String bulkName = "bulkdata/" + hash + ".raw";
        saveMultipart(bulkName,bulk,contentType, SEPARATOR);
        String finalUri = studyUid + "/" + bulkName;
        log.warn("Final uri = {} was {}", finalUri, bulk.getURI());
        bulk.setURI(finalUri);
    }

    /**
     * Converts the image format from the one it is in to an acceptable one for OHIF display purposes.
     *
     * It then writes it out to the given destination file as a multipart/related instance.
     */
    public void convertImageFormat(Attributes attr, String dest, int frame, BulkData bulk, String sourceType, String desiredType) {
        Object writeData = bulk;
        String writeType = sourceType;
        if( imageReader!=null && !sourceType.equals(desiredType) ) {
            log.warn("Converting image from {} to {}", sourceType, desiredType);
            try {
                Raster r = imageReader.readRaster(frame-1, imageReader.getDefaultReadParam());
                DataBuffer buf = r.getDataBuffer();
                writeData = toBytes(buf);
                if( writeData==null ) {
                    log.error("Unable to convert data buffer from {} to bytes", buf.getClass());
                    writeData = bulk;
                } else {
                    writeType = desiredType;
                }
            } catch(IOException e) {
                log.error("Couldn't convert image because {}",e);
                e.printStackTrace();
            }
        }
        log.debug("Original bulkdata source is {}", bulk.getURI());
        saveMultipart(dest, writeData, sourceType, SEPARATOR);
    }

    public static byte[] toBytes(short[] data) {
        byte[] ret = new byte[data.length*2];
        int i=0;
        for(short s : data) {
            ret[i++] = (byte) (s & 0xFF);
            ret[i++] = (byte) ((s & 0xFF00) >> 8);
        }
        return ret;
    }

    public static byte[] toBytes(DataBuffer buf) {
        if( buf instanceof DataBufferByte ) {
            DataBufferByte byteBuf = (DataBufferByte) buf;
            byte[][] banks = byteBuf.getBankData();
            if( banks.length==1 ) return banks[0];
            int len = 0;
            for(byte[] bank : banks) len += bank.length;
            byte[] ret = new byte[len];
            int position = 0;
            for(byte[] bank : banks) {
                System.arraycopy(bank,0,ret,position,bank.length);
                position += bank.length;
            }
            return ret;
        }
        if( buf instanceof DataBufferShort ) {
            return toBytes( ((DataBufferShort) buf).getData() );
        }
        if( buf instanceof DataBufferUShort ) {
            return toBytes( ((DataBufferUShort) buf).getData() );
        }
        // Probably int RGB, should convert this sometime.
        throw new UnsupportedOperationException("Unknown buffer type "+ buf.getClass());
    }

    /** Choose the content type for this object. defaultContentType can be null to mean don't perform any mapping.
     */
    public String chooseConvertType(Attributes attr, Map<String,String> contentTypeMapping, String defaultContentType) {
        String tsuid = attr.getString(Tag.AvailableTransferSyntaxUID);
        String contentType = contentTypeMapping.get(tsuid);
        if( contentType==null && defaultContentType==null ) {
                contentType = CONTENT_TYPES.get(tsuid);
                return contentType==null ? OCTET_STREAM : contentType;
        }
        return contentType!=null ? contentType : defaultContentType;
    }
}
