package org.dcm4che.staticwado;

import org.dcm4che3.data.*;
import org.dcm4che3.io.DicomOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Attr;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.dcm4che.staticwado.DicomAccess.getHash;

/** A holder class for study data */
public class StudyData {
    private static final Logger log = LoggerFactory.getLogger(StudyData.class);

    private final String studyUid;
    private final Map<String,Attributes> deduplicated = new HashMap<>();
    private final Map<String,Attributes> extractData = new HashMap<>();
    private final StudyManager callbacks;

    // Store maps of hash values to file names
    private final Map<String,String> readHashes = new HashMap<>();

    // Store a map of sop instanceUID to hash value
    private final Map<String,String> sopInstanceMap = new HashMap<>();
    private final String studyDir;

    public StudyData(SopId id, StudyManager callbacks) {
        this(id.getStudyInstanceUid(), callbacks);

    }

    public StudyData(String uid, StudyManager callbacks) {
        this.callbacks = callbacks;
        studyUid = uid;
        studyDir = callbacks.getStudiesDir(studyUid);
    }

    public String getStudyUid() {
        return studyUid;
    }

    public String addExtract(Attributes extract) {
        var hashValue = getHash(extract);
        if( extractData.putIfAbsent(hashValue,extract)==null ) {
            readHashes.put(hashValue,callbacks.getBulkdataName(hashValue,".json.gz"));
            return hashValue;
        }
        return null;
    }

    public String addDeduplicated(Attributes instance) {
        var hashValue = getHash(instance);
        var sopUid = instance.getString(Tag.SOPInstanceUID);
        log.debug("Adding deduplicated instance {} sop {}", hashValue, sopUid);
        sopInstanceMap.computeIfAbsent(instance.getString(Tag.SOPInstanceUID), key -> hashValue);
        var current = deduplicated.putIfAbsent(hashValue, instance);
        if( current==null ) {
            readHashes.put(hashValue, callbacks.getDeduplicatedName(hashValue));
            callbacks.studyStats.add("AddDeduplicated", 5000, "Add deduplicated instance to {}",
                studyUid);
            return hashValue;
        }
        callbacks.studyStats.add("SkipDeduplicated", 5000, "Skip deduplicated add instance {} to {}",
            hashValue, studyUid);
        return null;
    }

    /**
     * Writes updated deduplicated group files to the deduplicated directory.  It first checks to see if these are
     * required, by generating a hash value of the current deduplicated set data, and comparing that to the most recent
     * deduplicated set data.
     * @return
     */
    public boolean writeDeduplicatedGroup(String dir, Function<String,String> nameFunc, boolean isStudyData) {
        var writeList = new ArrayList<>(deduplicated.values());
        writeList.sort( (a,b) -> getHash(a).compareTo(getHash(b)));
        String hashValue = DicomAccess.hashAttributes(writeList.toArray(Attributes[]::new));
        if( readHashes.containsKey(hashValue) ) {
            return false;
        }
        String name = nameFunc.apply(hashValue);
        Attributes info = new Attributes();
        DicomAccess.setRefs(info, extractData.keySet());
        info.setString(DicomAccess.DEDUPPED_CREATER, DicomAccess.DEDUPPED_TYPE, VR.CS, DicomAccess.INFO_TYPE);
        info.setString(DicomAccess.DEDUPPED_CREATER, DicomAccess.DEDUPPED_HASH, VR.CS, hashValue);
        writeList.add(0,info);
        if( isStudyData ) {
            extractData.values().forEach(item -> writeList.add(item));
        }
        JsonAccess.write(callbacks.fileHandler,dir,name,isStudyData, writeList.toArray(Attributes[]::new));
        callbacks.studyStats.add("GroupDeduplicated", 1,
            "Combine single instance deduplicated objects into sets: {} instances",
            deduplicated.size());
        return true;
    }

    public boolean isEmpty() {
        return deduplicated.isEmpty();
    }

    /** If the sop UID already exists, then return true */
    public boolean alreadyExists(SopId id) {
        return sopInstanceMap.containsKey(id.getSopInstanceUid());
    }

    public int size() {
        if( sopInstanceMap.size()!=deduplicated.size() ) {
            log.warn("sop instance size {} deduplicated size {}", sopInstanceMap.size(), deduplicated.size());
        }
        return sopInstanceMap.size();
    }

    public void forEachInstance(BiConsumer<String,String> consumer) {
        sopInstanceMap.forEach((sopUid, hash) -> {
            var deduplicateInstance = deduplicated.get(hash);
            var seriesUid = deduplicateInstance.getString(Tag.SeriesInstanceUID);
            consumer.accept(seriesUid, sopUid);
        });
    }

