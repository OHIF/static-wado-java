package org.dcm4che.staticwado;

import org.dcm4che3.data.*;
import org.dcm4che3.image.PhotometricInterpretation;
import org.dcm4che3.imageio.codec.ImageWriterFactory;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReader;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.CountingOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.*;
import javax.imageio.stream.FileImageInputStream;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles access to the bulkdata, both the image data and the other bulkdata.
 *
 * TODO - split into multiple components, one for bulkdata and the other for image data.
 */
public class ExtractImageFrames {
    private static final Logger log = LoggerFactory.getLogger(ExtractImageFrames.class);

    private static final ColorSpace sRGB = ColorSpace.getInstance(ColorSpace.CS_sRGB);

    StudyManager callbacks;

    public static final Set<String> VIDEO_TSUIDS = new HashSet<>(Arrays.asList(
            UID.MPEG2MPHL, UID.MPEG2MPML, UID.MPEG4HP41, UID.MPEG4HP41BD,
            UID.MPEG4HP42STEREO, UID.MPEG4HP422D, UID.MPEG4HP423D, UID.MPEG4HP42STEREO,
            UID.HEVCMP51, UID.HEVCM10P51));

    public static final String IMAGE_JPEG_LOSSLESS = "image/jll";
    public static final String IMAGE_JPEG_LS = "image/x-jls";
    public static final String IMAGE_JPEG = "image/jpeg";
    public static final String IMAGE_JP2 = "image/jp2";
    public static final String VIDEO_H264 = "video/mp4";
    public static final String VIDEO_H265 = "video/H265";
    public static final String VIDEO_MPEG2 = "video/mpeg";

    public static final Map<String,String> EXTENSIONS = new HashMap<>();
    static {
        EXTENSIONS.put(IMAGE_JPEG,"jpg");
        EXTENSIONS.put(VIDEO_H264,"mp4");
        EXTENSIONS.put(VIDEO_MPEG2, "mpeg");
    }

    private String recompress = "lei,j2k";

    public static final String OCTET_STREAM = "application/octet-stream";

    public static final String SEPARATOR = "BOUNDARY_FIXED_32934857949532587";

    public static final Map<String,String> CONTENT_TYPES = new HashMap<>();
    static {
        CONTENT_TYPES.put(UID.ImplicitVRLittleEndian,OCTET_STREAM);
        CONTENT_TYPES.put(UID.ExplicitVRLittleEndian,OCTET_STREAM);
        CONTENT_TYPES.put(UID.JPEGBaseline8Bit,IMAGE_JPEG);
        CONTENT_TYPES.put(UID.RLELossless,"image/x-dicom-rle");
        CONTENT_TYPES.put(UID.JPEGLossless,IMAGE_JPEG);
        CONTENT_TYPES.put(UID.JPEGLosslessSV1, IMAGE_JPEG_LOSSLESS);
        CONTENT_TYPES.put(UID.JPEGLSLossless, IMAGE_JPEG_LS);
        CONTENT_TYPES.put(UID.JPEG2000Lossless, IMAGE_JP2);
        CONTENT_TYPES.put(UID.JPEG2000, IMAGE_JP2);
        CONTENT_TYPES.put(UID.MPEG2MPML, VIDEO_MPEG2);
        CONTENT_TYPES.put(UID.MPEG2MPHL, VIDEO_MPEG2);
        CONTENT_TYPES.put(UID.MPEG4HP41, VIDEO_H264);
        CONTENT_TYPES.put(UID.MPEG4HP41BD, VIDEO_H264);
        CONTENT_TYPES.put(UID.MPEG4HP422D, VIDEO_H264);
        CONTENT_TYPES.put(UID.MPEG4HP423D, VIDEO_H264);
        CONTENT_TYPES.put(UID.HEVCMP51, VIDEO_H265);
        CONTENT_TYPES.put(UID.HEVCM10P51, VIDEO_H265);
    }

    private ImageWriter compressor;

    private ImageWriter jpegCompressor;

    private String tsuid = UID.ImplicitVRLittleEndian;
    private ImageWriteParam compressParam;

    public ExtractImageFrames(StudyManager callbacks) {
        this.callbacks = callbacks;
        jpegCompressor = ImageIO.getImageWritersByFormatName("jpeg").next();
        setTransferSyntaxUid(callbacks.getDestinationTsuid());
    }

