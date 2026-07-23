package de.uni_stuttgart.ils.reqif4j.datatypes;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;

public class DatatypeDate extends Datatype {
    public DatatypeDate(String id, String name) {
        super(id, name, ReqIFConst.DATE);
    }
}