    static class SeriesRecord {
        public final String seriesUid;
        public final Attributes seriesQuery;
        public final List<Attributes> metadata = new ArrayList<Attributes>();
        public final List<Attributes> instancesQuery = new ArrayList<>();

        public SeriesRecord(String seriesUid, Attributes metadata) {
            this.seriesUid = seriesUid;
            this.seriesQuery = TagLists.SERIES.select(metadata);
        }

        public void add(Attributes instance) {
            this.metadata.add(instance);
            this.instancesQuery.add(TagLists.INSTANCE_QUERY.select(instance));
        }
    }

    /**
     * Generate study/series/instance query objects and series level metadata files.
     */
    public Attributes writeStudyMetadata() {
        if( deduplicated.size()==0 ) {
            log.warn("No deduplicated instances, not writing study {}",studyUid);
            return null;
        }
        //  TODO - check to see if the content is identical
        var seriesContents = new HashMap<String,SeriesRecord>();
        var studyQuery = new AtomicReference<Attributes>();
        deduplicated.forEach((hash,attr) -> {
            Attributes metadata = toMetadata(attr);
            if( studyQuery.get()==null ) {
                studyQuery.set(TagLists.STUDY.select(metadata));
            }
            String seriesUid = metadata.getString(Tag.SeriesInstanceUID);
            SeriesRecord r = seriesContents.computeIfAbsent(seriesUid,
                (uid) -> new SeriesRecord(uid,metadata));
            r.add(metadata);
        });
        Attributes studyQ = studyQuery.get();
        studyQ.setInt(Tag.NumberOfStudyRelatedSeries,VR.IS, seriesContents.size());
        seriesContents.forEach( (uid,r) -> {
            DicomAccess.addToStrings(studyQ,null,Tag.ModalitiesInStudy,VR.CS,r.seriesQuery.getString(Tag.Modality));
            r.seriesQuery.setInt(Tag.NumberOfSeriesRelatedInstances,VR.IS,r.metadata.size());
            studyQ.setInt(Tag.NumberOfStudyRelatedInstances,VR.IS,r.metadata.size()+studyQ.getInt(Tag.NumberOfStudyRelatedInstances,0));
        });
        JsonAccess.write(callbacks.fileHandler, studyDir, "studies", true, studyQuery.get());
        var seriesQuery = seriesContents.values().stream().map((r) -> r.seriesQuery).toArray(Attributes[]::new);
        JsonAccess.write(callbacks.fileHandler, studyDir, "series", true, seriesQuery);
        seriesContents.forEach((key,r) -> {
           JsonAccess.write(callbacks.fileHandler, studyDir+"/series/"+r.seriesUid, "metadata",true,
               r.metadata.toArray(Attributes[]::new));
            JsonAccess.write(callbacks.fileHandler, studyDir+"/series/"+r.seriesUid, "instances",true,
                r.instancesQuery.toArray(Attributes[]::new));
        });
        writeDeduplicatedGroup(studyDir,
            (hash) -> "deduplicated", true);
        return studyQ;
    }


    /**
     * Converts a deduplicated instance back into a full instance.
     * @param deduplicated
     * @return Attributes which is a full metadata instance object.
     */
    public Attributes toMetadata(Attributes deduplicated) {
        if( deduplicated==null ) return null;
        var refs = deduplicated.getStrings(DicomAccess.DEDUPPED_CREATER,DicomAccess.DEDUPPED_REF, VR.CS);
        Attributes ret = new Attributes();
        ret.addAll(deduplicated);
        if( refs!=null ) {
            for(var ref : refs) {
                Attributes extractItem = getOrLoadExtract(ref);
                if( extractItem==null ) {
                    log.warn("Unable to load extract item {}", ref);
                    continue;
                }
                log.debug("Adding extract {}", extractItem.getString(DicomAccess.DEDUPPED_CREATER, DicomAccess.DEDUPPED_TYPE));
                ret.addAll(toMetadata(extractItem));
            }
        }
        return ret;
    }

    public Attributes getMetadata(String key) {
        var deduplicatedInstance = deduplicated.get(key);
        if( deduplicatedInstance==null ) {
            var hashValue = sopInstanceMap.get(key);
            if( hashValue==null ) return null;
            deduplicatedInstance = deduplicated.get(hashValue);
        }
        return toMetadata(deduplicatedInstance);
    }

