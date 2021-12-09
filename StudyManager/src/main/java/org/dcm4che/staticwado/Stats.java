package org.dcm4che.staticwado;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class Stats {
  private final Stats parent;
  private final String name;
  private final Logger log;
  private final Map<String,Integer> stats = new HashMap<>();

  public Stats(String name, Stats parent) {
    this.parent = parent;
    this.name = name;
    this.log = LoggerFactory.getLogger(Stats.class.getName()+"."+name);
  }

  public void add(String name, int logCount, String description, Object...args) {
    var current = stats.compute(name, (key,val) -> (val==null ? 1 : val+1));
    if( logCount > 0 && current % logCount == 0 ) {
      Object[] extraArgs = new Object[2+args.length];
      extraArgs[0] = name;
      extraArgs[1] = current;
      System.arraycopy(args,0,extraArgs,2,args.length);
      log.warn("{} {} "+description,extraArgs);
    }
    if( parent!=null ) {
      parent.add(name,-1,description,args);
    }
  }

  public void summarize() {
    log.warn("{}", this.name);
    stats.forEach((name,count) -> {
      log.warn("{} {}", name, count);
    });
    stats.clear();
  }
}
