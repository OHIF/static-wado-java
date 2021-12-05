package org.dcm4che.staticwado;

import java.util.function.BiConsumer;
import java.util.jar.Attributes;

/**
 * Consumes full instances of Attributes, including BulkData.  Produces
 * BulkDataURI containing instances.
 */
public class InstanceConsumer implements BiConsumer<SopId, Attributes> {

    public void accept(SopId id, Attributes attr) {

    }
}