    public void setTransferSyntaxUid(String tsuid) {
        this.tsuid = tsuid;
        if( tsuid==null || UID.ImplicitVRLittleEndian.equals(tsuid) || UID.ExplicitVRLittleEndian.equals(tsuid)) {
            compressor = null;
            return;
        }
        ImageWriterFactory.ImageWriterParam param =
                ImageWriterFactory.getImageWriterParam(tsuid);
        if (param == null)
            throw new UnsupportedOperationException(
                    "Unsupported Transfer Syntax: " + tsuid);

        this.compressor = ImageWriterFactory.getImageWriter(param);
        this.compressParam = compressor.getDefaultWriteParam();
        compressParam.setCompressionMode(
                ImageWriteParam.MODE_EXPLICIT);
        if( tsuid.equals(UID.JPEGLosslessSV1) ) {
            compressParam.setCompressionType("LOSSLESS-1");
        } else if( tsuid.equals(UID.JPEG2000Lossless) ) {
            compressParam.setCompressionType("LOSSLESS");
        }
    }

    public void saveUncompressed(SopId id, Attributes attr, BulkData bulk) {
        int rows = attr.getInt(Tag.Rows,0);
        int cols = attr.getInt(Tag.Columns,0);
        int bits = attr.getInt(Tag.BitsAllocated, 8);
        int samples = attr.getInt(Tag.SamplesPerPixel,1);
        long imageLen = rows*((long) cols)*bits*samples;
        int frames = attr.getInt(Tag.NumberOfFrames,1);
        String dir = callbacks.getStudiesDir(id);
        DicomImageReader reader = id.getDicomImageReader();
        String seriesUid = id.getSeriesInstanceUid();
        String sopUid = id.getSopInstanceUid();
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
        String frameName = "series/"+seriesUid + "/instances/"+ sopUid + "/frames";
        BulkDataReader bulkdataReader = new BulkDataReader(callbacks.fileHandler, dir, frameName);
        for(int i=1; i<= frames; i++) {
            bulk.setURI(baseUri + "?offset="+(origOffset+imageLen*i-imageLen)+"&length="+imageLen);
            BulkData convertBulk = convertImageFormat(reader, dir, attr, frameName(frameName,i), i, bulk, false);
            bulkdataReader.add(convertBulk);
        }

        int midFrame = (frames+1)/2;
        convertThumbnail(reader, dir, attr, frameName.replace("/frames", "thumbnail"), midFrame);

        attr.setValue(Tag.PixelData,VR.OB, bulkdataReader);
    }

    public static boolean isVideo(Attributes attr) {
        return VIDEO_TSUIDS.contains(attr.getString(Tag.AvailableTransferSyntaxUID));
    }

    public static String getContentType(Attributes attr) {
        String tsuid = attr.getString(Tag.TransferSyntaxUID, attr.getString(Tag.AvailableTransferSyntaxUID));
        if( tsuid==null ) return OCTET_STREAM;
        String contentType = CONTENT_TYPES.get(tsuid);
        if( contentType==null ) {
            log.warn("Unknown content type for transfer syntax {}", tsuid);
            return OCTET_STREAM;
        }
        return contentType;
    }

    public void saveVideo(SopId id, Attributes attr, Fragments fragments) {
        String dest = saveRawFragments(id,attr,fragments, 1, fragments.size(), null);
        attr.setValue(Tag.PixelData, VR.OB, new BulkData(null, dest, false));
    }

