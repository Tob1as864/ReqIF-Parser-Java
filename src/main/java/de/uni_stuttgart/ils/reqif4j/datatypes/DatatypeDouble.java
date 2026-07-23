package de.uni_stuttgart.ils.reqif4j.datatypes;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;

public class DatatypeDouble extends Datatype {
    public DatatypeDouble(String id, String name) {
        super(id, name, ReqIFConst.DOUBLE);
    }
}
