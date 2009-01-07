/*
   Copyright 2006 OCLC Online Computer Library Center, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
 */
/*
 * LuceneRecordIterator.java
 *
 * Created on January 15, 2006, 2:29 PM
 */

package ORG.oclc.os.SRW.DSpaceLucene;

import ORG.oclc.os.SRW.Record;
import ORG.oclc.os.SRW.RecordIterator;
import ORG.oclc.os.SRW.Utilities;
import gov.loc.www.zing.srw.ExtraDataType;
import java.util.NoSuchElementException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dspace.content.DCValue;
import org.dspace.content.Item;



/**
 *
 * @author levan
 */
public class LuceneRecordIterator implements RecordIterator {
    static final Log log=LogFactory.getLog(LuceneRecordIterator.class);
    static String DCSchemaID="info:srw/schema/1/dc-v1.1";

    ExtraDataType edt;
    int numRecs, whichRecord=0;
    long startPoint;
    LuceneQueryResult lqr;

    /** Creates a new instance of PearsRecordIterator */
    public LuceneRecordIterator(LuceneQueryResult lqr, long startPoint, int numRecs, ExtraDataType edt)
      throws InstantiationException {
        log.info("lqr="+lqr+", startPoint="+startPoint+", numRecs="+numRecs);
        this.lqr=lqr;
        this.startPoint=startPoint;
        this.numRecs=numRecs;
        this.edt=edt;
    }

    public void close() {
    }
    
    public boolean hasNext() {
        log.info("in hasNext: whichRecord="+whichRecord+", numRecs="+numRecs+", resultItems.length="+lqr.resultItems.length);
        if(whichRecord<numRecs && whichRecord<lqr.resultItems.length)
            return true;
        return false;
    }

     private String makeDCRecord(DCValue[] values) {
        DCValue      value;
        StringBuffer sb=new StringBuffer();
        sb.append("<srw_dc:dc xmlns:srw_dc=\""+DCSchemaID+"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n");
        for(int i=0; i<values.length; i++) {
            value=values[i];
            sb.append("    <dc:").append(value.element);
            if(value.qualifier!=null)
                sb.append(".").append(value.qualifier);
            sb.append(">").append(Utilities.xmlEncode(value.value));
            sb.append("</dc:").append(value.element);
            if(value.qualifier!=null)
                sb.append(".").append(value.qualifier);
            sb.append(">\n");
        }
        sb.append("</srw_dc:dc>\n");

        return sb.toString();
    }

    public Object next() throws NoSuchElementException {
        return nextRecord();
    }

    public Record nextRecord() throws NoSuchElementException {
        log.info("in nextRecord: lqr="+lqr+", startPoint="+startPoint+", whichRecord="+whichRecord);
        DCValue[]    values=lqr.resultItems[(int)whichRecord].getDC(Item.ANY, Item.ANY, Item.ANY);
        whichRecord++;
        String stringRecord=makeDCRecord(values);
        return new Record(stringRecord, DCSchemaID);
    }
    
    public void remove() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }
}