    public String saveRawFragments(SopId id, Attributes attr, Fragments fragments, int start, int end, String frameName) {
        String contentType = getContentType(attr);
        String seriesUid = id.getSeriesInstanceUid();
        String sopUid = id.getSopInstanceUid();
        String dest = "series/"+seriesUid + "/instances/"+ sopUid + "/rendered"+(frameName==null ? "" : ("/"+frameName));
        log.warn("Writing single part {} content type {}", dest, contentType);
        long length = 0;
        try(OutputStream os = callbacks.fileHandler.writeStudyDir(id,dest,true)) {
            for(int i=start; i< end; i++) {
                length += copyFrom(fragments.get(i),os);
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
        return dest + "?length=" + length;
    }

    public void saveCompressed(SopId id, Attributes attr, Fragments fragments) {
        int frames = attr.getInt(Tag.NumberOfFrames,1);
        String dir = callbacks.getStudiesDir(id);
        String seriesUid = id.getSeriesInstanceUid();
        String sopUid = id.getSopInstanceUid();
        DicomImageReader reader = id.getDicomImageReader();

        if( isVideo(attr) ) {
            saveVideo(id,attr,fragments);
            return;
        }
        String frameName = "series/"+seriesUid + "/instances/"+ sopUid + "/frames";

        boolean fragmented = fragments.size()!=frames+1;
        BulkDataReader bulkdataReader = new BulkDataReader(callbacks.fileHandler, dir,frameName);
        for(int i=1; i<=frames; i++) {
            Object bulk = fragments.get(i);
            bulkdataReader.add(convertImageFormat(reader,dir, attr, frameName(frameName,i), i, bulk, fragmented));
        }

        int midFrame = (frames+1)/2;
        convertThumbnail(reader, dir, attr, frameName.replace("/frames", "/thumbnail"), midFrame);
        attr.setValue(Tag.PixelData,VR.OB, bulkdataReader);
    }

    /** Generate an alternate sub-directory name when frameNo exceeds 10000 .. frame(frameNo/10000)/frameNo
     * to deal with file system limitations. */
    public static String frameName(String dir, int i) {
        // TODO - consider using additional sub-frames - alternatively, consider using single files with offsets
        return dir + "/" + i;
    }

    /** Saves the raw, original DICOM object to the studies/.../SOP_Instance.gz file */
    public void saveOriginal(SopId id, Attributes attr) {
        byte[] original = getBytes(attr);
        if( original==null ) return;
        String seriesInstanceUID = attr.getString(Tag.SeriesInstanceUID);
        String sopInstanceUID = attr.getString(Tag.SOPInstanceUID);
        String dest = "series/"+seriesInstanceUID+"/instances/"+sopInstanceUID;
        saveMultipart(callbacks.getStudiesDir(id), dest,original,"application/dicom", SEPARATOR, true, null);
    }

    public static byte[] getBytes(Attributes attr) {
        if( attr==null ) return null;
        try (
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DicomOutputStream dos = new DicomOutputStream(baos, UID.ExplicitVRLittleEndian))
        {
            Attributes fmi = new Attributes();
            fmi.setString(Tag.MediaStorageSOPClassUID,VR.UI,attr.getString(Tag.SOPClassUID));
            fmi.setString(Tag.TransferSyntaxUID, VR.UI, UID.ExplicitVRLittleEndian);
            fmi.setString(Tag.MediaStorageSOPInstanceUID, VR.UI, attr.getString(Tag.SOPInstanceUID));
            dos.writeDataset(fmi,attr);
            return baos.toByteArray();
        } catch(IOException e) {
            log.warn("Unable to write raw value");
            return null;
        }
    }

    static final byte[] DASH_BYTES = "--".getBytes(StandardCharsets.UTF_8);
    static final byte[] NEWLINE_BYTES = "\r\n".getBytes(StandardCharsets.UTF_8);
    static final byte[] HEADER_SEPARATOR = " ".getBytes(StandardCharsets.UTF_8);

    public static long valueLength(Object value) {
        if( value instanceof byte[] ) {
            byte[] bValue = (byte[]) value;
            return bValue.length;
        }
        if( value instanceof BulkData ) {
            return ((BulkData) value).longLength();
        }
        throw new UnsupportedOperationException("Unknown type:" + value.getClass());
    }

    public BulkData saveMultipart(String dir, String dest, Object value, String contentType, String separator, boolean gzip, Map<String,String> headers) {
        BulkData ret = new BulkData(dest,0,-1,false);
        ret.setLength(valueLength(value));
        byte[] separatorBytes = separator.getBytes(StandardCharsets.UTF_8);
        log.debug("Writing multipart {} content type {} value {}", dest, contentType, value);
        if( headers==null ) headers = new HashMap<>();
        headers.put("Content-Type", contentType);
        headers.put("Content-Length", Long.toString(ret.longLength()));
        try(OutputStream os = callbacks.fileHandler.openForWrite(dir,dest,gzip, true)) {
            CountingOutputStream cos = new CountingOutputStream(os);
            cos.write(DASH_BYTES);
            cos.write(separatorBytes);
            cos.write(NEWLINE_BYTES);
            for(String hdr : headers.keySet()) {
                cos.write(hdr.getBytes(StandardCharsets.UTF_8));
                cos.write(':');
                cos.write(HEADER_SEPARATOR);
                cos.write(headers.get(hdr).getBytes(StandardCharsets.UTF_8));
                cos.write(NEWLINE_BYTES);
            }
            cos.write(NEWLINE_BYTES);
            ret.setOffset(cos.getCount());
            copyFrom(value,os);
            os.write(NEWLINE_BYTES);
            os.write(DASH_BYTES);
            os.write(separatorBytes);
            os.write(DASH_BYTES);
        } catch(IOException e) {
            log.warn("Unable to write", e);
        }
        return ret;
    }

    /** Saves an object as singlepart to the thumbnail or rendered directory  */
    public void saveSinglepart(String dir, String dest, Object value, String contentType) {
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            log.debug("No singlepart for {}", contentType);
            return;
        }
        log.warn("Writing single part {}.{} content type {}", dest, extension, contentType);
        saveSinglepart(dir, dest+"."+extension, value);
    }

    public void saveSinglepart(String dir, String dest, Object value) {
        try(OutputStream os = callbacks.fileHandler.openForWrite(dir, dest, false, true)) {
            copyFrom(value,os);
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

    public long copyFrom(Object value, OutputStream os) throws IOException {
        if( value instanceof byte[] ) {
            os.write((byte[]) value);
            return ((byte[]) value).length;
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
            return length;
        }
    }

    public void saveBulkdata(SopId id, Attributes attr, int tag, BulkData bulk) {
        String uri = bulk.getURI();
        String hash = callbacks.fileHandler.hashOf(bulk.getFile(), getOffset(uri), getLength(uri));
        String bulkName = callbacks.getBulkdataName(hash);
        BulkData updatedBulk = saveMultipart(callbacks.getStudiesDir(id),bulkName,bulk,OCTET_STREAM, SEPARATOR, true, null);
        attr.setValue(tag,attr.getVR(tag), updatedBulk);
    }

    public ImageTypeSpecifier getSpecifier(Attributes ds) {
        PhotometricInterpretation pmi = PhotometricInterpretation.fromString(
                ds.getString(Tag.PhotometricInterpretation, "MONOCHROME2"));
        int width = ds.getInt(Tag.Columns, 0);
        int height = ds.getInt(Tag.Rows, 0);
        int samples = ds.getInt(Tag.SamplesPerPixel, 1);
        boolean banded = samples > 1 && ds.getInt(Tag.PlanarConfiguration, 0) != 0;
        int bitsAllocated = ds.getInt(Tag.BitsAllocated, 8);
        int bitsStored = ds.getInt(Tag.BitsStored, bitsAllocated);
        int dataType = bitsAllocated <= 8 ? DataBuffer.TYPE_BYTE
                : DataBuffer.TYPE_USHORT;

        SampleModel sampleModel = pmi.createSampleModel(dataType, width, height, samples, banded);
        ColorModel colourModel = pmi.createColorModel(bitsStored, dataType, sRGB, ds);
        return new ImageTypeSpecifier(colourModel, sampleModel);
    }

    public static String getSimpleTsuid(String sourceTsuid) {
        if( sourceTsuid==null ) return "lei";
        String contentType = CONTENT_TYPES.get(sourceTsuid);
        if( contentType==null || OCTET_STREAM.equalsIgnoreCase(contentType) ) return "lei";
        if( IMAGE_JPEG.equalsIgnoreCase(contentType) ) return "jpeg";
        if( IMAGE_JPEG_LOSSLESS.equalsIgnoreCase(contentType) ) return "jll";
        if( IMAGE_JPEG_LS.equalsIgnoreCase(contentType) ) return "jls";
        if( IMAGE_JP2.equalsIgnoreCase(contentType) ) return "j2k";
        return "lei";
    }

    /**
     * Converts the image format from the one it is in to the specified output format, adding the format to
     * the AvailableTransferSyntaxUID list.
     *
     * It then writes it out to the given destination file in the specified format.  Formats can be multipart
     * encapsulated or raw.
     */
    public BulkData convertImageFormat(DicomImageReader reader, String dir, Attributes attr, String dest, int frame, Object bulk, boolean fragmented) {
        Object writeData = bulk;
        String sourceTsuid = attr.getString(Tag.AvailableTransferSyntaxUID);
        log.warn("sourceTsuid = {}", sourceTsuid);
        String writeType = CONTENT_TYPES.get(sourceTsuid);
        boolean gzip = false;
        if( writeType==null ) {
            writeType = OCTET_STREAM;
        } else {
            writeType = writeType + ";transfer-syntax="+sourceTsuid;
        }
        String simpleTsuid = getSimpleTsuid(sourceTsuid);
        if( reader!=null && (tsuid!=null && recompress.contains(simpleTsuid) || fragmented) ) {
            log.debug("Converting image from {}({}) to {}", sourceTsuid, simpleTsuid, tsuid);
            try {
                WritableRaster r = (WritableRaster) reader.readRaster(frame-1, null);
                if( compressor!=null ) {
                    ImageTypeSpecifier specifier = getSpecifier(attr);
                    BufferedImage bi = new BufferedImage(specifier.getColorModel(),r,false,null);
                    try(ExtMemoryCacheImageOutputStream ios = new ExtMemoryCacheImageOutputStream(attr)) {
                        synchronized(compressor) {
                            compressor.setOutput(ios);

                            compressor.write(null, new IIOImage(bi, null, null), compressParam);
                        }
                        writeData = ios.toByteArray();
                        writeType = CONTENT_TYPES.get(tsuid) + ";transfer-syntax="+tsuid;
                        attr.setString(Tag.AvailableTransferSyntaxUID,VR.UI, tsuid);
                        log.warn("Converted {} to {} length {} type {}", sourceTsuid, tsuid, ((byte[]) writeData).length, writeType);
                    }
                } else {
                    log.debug("Write source type {} uncompressed", sourceTsuid);
                    DataBuffer buf = r.getDataBuffer();
                    writeData = toBytes(buf);
                    writeType = OCTET_STREAM;
                    gzip = true;
                    if( writeData==null ) {
                        log.error("Unable to convert data buffer from {} to bytes", buf.getClass());
                        writeData = bulk;
                    }
                    attr.setString(Tag.AvailableTransferSyntaxUID,VR.UI, UID.ImplicitVRLittleEndian);
                }
            } catch(IOException e) {
                log.error("Couldn't convert image because {}",(Object) e);
                e.printStackTrace();
            }
        } else {
            callbacks.studyStats.add("Orig TS Image", 1000, "Leaving {} as original type {} imageReader {} tsuid {}", sourceTsuid, writeType, reader, tsuid);
            gzip = UID.ImplicitVRLittleEndian.equals(tsuid) || UID.ExplicitVRLittleEndian.equals(tsuid);
        }
        log.debug("Original bulkdata source is {}", (bulk instanceof BulkData) ? ((BulkData) bulk).getURI() : bulk);
        BulkData writeBulk = saveMultipart(dir,dest, writeData, writeType, SEPARATOR, gzip, null);
        saveSinglepart(dir, dest, writeData, writeType);
        return writeBulk;
    }

    /**
     * Converts the image format into a thumbnail representation and writes it out.
     */
    public void convertThumbnail(DicomImageReader reader, String dir, Attributes attr, String dest, int frame) {
        if( reader!=null ) {
            try {
                ImageReadParam param = reader.getDefaultReadParam();
                BufferedImage bi = reader.read(frame - 1, param);
                try (ExtMemoryCacheImageOutputStream ios = new ExtMemoryCacheImageOutputStream(attr)) {
                    synchronized(jpegCompressor) {
                        jpegCompressor.setOutput(ios);
                        jpegCompressor.write(null, new IIOImage(bi, null, null), null);
                    }
                    byte[] writeData = ios.toByteArray();
                    saveSinglepart(dir,dest, writeData);
                    callbacks.studyStats.add("Thumbnail", 1000,
                        "Wrote thumbnail to {} as JPEG length {} type image/jpeg",dest, ((byte[]) writeData).length);
                }
            } catch (IOException e) {
                log.warn("Unable to write to {}", dest);
            }
        }
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

    public String getTransferSyntaxUid() {
        return tsuid;
    }

    public void setRecompress(String recompress) {
        this.recompress = recompress==null ? "lei,j2k" : recompress;
    }

    public String getRecompress() {
        return recompress;
    }
}