    public Attributes getOrLoadExtract(String hashValue) {
        return extractData.computeIfAbsent(hashValue, (key) -> {
            try {
                Attributes extract = JsonAccess.readSingle(callbacks.fileHandler, callbacks.getStudiesDir(studyUid),
                    callbacks.getBulkdataName(hashValue,".json.gz"));
                if( extract!=null ) return extract;
                log.warn("Extract data at {} is null", callbacks.getBulkdataName((hashValue)));
                return null;
            } catch(IOException e) {
                log.warn("Unable to read {}", hashValue, e);
                return null;
            }
        });
    }

    public void readDeduplicatedGroup() {
        readDeduplicatedDir(callbacks.getDeduplicatedDir(studyUid));
    }

    public void readDeduplicatedDir(String dir) {
        List<String> files = callbacks.fileHandler.listContentsIncreasingAge(dir);
        log.warn("Reading {} deduplicated files", files.size());
        files.forEach(file -> {
           if( !file.endsWith(".gz") ) return;
           String hashValue = file.substring(0,file.length()-3);
           if( readHashes.containsKey(hashValue) ) return;
           readHashes.put(hashValue,file);
           try {
               var items = JsonAccess.read(callbacks.fileHandler, dir, file);
               items.forEach(attr -> {
                  String type = attr.getString(DicomAccess.DEDUPPED_CREATER,DicomAccess.DEDUPPED_TYPE);
                  if( DicomAccess.INFO_TYPE.equals(type) ) {
                     String[] refs = attr.getStrings(DicomAccess.DEDUPPED_CREATER,DicomAccess.DEDUPPED_REF,VR.CS);
                     if( refs!=null ) {
                         Arrays.stream(refs).forEach( ref -> readHashes.put(ref,file) );
                     }
                  } else if( DicomAccess.INSTANCE_TYPE.equals(type) ) {
                      addDeduplicated(attr);
                  } else {
                      addExtract(attr);
                  }
               });
           } catch(IOException e) {
               log.warn("Failed to read {}/{} because {}", dir,file,e);
           }
        });
    }

    /**
     * Writes the DIMSE format for the given sop instance to the stream.
     * Includes the fmi is the includeFmi is set.
     * @param key - either a hash or a sop insntance uid.
     * @param os
     * @throws IOException
     * @throws FileNotFoundException when the key isn't found
     */
    public void writeDimse(String key, OutputStream os, boolean includeFmi) throws IOException {
        Attributes attr = getMetadata(key);
        if( attr==null ) {
            throw new FileNotFoundException("SOP Instance "+key+" wasn't found");
        }
        var studyUid = attr.getString(Tag.StudyInstanceUID);
        var tsuid = attr.getString(Tag.AvailableTransferSyntaxUID);
        var isCompressed = tsuid!=null && !(
            tsuid.equals(UID.ExplicitVRLittleEndian) ||
            tsuid.equals(UID.ImplicitVRLittleEndian) ||
            tsuid.equals(UID.DeflatedExplicitVRLittleEndian));
        Attributes fmi = null;
        if( includeFmi ) {
            fmi = new Attributes();
            fmi.setString(Tag.TransferSyntaxUID,VR.UI, tsuid);
            tsuid = UID.ExplicitVRLittleEndian;
        }
        try (DicomOutputStream dos = new DicomOutputStream(os,tsuid)){
            attr.accept((attrs, tag, vr, value) -> {
                var bulkValue = fixBulkDataUri(studyUid, value);
                if( tag==Tag.PixelData && isCompressed && bulkValue!=null ) {
                    var frames = attrs.getInt(Tag.NumberOfFrames,1);
                    var frags = attrs.newFragments(Tag.PixelData, vr,frames+1);
                    bulkValue.toPixelFragments(frames,frags);
                    attrs.setValue(Tag.PixelData,vr,frags);
                    return true;
                }
                if (bulkValue != null) attrs.setValue(tag, vr, bulkValue);
                return true;
            }, true);
            dos.writeDataset(fmi,attr);
        } catch(RuntimeException e) {
            throw e;
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    public BulkDataReader fixBulkDataUri(String studyUid, Object value) throws IOException {
        if( value instanceof BulkData ) {
            BulkData bulk = (BulkData) value;
            var bulkReader = (bulk instanceof BulkDataReader) ? ((BulkDataReader) bulk) :
                new BulkDataReader(callbacks.fileHandler, callbacks.getStudiesDir(studyUid), bulk);
            return bulkReader;
        }
        return null;
    }

    public void readDeduplicatedInstances() {
        readDeduplicatedDir(callbacks.getDeduplicatedInstancesDir(studyUid));
    }
}
