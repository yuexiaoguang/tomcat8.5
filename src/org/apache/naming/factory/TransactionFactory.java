package org.apache.naming.factory;

import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import org.apache.naming.TransactionRef;

/**
 * Object factory for User transactions.
 */
public class TransactionFactory extends FactoryBase {

    @Override
    protected boolean isReferenceTypeSupported(Object obj) {
        return obj instanceof TransactionRef;
    }

    @Override
    protected ObjectFactory getDefaultFactory(Reference ref) {
        // No default factory supported.
        return null;
    }

    @Override
    protected Object getLinked(Reference ref) {
        // Not supported
        return null;
    }
}
