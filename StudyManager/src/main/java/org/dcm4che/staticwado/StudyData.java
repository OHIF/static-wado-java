package org.dcm4che.staticwado;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
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
        this.callbacks = callbacks;
        studyUid = id.getStudyInstanceUid();
        studyDir = callbacks.getStudiesDir(studyUid);
    }

    public String getStudyUid() {
        return studyUid;
    }

    public String addExtract(Attributes extract) {
        var hashValue = getHash(extract);
        if( extractData.containsKey(hashValue) ) return null;
        extractData.put(hashValue,extract);
        return hashValue;
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
        JsonAccess.write(callbacks.fileHandler,dir,name,writeList.toArray(Attributes[]::new));
        callbacks.studyStats.add("GroupDeduplicated", 1,
            "Combine single instance deduplicated objects into sets: {} instances",
            deduplicated.size());
        return true;
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
    public boolean writeStudyMetadata() {
        if( deduplicated.size()==0 ) return false;
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
        JsonAccess.write(callbacks.fileHandler, studyDir, "studies", studyQuery.get());
        var seriesQuery = seriesContents.values().stream().map((r) -> r.seriesQuery).toArray(Attributes[]::new);
        JsonAccess.write(callbacks.fileHandler, studyDir, "series", seriesQuery);
        seriesContents.forEach((key,r) -> {
           JsonAccess.write(callbacks.fileHandler, studyDir+"/series/"+r.seriesUid, "metadata",
               r.metadata.toArray(Attributes[]::new));
            JsonAccess.write(callbacks.fileHandler, studyDir+"/series/"+r.seriesUid, "instances",
                r.instancesQuery.toArray(Attributes[]::new));
        });
        writeDeduplicatedGroup(studyDir,
            (hash) -> "deduplicated", true);
        return true;
    }


    /**
     * Converts a deduplicated instance back into a full instance.
     * @param deduplicated
     * @return Attributes which is a full metadata instance object.
     */
    public Attributes toMetadata(Attributes deduplicated) {
        var refs = deduplicated.getStrings(DicomAccess.DEDUPPED_CREATER,DicomAccess.DEDUPPED_REF, VR.CS);
        Attributes ret = new Attributes(deduplicated);
        if( refs!=null ) {
            for(var ref : refs) {
                Attributes extractItem = getOrLoadExtract(ref);
                if( extractItem==null ) {
                    log.warn("Unable to load extract item", ref);
                    continue;
                }
                ret.addAll(extractItem);
            }
        }
        return ret;
    }

    public Attributes getOrLoadExtract(String hashValue) {
        return extractData.computeIfAbsent(hashValue, (key) -> {
            try {
                List<Attributes> list = JsonAccess.read(callbacks.fileHandler, callbacks.getStudiesDir(studyUid),
                    callbacks.getBulkdataName(hashValue) + ".json.gz");
                if( list!=null && list.size()>0 ) return list.get(0);
                log.warn("Extract data at {} is null or too many values", callbacks.getBulkdataName((hashValue)));
                return null;
            } catch(IOException e) {
                log.warn("Unable to read {}", hashValue, e);
                return null;
            }
        });
    }

    public void readDeduplicatedGroup() {
    }

    public void readDeduplicatedInstances() {
    }
}
