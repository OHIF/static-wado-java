package org.dcm4che.staticwado;

import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Fragments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Over-ride the bulk data reader to decompress gzip data, and seek past the multipart/related stuff, skipping the
 * bits at the end as well.
 */
public class BulkDataReader extends BulkData {
  private static final Logger log = LoggerFactory.getLogger(BulkDataReader.class);

  private final FileHandler handler;
  private final String dir;
  private final List<Long> offsets = new ArrayList<>();
  private final List<Long> lengths = new ArrayList<>();
  private final String dest;

  public BulkDataReader(FileHandler fileHandler, String studiesDir, String dest) {
    super(dest,0, -1, false);
    this.handler = fileHandler;
    this.dir = studiesDir;
    this.dest = dest;
  }

  public BulkDataReader(FileHandler fileHandler, String studiesDir, BulkData bulk) {
    super(bulk.uriWithoutOffsetAndLength(),0,-1, bulk.bigEndian());
    this.handler = fileHandler;
    this.dir = studiesDir;
    var uri = bulk.getURI();
    var question = uri.indexOf('?');
    this.dest = uri.substring(0,question==-1 ? uri.length() : question);
    var extractLengths = extractLongs(uri,"lengths");
    if( extractLengths!=null && extractLengths.size()>0 ) {
      var extractOffsets = extractLongs(uri,"offsets");
      lengths.addAll(extractLengths);
      offsets.addAll(extractOffsets);
    } else {
      var length = bulk.longLength();
      var offset = bulk.offset();
      if( length!=-1 ) {
        lengths.add(length);
        offsets.add(offset);
      }
    }
  }

  @Override
  public InputStream openStream() throws IOException {
    try {
      FileInputStream fis = new FileInputStream(new File(dir, dest));
      return fis;
    } catch(FileNotFoundException fnfe) {
      // No-op
    }
    return new GZIPInputStream(new FileInputStream(new File(dir,dest+".gz")));
  }

  public static List<Long> extractLongs(String uri, String param) {
    return parseLongList(extractParam(uri,param));
  }

  public static List<Long> parseLongList(String lst) {
    if( lst==null || lst.length()==0 ) return null;
    var start=0;
    var ret = new ArrayList<Long>();
    while(start!=-1) {
      var end = lst.indexOf(',',start);
      ret.add(Long.parseLong(lst.substring(start,end==-1 ? lst.length() : end)));
      if( end==-1 ) break;
      start = end+1;
    }
    return ret;
  }

  public static String extractParam(String uri, String param) {
    var posn = uri.indexOf(param+"=");
    if( posn==-1 ) return null;
    var end = uri.indexOf('&',posn);
    return uri.substring(posn+param.length()+1,end==-1 ? uri.length() : end);
  }

  /** Adds a fragment */
  public void add(BulkData bulk) {
    lengths.add(bulk.longLength());
    offsets.add(bulk.offset());
  }


  public void toPixelFragments(int frames, Fragments fragments) {
    fragments.clear();
    //  TODO - consider generating the BOT
    fragments.add(new byte[0]);
    for(int i=0; i<frames; i++) {
      fragments.add(generateSubBulkData(i));
    }
  }

  public BulkDataReader generateSubBulkData(int idx) {
    var length = lengths.size()>idx ? lengths.get(idx) : -1;
    var offset = offsets.size()>idx ? offsets.get(idx) : 0;
    var ret = new BulkDataReader(handler,dir,dest+"/"+(1+idx));
    ret.offsets.add(offset);
    ret.lengths.add(length);
    // Call the setters so that the values have the correct values up front
    ret.setOffset(offset);
    ret.setLength(length);
    return ret;
  }

  @Override
  public String getURI() {
    StringBuilder ret = new StringBuilder(dest);
    ret.append('?');
    if( lengths.size()==1 ) {
      ret.append("offset=").append(offsets.get(0));
      ret.append("&length=").append(lengths.get(0));
    } else if( lengths.size()>0 ) {
      ret.append("offsets=");
      for(var i=0; i<offsets.size(); i++) {
        if( i>0 ) ret.append(',');
        ret.append(offsets.get(i));
      }
      ret.append("&lengths=");
      for(var i=0; i<lengths.size(); i++) {
        if( i>0 ) ret.append(',');
        ret.append(lengths.get(i));
      }
    }
    return ret.toString();
  }

  @Override
  public long longLength() {
    if( lengths.size()==0 ) return -1;
    long ret = 0;
    for(var len : lengths) ret += len;
    return ret;
  }

  @Override
  public long offset() {
    return offsets.size()==1 ? offsets.get(0) : 0;
  }
}
